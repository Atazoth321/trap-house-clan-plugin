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
    DiscordJoinBot(DiscordWorker.Connections connections,Map<String,String> env){this.connections=connections;this.env=env;}
    private String required(String key) {String v=env.get(key);if(v==null||v.isBlank())throw new IllegalArgumentException("Configure "+key+" in Render");return v;}
    private int send(String method,String path,JsonObject body)throws Exception {
        HttpRequest request=HttpRequest.newBuilder(URI.create("https://discord.com/api/v10/"+path)).timeout(Duration.ofSeconds(10)).header("Authorization","Bot "+required("TRAP_DISCORD_BOT_TOKEN"))
            .header("Content-Type","application/json").method(method,HttpRequest.BodyPublishers.ofString(body.toString())).build();
        return http.send(request,HttpResponse.BodyHandlers.discarding()).statusCode();
    }
    void setup()throws Exception {
        String app=DiscordLogin.snowflake(required("TRAP_DISCORD_CLIENT_ID")),guild=DiscordLogin.snowflake(required("TRAP_DISCORD_GUILD_ID"));
        String channel=DiscordLogin.snowflake(required("TRAP_DISCORD_LINK_CHANNEL_ID"));DiscordLogin.snowflake(required("TRAP_DISCORD_REVIEW_CHANNEL_ID"));
        if(!required("TRAP_DISCORD_PUBLIC_KEY").matches("[0-9a-fA-F]{64}"))throw new IllegalArgumentException("Use the Discord application's 64-character Public Key");
        required("TRAP_DISCORD_BOT_TOKEN");
        try(Connection c=connections.open();PreparedStatement s=RankingStore.query(c,"SELECT 1 FROM discord_member_links l JOIN members m ON m.id=l.member_id JOIN member_permissions p ON p.member_id=m.id WHERE m.active AND p.permission='OWNER' LIMIT 1");ResultSet r=s.executeQuery()) {
            if(!r.next())throw new IllegalStateException("Link and bootstrap the verified clan Owner before setting up Discord approvals. See docs/discord-joining.md.");
        }
        for(String command:new String[]{"link","pending"}) {
            JsonObject body=new JsonObject();body.addProperty("name",command);body.addProperty("description",command.equals("link")?"Request a verified RuneLite character link":"Owner: review pending RuneLite character links");body.addProperty("type",1);
            // Upsert only our commands; do not replace unrelated application commands.
            int status=send("POST","applications/"+app+"/guilds/"+guild+"/commands",body);
            if(status<200||status>=300)throw new IllegalStateException("Discord command setup failed (HTTP "+status+")");
        }
        JsonObject body=new JsonObject();body.addProperty("content","**TRAP HOUSE RuneLite linking**\nClick Link RuneLite and enter your OSRS name. The Owner must verify your character and clan membership before approval. After approval, use Sign in with Discord in the plugin.");
        body.add("allowed_mentions",DiscordJoining.object("{\"parse\":[]}"));JsonArray rows=new JsonArray();rows.add(DiscordJoining.row(DiscordJoining.button("join:start","Link RuneLite",1)));body.add("components",rows);
        int status=send("POST","channels/"+channel+"/messages",body);if(status<200||status>=300)throw new IllegalStateException("Discord link button failed (HTTP "+status+")");
    }
    @Override public void run() {
        try(Connection c=connections.open()) {
            c.setAutoCommit(false);
            // Signatures expire after five minutes; a day of cached responses covers retries.
            RankingStore.execute(c,"DELETE FROM discord_interactions WHERE created_at<now()-interval '1 day'");
            try(PreparedStatement s=RankingStore.query(c,"SELECT id,payload::text,attempts FROM discord_join_outbox WHERE delivered_at IS NULL AND next_attempt<=now() ORDER BY created_at LIMIT 1 FOR UPDATE SKIP LOCKED");ResultSet r=s.executeQuery()) {
                if(!r.next()){c.commit();return;}
                UUID id=r.getObject(1,UUID.class);boolean delivered=false;
                try {int status=send("POST","channels/"+DiscordLogin.snowflake(required("TRAP_DISCORD_REVIEW_CHANNEL_ID"))+"/messages",DiscordJoining.object(r.getString(2)));delivered=status>=200&&status<300;}
                catch(InterruptedException interrupted){Thread.currentThread().interrupt();return;}
                catch(Exception retry){/* Never log the bot token, request headers, or response bodies. */}
                if(delivered)RankingStore.execute(c,"UPDATE discord_join_outbox SET delivered_at=now(),attempts=attempts+1 WHERE id=?",id);
                else RankingStore.execute(c,"UPDATE discord_join_outbox SET attempts=attempts+1,next_attempt=now()+(? * interval '1 second') WHERE id=?",Math.min(3600,60L<<Math.min(r.getInt(3),6)),id);
                c.commit();
            }
        }catch(SQLException failure){System.err.println("Discord linking notifications unavailable; will retry.");}
    }
}
