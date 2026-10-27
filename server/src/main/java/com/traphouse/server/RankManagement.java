package com.traphouse.server;

import com.google.gson.*;
import java.sql.*;
import static com.traphouse.server.RankingStore.*;

/** Owner-only editor data; display ranks never grant API permissions. */
final class RankManagement {
    static JsonObject read(Connection c,Actor actor)throws SQLException {
        RankSecurity.owner(actor);
        JsonObject data=new JsonObject();data.addProperty("ownerId",actor.id.toString());
        JsonArray members=new JsonArray();
        try(PreparedStatement s=query(c,"SELECT m.id,m.name,s.assigned_rank,coalesce(s.locked,false) FROM members m LEFT JOIN member_rank_state s ON s.member_id=m.id WHERE m.active ORDER BY m.normalized_name,m.id");ResultSet r=s.executeQuery()) {
            while(r.next()){JsonObject member=new JsonObject();member.addProperty("id",r.getString(1));member.addProperty("name",r.getString(2));member.addProperty("assignedRank",RankSecurity.canonicalRank(c,r.getString(3)));member.addProperty("locked",r.getBoolean(4));members.add(member);}
        }
        data.add("members",members);return data;
    }
    static void initializeOwnerRanks(Connection c)throws SQLException {
        try(Statement s=c.createStatement()){s.execute("SELECT pg_advisory_xact_lock(72419051001)");}
        try(PreparedStatement s=query(c,"SELECT 1 FROM configuration_migrations WHERE name='owner-display-rank-v1'");ResultSet r=s.executeQuery()){if(r.next())return;}
        try(PreparedStatement s=query(c,"SELECT m.id FROM members m JOIN member_permissions p ON p.member_id=m.id LEFT JOIN member_rank_state state ON state.member_id=m.id WHERE m.active AND p.permission='OWNER' AND state.assigned_rank IS NULL FOR UPDATE OF m");ResultSet r=s.executeQuery()) {
            while(r.next())assignOwner(c,r.getObject(1,java.util.UUID.class));
        }
        execute(c,"INSERT INTO configuration_migrations(name) VALUES ('owner-display-rank-v1')");
    }
    static void assignOwner(Connection c,java.util.UUID member)throws SQLException {
        execute(c,"INSERT INTO member_rank_state(member_id,assigned_rank,locked) VALUES (?,'Owner',true) ON CONFLICT(member_id) DO UPDATE SET assigned_rank='Owner',locked=true",member);
        JsonObject details=new JsonObject();details.addProperty("source","server operator owner provisioning");details.addProperty("rank","Owner");
        RankSecurity.audit(c,new Actor(member,"",RankSecurity.Role.OWNER),"OWNER_DISPLAY_RANK_INITIALIZED",member,details);
    }
}
