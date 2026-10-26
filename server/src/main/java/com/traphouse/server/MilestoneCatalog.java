package com.traphouse.server;

import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.*;

/** Additive catalog: existing rewards, progress, goal identities, and staff edits are retained. */
final class MilestoneCatalog {
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
