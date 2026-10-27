package com.traphouse.server;

import com.google.gson.*;
import java.net.URI;
import java.net.http.*;
import java.sql.*;
import java.time.Duration;
import java.util.*;

/** Bot channel messages use a separate durable outbox; interaction handlers never wait on HTTP. */
final class DiscordJoinBot implements Runnable {
    private final DiscordWorker.Connections connections;
    private final Map<String,String> env;
    private final HttpClient http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).followRedirects(HttpClient.Redirect.NEVER).build();
    interface Transport {Reply send(String method,String path,JsonObject body)throws Exception;}
    static final class Reply {final int status;final String body;Reply(int status,String body){this.status=status;this.body=body;}}
    private final Transport transport;
    DiscordJoinBot(DiscordWorker.Connections connections,Map<String,String> env){this.connections=connections;this.env=env;this.transport=this::exchange;}
    DiscordJoinBot(DiscordWorker.Connections connections,Map<String,String> env,Transport transport){this.connections=connections;this.env=env;this.transport=transport;}
    private String required(String key) {String v=env.get(key);if(v==null||v.isBlank())throw new IllegalArgumentException("Configure "+key+" in Render");return v;}
    private Reply exchange(String method,String path,JsonObject body)throws Exception {
        HttpRequest request=HttpRequest.newBuilder(URI.create("https://discord.com/api/v10/"+path)).timeout(Duration.ofSeconds(10)).header("Authorization","Bot "+required("TRAP_DISCORD_BOT_TOKEN"))
            .header("Content-Type","application/json").method(method,method.equals("GET")||method.equals("DELETE")?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(body.toString())).build();
        HttpResponse<String> response=http.send(request,HttpResponse.BodyHandlers.ofString());return new Reply(response.statusCode(),response.body());
    }
    private int send(String method,String path,JsonObject body)throws Exception{return transport.send(method,path,body).status;}
    static JsonObject command(String name) {
        JsonObject body=new JsonObject();body.addProperty("name",name);body.addProperty("type",1);
        switch(name) {
            case "link":body.addProperty("description","Request a verified RuneLite character link");break;
            case "pending":body.addProperty("description","Staff: review pending RuneLite character links");break;
            case "edit-link":body.addProperty("description","Owner: correct an accepted RuneLite link's character name");break;
            case "revoke-link":body.addProperty("description","Owner: revoke an accepted RuneLite link and its credentials");break;
            default:throw new IllegalArgumentException("Unknown link command");
        }
        if(name.equals("edit-link")||name.equals("revoke-link")) {
            JsonObject member=new JsonObject();member.addProperty("type",6);member.addProperty("name","member");
            member.addProperty("description","Discord member whose accepted link you want to manage");member.addProperty("required",true);
            JsonArray options=new JsonArray();options.add(member);body.add("options",options);
        }
        return body;
    }
    void setup()throws Exception {setup(true);}
    int cleanReviewedMessages()throws Exception {
        String channel=DiscordLogin.snowflake(required("TRAP_DISCORD_REVIEW_CHANNEL_ID")),app=DiscordLogin.snowflake(required("TRAP_DISCORD_CLIENT_ID"));
        String staff=DiscordLogin.snowflake(required("TRAP_DISCORD_STAFF_LOG_CHANNEL_ID"));
        if(channel.equals(staff))throw new IllegalArgumentException("Staff log must be separate from the review channel");
        String before="";int queued=0;
        try(Connection c=connections.open()) {
            c.setAutoCommit(false);
            // Bounded historical cleanup; never touch unrelated or still-pending messages.
            for(int page=0;page<10;page++) {
                Reply reply=transport.send("GET","channels/"+channel+"/messages?limit=100"+before,new JsonObject());
                if(reply.status<200||reply.status>=300)throw new IllegalStateException("Cannot read review channel (HTTP "+reply.status+")");
                JsonArray messages=JsonParser.parseString(reply.body).getAsJsonArray();
                if(messages.size()==0)break;
                for(JsonElement value:messages) {
                    JsonObject message=value.getAsJsonObject();
                    if(!message.has("author")||!app.equals(message.getAsJsonObject("author").get("id").getAsString()))continue;
                    UUID request=reviewRequest(message);if(request==null)continue;
                    try(PreparedStatement s=RankingStore.query(c,"SELECT 1 FROM discord_join_requests WHERE id=? AND status<>'PENDING'",request);ResultSet r=s.executeQuery()) {
                        if(!r.next())continue;
                        DiscordJoinArchive.enqueue(c,request);DiscordJoinArchive.remember(c,request,channel,DiscordLogin.snowflake(Policy.text(message,"id",20)));queued++;
                    }
                }
                c.commit();before="&before="+DiscordLogin.snowflake(Policy.text(messages.get(messages.size()-1).getAsJsonObject(),"id",20));
                if(messages.size()<100)break;
            }
        }
        return queued;
    }
    static UUID reviewRequest(JsonObject message) {
        if(!message.has("components"))return null;
        for(JsonElement row:message.getAsJsonArray("components")) {
            JsonObject group=row.getAsJsonObject();if(!group.has("components"))continue;
            for(JsonElement value:group.getAsJsonArray("components")) {
                JsonObject button=value.getAsJsonObject();if(!button.has("custom_id"))continue;
                String custom=button.get("custom_id").getAsString();
                if(custom.matches("join:(approve|reject):[0-9a-fA-F-]{36}")) {
                    try{return UUID.fromString(custom.substring(custom.lastIndexOf(':')+1));}catch(IllegalArgumentException invalid){return null;}
                }
            }
        }
        return null;
    }
    void setup(boolean postButton)throws Exception {
        String app=DiscordLogin.snowflake(required("TRAP_DISCORD_CLIENT_ID")),guild=DiscordLogin.snowflake(required("TRAP_DISCORD_GUILD_ID"));
        String channel=DiscordLogin.snowflake(required("TRAP_DISCORD_LINK_CHANNEL_ID"));DiscordLogin.snowflake(required("TRAP_DISCORD_REVIEW_CHANNEL_ID"));
        if(!required("TRAP_DISCORD_PUBLIC_KEY").matches("[0-9a-fA-F]{64}"))throw new IllegalArgumentException("Use the Discord application's 64-character Public Key");
        required("TRAP_DISCORD_BOT_TOKEN");
        try(Connection c=connections.open();PreparedStatement s=RankingStore.query(c,"SELECT 1 FROM discord_member_links l JOIN members m ON m.id=l.member_id JOIN member_permissions p ON p.member_id=m.id WHERE m.active AND p.permission='OWNER' LIMIT 1");ResultSet r=s.executeQuery()) {
            if(!r.next())throw new IllegalStateException("Link and bootstrap the verified clan Owner before setting up Discord approvals. See docs/discord-joining.md.");
        }
        for(String command:new String[]{"link","pending","edit-link","revoke-link"}) {
            JsonObject body=command(command);
            // Upsert only our commands; do not replace unrelated application commands.
            int status=send("POST","applications/"+app+"/guilds/"+guild+"/commands",body);
            if(status<200||status>=300)throw new IllegalStateException("Discord command setup failed (HTTP "+status+")");
        }
        if(!postButton)return;
        JsonObject body=new JsonObject();body.addProperty("content","**TRAP HOUSE RuneLite linking**\nClick Link RuneLite and enter your OSRS name. Staff must verify your character and clan membership before approval. After approval, use Sign in with Discord in the plugin.");
        body.add("allowed_mentions",DiscordJoining.object("{\"parse\":[]}"));JsonArray rows=new JsonArray();rows.add(DiscordJoining.row(DiscordJoining.button("join:start","Link RuneLite",1)));body.add("components",rows);
        int status=send("POST","channels/"+channel+"/messages",body);if(status<200||status>=300)throw new IllegalStateException("Discord link button failed (HTTP "+status+")");
    }
    @Override public void run() {
        try(Connection c=connections.open()) {
            c.setAutoCommit(false);
            // Signatures expire after five minutes; a day of cached responses covers retries.
            RankingStore.execute(c,"DELETE FROM discord_interactions WHERE created_at<now()-interval '1 day'");
            try(PreparedStatement s=RankingStore.query(c,"SELECT id,payload::text,attempts FROM discord_join_outbox WHERE delivered_at IS NULL AND next_attempt<=now() ORDER BY created_at LIMIT 1 FOR UPDATE SKIP LOCKED");ResultSet r=s.executeQuery()) {
                if(!r.next()){DiscordJoinArchive.deliver(c,env,transport);c.commit();return;}
                UUID id=r.getObject(1,UUID.class);boolean delivered=false;
                try {
                    String state=null;
                    try(PreparedStatement q=RankingStore.query(c,"SELECT status FROM discord_join_requests WHERE id=?",id);ResultSet request=q.executeQuery()){if(request.next())state=request.getString(1);}
                    if(!"PENDING".equals(state))delivered=true;
                    else {
                        String channel=DiscordLogin.snowflake(required("TRAP_DISCORD_REVIEW_CHANNEL_ID"));
                        JsonObject payload=DiscordJoining.object(r.getString(2));payload.addProperty("nonce",id.toString().replace("-","").substring(0,24));payload.addProperty("enforce_nonce",true);
                        Reply response=transport.send("POST","channels/"+channel+"/messages",payload);
                        if(response.status>=200&&response.status<300) {
                            String message=DiscordLogin.snowflake(Policy.text(DiscordJoining.object(response.body),"id",20));
                            RankingStore.execute(c,"UPDATE discord_join_outbox SET review_channel_id=?,review_message_id=? WHERE id=?",channel,message,id);delivered=true;
                        }
                    }
                }
                catch(InterruptedException interrupted){Thread.currentThread().interrupt();return;}
                catch(Exception retry){/* Never log the bot token, request headers, or response bodies. */}
                if(delivered)RankingStore.execute(c,"UPDATE discord_join_outbox SET delivered_at=now(),attempts=attempts+1 WHERE id=?",id);
                else RankingStore.execute(c,"UPDATE discord_join_outbox SET attempts=attempts+1,next_attempt=now()+(? * interval '1 second') WHERE id=?",Math.min(3600,60L<<Math.min(r.getInt(3),6)),id);
                c.commit();
            }
            DiscordJoinArchive.deliver(c,env,transport);c.commit();
        }catch(SQLException failure){System.err.println("Discord linking notifications unavailable; will retry.");}
    }
}
