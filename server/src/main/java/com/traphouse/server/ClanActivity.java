package com.traphouse.server;

import com.google.gson.*;
import java.sql.*;

/** Shared confirmed awards; excludes manual request reasons and inactive members. */
final class ClanActivity {
    static JsonArray read(Connection c) throws SQLException {
        JsonArray entries=new JsonArray();
        try(PreparedStatement s=c.prepareStatement("SELECT name,title,points,category,created_at FROM (SELECT m.name,n.title,n.points,l.category,l.created_at FROM point_notifications n JOIN ledger l ON l.id=n.ledger_id JOIN members m ON m.id=n.member_id WHERE m.active AND NOT EXISTS (SELECT 1 FROM reversed_goal_awards x WHERE x.event_id=l.event_id) AND n.points>0 AND l.category IN ('Drops','Pets','Milestones','Combat','Diaries','Collection') UNION ALL SELECT m.name,h.rank,0,'Ranks',h.earned_at FROM earned_rank_history h JOIN members m ON m.id=h.member_id WHERE m.active) activity ORDER BY created_at DESC,name,title LIMIT 40");ResultSet r=s.executeQuery()) {
            while(r.next()) {
                JsonObject entry=new JsonObject();entry.addProperty("name",r.getString(1));entry.addProperty("title",r.getString(2));
                entry.addProperty("points",r.getInt(3));entry.addProperty("category",r.getString(4));entry.addProperty("at",r.getTimestamp(5).toInstant().toString());entries.add(entry);
            }
        }
        return entries;
    }
}
