package com.traphouse.server;

import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.*;

/** Additive catalog: existing rewards, progress, goal identities, and staff edits are retained. */
final class MilestoneCatalog {
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
