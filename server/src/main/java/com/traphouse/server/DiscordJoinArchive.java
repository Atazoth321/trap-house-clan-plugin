package com.traphouse.server;

import com.google.gson.*;
import java.sql.*;
import java.util.*;
import static com.traphouse.server.RankingStore.*;

/** Log a resolved request durably before deleting its public review message. */
final class DiscordJoinArchive {
    static void enqueue(Connection c,UUID request)throws SQLException {
        try(PreparedStatement s=query(c,"SELECT r.character_name,r.discord_user_id,r.status,r.reason,coalesce(r.reviewer_discord_id,m.name),r.reviewed_at FROM discord_join_requests r LEFT JOIN members m ON m.id=r.reviewer WHERE r.id=? AND r.status<>'PENDING'",request);ResultSet r=s.executeQuery()) {
            if(!r.next())return;
            JsonObject payload=new JsonObject();
            payload.addProperty("content","**RuneLite link "+r.getString(3).toLowerCase(Locale.ROOT)+"**\nCharacter: **"+r.getString(1)+"**\nDiscord ID: `"+r.getString(2)+"`\nReviewed by: **"+r.getString(5)+"**\nReason: "+plain(r.getString(4))+"\nReviewed: "+r.getTimestamp(6).toInstant()+"\nRequest: `"+request+"`");
            payload.add("allowed_mentions",DiscordJoining.object("{\"parse\":[]}"));
            payload.addProperty("nonce",request.toString().replace("-","").substring(0,24));payload.addProperty("enforce_nonce",true);
            execute(c,"INSERT INTO discord_join_archive_outbox(request_id,payload) VALUES (?,?::jsonb) ON CONFLICT DO NOTHING",request,payload.toString());
        }
    }
    private static String plain(String value){return value==null?"":value.replaceAll("[\\p{Cntrl}`*_~<>]"," ").trim();}
    static void remember(Connection c,JsonObject interaction,UUID request)throws SQLException {
        // /pending is ephemeral and contains several requests; never delete that message.
        if(!interaction.has("message")||!interaction.has("channel_id")||!interaction.has("application_id"))return;
        JsonObject message=interaction.getAsJsonObject("message");
        if(message.has("flags")&&(message.get("flags").getAsInt()&64)!=0)return;
        if(!message.has("author")||!interaction.get("application_id").getAsString().equals(message.getAsJsonObject("author").get("id").getAsString()))return;
        remember(c,request,DiscordLogin.snowflake(Policy.text(interaction,"channel_id",20)),DiscordLogin.snowflake(Policy.text(message,"id",20)));
    }
    static void remember(Connection c,UUID request,String channel,String message)throws SQLException {
        execute(c,"UPDATE discord_join_outbox SET review_channel_id=?,review_message_id=? WHERE id=? AND review_message_id IS NULL",channel,message,request);
    }
    static void deliver(Connection c,Map<String,String> env,DiscordJoinBot.Transport transport)throws SQLException {
        try(PreparedStatement s=query(c,"SELECT request_id,payload::text,logged_at,attempts FROM discord_join_archive_outbox WHERE cleaned_at IS NULL AND next_attempt<=now() ORDER BY created_at,request_id LIMIT 1 FOR UPDATE SKIP LOCKED");ResultSet r=s.executeQuery()) {
            if(!r.next())return;UUID id=r.getObject(1,UUID.class);
            try {
                if(r.getTimestamp(3)==null) {
                    String channel=DiscordLogin.snowflake(env.get("TRAP_DISCORD_STAFF_LOG_CHANNEL_ID"));
                    if(channel.equals(env.get("TRAP_DISCORD_REVIEW_CHANNEL_ID")))throw new IllegalArgumentException("Staff log must be separate from the review channel");
                    DiscordJoinBot.Reply reply=transport.send("POST","channels/"+channel+"/messages",DiscordJoining.object(r.getString(2)));
                    if(reply.status<200||reply.status>=300)throw new IllegalStateException("Staff log unavailable");
                    String message=DiscordLogin.snowflake(Policy.text(DiscordJoining.object(reply.body),"id",20));
                    execute(c,"UPDATE discord_join_archive_outbox SET staff_channel_id=?,staff_message_id=?,logged_at=now(),attempts=0,next_attempt=now() WHERE request_id=?",channel,message,id);
                    // Commit the log receipt separately. Cleanup cannot run before this is durable.
                    return;
                }
                try(PreparedStatement q=query(c,"SELECT review_channel_id,review_message_id,delivered_at FROM discord_join_outbox WHERE id=? FOR UPDATE",id);ResultSet pending=q.executeQuery()) {
                    if(pending.next()) {
                        if(pending.getTimestamp(3)==null)return;
                        if(pending.getString(2)!=null) {
                            String channel=DiscordLogin.snowflake(pending.getString(1)),message=DiscordLogin.snowflake(pending.getString(2));
                            int status=transport.send("DELETE","channels/"+channel+"/messages/"+message,new JsonObject()).status;
                            if(status!=404&&(status<200||status>=300))throw new IllegalStateException("Review cleanup unavailable");
                        }
                    }
                }
                execute(c,"UPDATE discord_join_archive_outbox SET cleaned_at=now(),attempts=attempts+1 WHERE request_id=?",id);
            }catch(InterruptedException interrupted){Thread.currentThread().interrupt();}
            catch(Exception retry){execute(c,"UPDATE discord_join_archive_outbox SET attempts=attempts+1,next_attempt=now()+(? * interval '1 second') WHERE request_id=?",Math.min(3600,60L<<Math.min(r.getInt(4),6)),id);}
        }
    }
}
