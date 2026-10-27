package com.traphouse.server;

import com.google.gson.*;
import java.sql.*;

/** A member's confirmed positive awards, paged in insertion order. No historical backfill. */
final class PointNotifications {
    static JsonObject read(Connection c,RankingStore.Actor actor,String query)throws SQLException {
        Long after=null;
        if(query!=null) {
            if(!query.matches("after=[0-9]{1,19}"))throw new Policy.Rejected(400,"Expected an award cursor");
            try {after=Long.parseLong(query.substring(6));}
            catch(NumberFormatException invalid){throw new Policy.Rejected(400,"Invalid award cursor");}
        }
        // Award writers lock this member before allocating notification IDs. Waiting here
        // ensures an in-flight award cannot later commit behind the returned cursor.
        RankingStore.lockMember(c,actor.id);
        long cursor=after==null?0:after;
        JsonArray awards=new JsonArray();
        if(after==null) {
            try(PreparedStatement s=RankingStore.query(c,"SELECT coalesce(max(id),0) FROM point_notifications WHERE member_id=?",actor.id);ResultSet r=s.executeQuery()) {
                r.next();cursor=r.getLong(1);
            }
        } else {
            try(PreparedStatement s=RankingStore.query(c,"SELECT n.id,n.points,n.title,l.category FROM point_notifications n JOIN ledger l ON l.id=n.ledger_id WHERE n.member_id=? AND n.id>? ORDER BY n.id LIMIT 100",actor.id,after);ResultSet r=s.executeQuery()) {
                while(r.next()) {
                    JsonObject award=new JsonObject();cursor=r.getLong(1);
                    award.addProperty("id",Long.toString(cursor));award.addProperty("points",r.getInt(2));award.addProperty("title",r.getString(3));award.addProperty("category",r.getString(4));awards.add(award);
                }
            }
        }
        JsonObject result=new JsonObject();result.addProperty("player",actor.name);
        result.addProperty("cursor",Long.toString(cursor));result.add("awards",awards);return result;
    }
}
