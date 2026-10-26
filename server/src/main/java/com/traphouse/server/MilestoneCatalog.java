package com.traphouse.server;

import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.*;

/** Additive catalog: existing rewards, progress, goal identities, and staff edits are retained. */
final class MilestoneCatalog {
    static int boostedReward(int points) {
        if(points<1||points>100000)throw new IllegalArgumentException("Invalid reward");
        return Math.min(100000,((points*3+5)/10)*5);
    }
    static void applyRewardBoost(Connection c)throws IOException,SQLException {
        try(Statement s=c.createStatement()){s.execute("SELECT pg_advisory_xact_lock(72419051001)");}
        try(PreparedStatement s=RankingStore.query(c,"SELECT 1 FROM configuration_migrations WHERE name='milestone-rewards-v2'");ResultSet r=s.executeQuery()) {
            if(r.next())return;
        }
        for(JsonElement entry:rewards()) {
            JsonObject reward=entry.getAsJsonObject();UUID goal=UUID.fromString(reward.get("id").getAsString());
            int original=reward.get("points").getAsInt(),increased=boostedReward(original);
            String name,category;
            try(PreparedStatement s=RankingStore.query(c,"SELECT name,category,points,enabled FROM goals WHERE id=? FOR UPDATE",goal);ResultSet r=s.executeQuery()) {
                if(!r.next())throw new IllegalStateException("Reward goal missing from catalog");
                // Respect operator overrides and disabled goals.
                if(r.getInt(3)!=original||!r.getBoolean(4))continue;
                name=r.getString(1);category=r.getString(2);
            }
            RankingStore.execute(c,"UPDATE goals SET points=? WHERE id=?",increased,goal);
            try(PreparedStatement s=RankingStore.query(c,"SELECT m.id,m.name FROM goal_progress p JOIN members m ON m.id=p.member_id WHERE p.goal_id=? AND p.award_event IS NOT NULL AND m.active ORDER BY m.id",goal);ResultSet r=s.executeQuery()) {
                while(r.next()) {
                    RankingStore.Actor member=new RankingStore.Actor(r.getObject(1,UUID.class),r.getString(2),false);
                    RankingStore.lockMember(c,member.id);
                    long paid;
                    try(PreparedStatement total=RankingStore.query(c,"SELECT coalesce(sum(l.points),0) FROM ledger l JOIN events e ON e.id=l.event_id WHERE l.member_id=? AND e.type='GOAL' AND e.event_key=?",member.id,goal.toString());ResultSet amounts=total.executeQuery()){amounts.next();paid=amounts.getLong(1);}
                    int difference=(int)Math.max(0L,increased-paid);if(difference==0)continue;
                    UUID event=UUID.randomUUID();
                    UUID request=UUID.nameUUIDFromBytes(("milestone-rewards-v2:"+member.id+":"+goal).getBytes(StandardCharsets.UTF_8));
                    int inserted;
                    try(PreparedStatement insert=RankingStore.query(c,"INSERT INTO events(id,member_id,client_id,type,event_key,observed_at,points) VALUES (?,?,?,'GOAL',?,now(),?) ON CONFLICT(member_id,client_id) DO NOTHING",event,member.id,request,goal.toString(),difference)){inserted=insert.executeUpdate();}
                    if(inserted==1) {
                        RankingStore.award(c,member,event,null,difference,name+" - milestone reward increase",category);
                        JsonObject details=new JsonObject();details.addProperty("source","milestone-rewards-v2 deployment migration");
                        details.addProperty("goal",goal.toString());details.addProperty("points",difference);
                        details.addProperty("previouslyAwarded",paid);details.addProperty("newReward",increased);
                        details.addProperty("reason","Owner-authorized milestone reward increase");
                        RankingServer.operatorAudit(c,member.id,"MILESTONE_REWARD_TOPUP",details);
                    }
                }
            }
        }
        RankingStore.execute(c,"INSERT INTO configuration_migrations(name) VALUES ('milestone-rewards-v2')");
    }
    static JsonArray rewards() throws IOException {
        try(InputStream input=MilestoneCatalog.class.getResourceAsStream("/milestone-rewards-v1.json")) {
            if(input==null)throw new IOException("Milestone rewards missing");
            return JsonParser.parseString(new String(input.readAllBytes(),StandardCharsets.UTF_8)).getAsJsonArray();
        }
    }
    static void applyRewards(Connection c)throws IOException,SQLException {
        // Caller owns the transaction. All reward edits and the marker commit together.
        try(Statement s=c.createStatement()){s.execute("SELECT pg_advisory_xact_lock(72419051001)");}
        try(PreparedStatement s=RankingStore.query(c,"SELECT 1 FROM configuration_migrations WHERE name='milestone-rewards-v1'");ResultSet r=s.executeQuery()) {
            if(r.next())return;
        }
        for(JsonElement entry:rewards()) {
            JsonObject reward=entry.getAsJsonObject();UUID id=UUID.fromString(reward.get("id").getAsString());
            int points=reward.get("points").getAsInt();
            if(points<1||points>100000)throw new IllegalStateException("Invalid milestone reward");
            try(PreparedStatement s=RankingStore.query(c,"SELECT points FROM goals WHERE id=? FOR UPDATE",id);ResultSet r=s.executeQuery()) {
                if(!r.next())throw new IllegalStateException("Reward goal missing from catalog");
                // Preserve previously configured positive rewards and disabled goals.
                if(r.getInt(1)==0)RankingStore.execute(c,"UPDATE goals SET points=? WHERE id=?",points,id);
            }
        }
        RankingStore.execute(c,"INSERT INTO configuration_migrations(name) VALUES ('milestone-rewards-v1')");
    }
    static JsonArray read() throws IOException {
        try(InputStream input=MilestoneCatalog.class.getResourceAsStream("/milestones.json")) {
            if(input==null)throw new IOException("Milestone catalog missing");
            return JsonParser.parseString(new String(input.readAllBytes(),StandardCharsets.UTF_8)).getAsJsonArray();
        }
    }
    static void seed(Connection c)throws IOException,SQLException {
        int sort=100;
        for(JsonElement entry:read()) {
            JsonObject goal=entry.getAsJsonObject();
            String source=goal.get("source").getAsString();
            String description=source.equals("QUEST")?"Completed quest read from your current game state.":"Your current unboosted skill or total level. Each configured milestone can award only once.";
            RankingStore.execute(c,"INSERT INTO goals(id,name,description,category,section,source,source_key,target,points,enabled,sort_order) VALUES (?,?,?,'Milestones',?,?,?,?,0,true,?) ON CONFLICT(id) DO NOTHING",
                UUID.fromString(goal.get("id").getAsString()),goal.get("name").getAsString(),description,goal.get("section").getAsString(),source,goal.get("sourceKey").getAsString(),goal.get("target").getAsLong(),sort++);
        }
    }
}
