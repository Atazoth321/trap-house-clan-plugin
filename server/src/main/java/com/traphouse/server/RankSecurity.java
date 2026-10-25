package com.traphouse.server;

import com.google.gson.*;
import java.sql.*;
import java.util.UUID;
import static com.traphouse.server.RankingStore.*;

/** Application ranks are independent of OSRS ranks and of API permissions. */
final class RankSecurity {
    enum Role { OWNER, ADMINISTRATOR, EVENT_MANAGER, MEMBER }

    static void owner(Actor actor) {
        if (actor.role != Role.OWNER) throw new Policy.Rejected(403,"Owner required");
    }
    static void rankPermission(Actor actor, boolean protectedRank, boolean locked) {
        if (actor.role == Role.OWNER) return;
        if (actor.role != Role.ADMINISTRATOR || protectedRank || locked)
            throw new Policy.Rejected(403,"Owner required for protected or locked ranks; otherwise administrator required");
    }
    static void adjustmentPermission(Actor actor,int delta) {
        if(delta<0)owner(actor);
        else if(!actor.admin)throw new Policy.Rejected(403,"Administrator required to award points");
    }
    static void demotionPermission(Actor actor,String previous,String next,long previousThreshold,long nextThreshold) {
        if(!previous.equals(next) && !previous.equals("Unranked")
            && (previousThreshold<0 || nextThreshold<0 || nextThreshold<previousThreshold))owner(actor);
    }
    static boolean eligible(String automatic, String assigned, boolean locked, boolean protectedRank, long automaticThreshold, long assignedThreshold) {
        return !locked && !protectedRank && !automatic.equals("Unranked") && !automatic.equals(assigned)
            && (assigned == null || automaticThreshold > assignedThreshold);
    }
    static boolean protectedRank(Connection c,String rank) throws SQLException {
        if (rank == null) return false;
        try (PreparedStatement s=query(c,"SELECT 1 FROM protected_ranks p LEFT JOIN rank_aliases a ON a.legacy_name=p.name WHERE coalesce(a.rank_name,p.name)=? OR p.name=?",rank,rank); ResultSet r=s.executeQuery()) { return r.next(); }
    }
    static String canonicalRank(Connection c,String rank)throws SQLException {
        if(rank==null)return null;
        try(PreparedStatement s=query(c,"SELECT rank_name FROM rank_aliases WHERE legacy_name=?",rank);ResultSet r=s.executeQuery()){return r.next()?r.getString(1):rank;}
    }
    static long threshold(Connection c,String rank) throws SQLException {
        if(rank==null)return -1;
        rank=canonicalRank(c,rank);
        try(PreparedStatement s=query(c,"SELECT threshold FROM ranks WHERE name=?",rank);ResultSet r=s.executeQuery()){return r.next()?r.getLong(1):-1;}
    }
    static void enrich(Connection c,UUID member,JsonObject profile) throws SQLException {
        long points,lifetimeEarned;
        try(PreparedStatement s=query(c,"SELECT coalesce(sum(points),0),coalesce(sum(points) FILTER(WHERE points>0),0) FROM point_entries WHERE member_id=?",member);ResultSet r=s.executeQuery()){r.next();points=r.getLong(1);lifetimeEarned=r.getLong(2);}
        String automatic="Unranked", balanceRank="Unranked", assigned=null; long autoThreshold=-1; boolean locked=false;
        try(PreparedStatement s=query(c,"SELECT name,threshold FROM ranks WHERE threshold<=? AND name NOT IN (SELECT name FROM protected_ranks) AND name NOT IN (SELECT legacy_name FROM rank_aliases) ORDER BY threshold DESC LIMIT 1",points);ResultSet r=s.executeQuery()) {
            if(r.next())balanceRank=r.getString(1);
        }
        try(PreparedStatement s=query(c,"SELECT rank,threshold FROM retained_automatic_ranks WHERE member_id=?",member);ResultSet r=s.executeQuery()) {
            if(r.next()){automatic=r.getString(1);autoThreshold=r.getLong(2);}
        }
        try(PreparedStatement s=query(c,"SELECT assigned_rank,locked FROM member_rank_state WHERE member_id=?",member);ResultSet r=s.executeQuery()) {
            if(r.next()){assigned=canonicalRank(c,r.getString(1));locked=r.getBoolean(2);}
        }
        boolean protection=protectedRank(c,assigned);
        profile.addProperty("points",points);
        profile.addProperty("lifetimeEarnedPoints",lifetimeEarned);
        profile.addProperty("automaticRank",automatic);
        profile.addProperty("automaticRankThreshold",autoThreshold);
        profile.addProperty("balanceRank",balanceRank);
        JsonArray earned=new JsonArray();
        try(PreparedStatement s=query(c,"SELECT coalesce(a.rank_name,h.rank),h.threshold,h.points_at_earning,h.earned_at,h.rank FROM earned_rank_history h LEFT JOIN rank_aliases a ON a.legacy_name=h.rank WHERE h.member_id=? ORDER BY h.earned_at,h.threshold,h.rank",member);ResultSet r=s.executeQuery()) {
            while(r.next()){JsonObject row=new JsonObject();row.addProperty("rank",r.getString(1));row.addProperty("threshold",r.getLong(2));row.addProperty("pointsAtEarning",r.getLong(3));row.addProperty("at",r.getTimestamp(4).toInstant().toString());row.addProperty("originalRank",r.getString(5));earned.add(row);}
        }
        profile.add("earnedRankHistory",earned);
        profile.addProperty("assignedRank",assigned);
        profile.addProperty("rank",assigned==null?automatic:assigned);
        profile.addProperty("rankLocked",locked);
        profile.addProperty("protectedRank",protection);
        profile.addProperty("promotionEligible",eligible(automatic,assigned,locked,protection,autoThreshold,threshold(c,assigned)));
    }
    static void audit(Connection c,Actor actor,String action,UUID target,JsonObject details) throws SQLException {
        execute(c,"INSERT INTO audit(actor,action,target,details) VALUES (?,?,?,?::jsonb)",actor.id,action,target,details.toString());
    }
    static JsonObject changeRank(Connection c,Actor actor,UUID member,JsonObject body) throws SQLException {
        // Reject unprivileged callers before looking up any target state.
        rankPermission(actor,false,false);
        String action=Policy.text(body,"action",10);
        String reason=Policy.text(body,"reason",1000);
        if(!java.util.Set.of("ASSIGN","REMOVE","LOCK","UNLOCK").contains(action))throw new Policy.Rejected(400,"Invalid rank action");
        if(action.equals("LOCK")||action.equals("UNLOCK"))owner(actor);
        lockMember(c,member);
        JsonObject before=new JsonObject();enrich(c,member,before);
        String current=before.has("assignedRank")&&!before.get("assignedRank").isJsonNull()?before.get("assignedRank").getAsString():null;
        String rank=action.equals("ASSIGN")?canonicalRank(c,Policy.text(body,"rank",40)):current;
        rankPermission(actor,protectedRank(c,current)||protectedRank(c,rank),before.get("rankLocked").getAsBoolean());
        if(action.equals("ASSIGN")&&!protectedRank(c,rank)&&threshold(c,rank)<0)throw new Policy.Rejected(422,"Unknown rank");
        if(action.equals("REMOVE"))rank=null;
        boolean locked=before.get("rankLocked").getAsBoolean();
        if(action.equals("LOCK")) { locked=true; if(rank==null)rank=before.get("automaticRank").getAsString(); }
        if(action.equals("UNLOCK"))locked=false;
        String previous=before.get("rank").getAsString();
        String effective=rank==null?before.get("automaticRank").getAsString():rank;
        demotionPermission(actor,previous,effective,current==null?before.get("automaticRankThreshold").getAsLong():threshold(c,current),rank==null?before.get("automaticRankThreshold").getAsLong():threshold(c,rank));
        execute(c,"INSERT INTO member_rank_state(member_id,assigned_rank,locked) VALUES (?,?,?) ON CONFLICT(member_id) DO UPDATE SET assigned_rank=excluded.assigned_rank,locked=excluded.locked",member,rank,locked);
        JsonObject after=new JsonObject();enrich(c,member,after);
        JsonObject details=new JsonObject();details.add("before",before);details.add("after",after);details.addProperty("reason",reason);
        audit(c,actor,"RANK_"+action,member,details);
        return after;
    }
    static JsonObject adjust(Connection c,Actor actor,UUID member,JsonObject body) throws SQLException {
        if(!actor.admin)throw new Policy.Rejected(403,"Administrator required");
        UUID id=Policy.uuid(Policy.text(body,"id",36));String reason=Policy.text(body,"reason",1000);
        if(!body.has("points")||!body.get("points").isJsonPrimitive()||!body.getAsJsonPrimitive("points").isNumber())
            throw new Policy.Rejected(400,"Integer points required");
        int delta;
        try { delta=body.get("points").getAsBigDecimal().intValueExact(); }
        catch(RuntimeException invalid){throw new Policy.Rejected(400,"Integer points required");}
        if(delta==0||delta>100000||delta< -100000)throw new Policy.Rejected(400,"Adjustment must be between -100000 and 100000 and nonzero");
        adjustmentPermission(actor,delta);
        lockMember(c,member);
        try(PreparedStatement s=query(c,"SELECT member_id,actor,points,reason FROM point_adjustments WHERE id=?",id);ResultSet r=s.executeQuery()) {
            if(r.next()) {
                if(!member.equals(r.getObject(1,UUID.class))||!actor.id.equals(r.getObject(2,UUID.class))||delta!=r.getInt(3)||!reason.equals(r.getString(4)))throw new Policy.Rejected(409,"Adjustment ID reused");
                return result(id.toString(),"DUPLICATE",delta);
            }
        }
        JsonObject before=new JsonObject();enrich(c,member,before);
        if(before.get("points").getAsLong()+delta<0)throw new Policy.Rejected(422,"Points cannot become negative");
        execute(c,"INSERT INTO point_adjustments(id,member_id,points,actor,reason) VALUES (?,?,?,?,?)",id,member,delta,actor.id,reason);
        // The database insertion trigger permanently audits the adjustment with both identities.
        return result(id.toString(),"ADJUSTED",delta);
    }
    static JsonObject permission(Connection c,Actor actor,UUID member,JsonObject body)throws SQLException {
        owner(actor);Role role;
        try{role=Role.valueOf(Policy.text(body,"permission",20));}catch(IllegalArgumentException invalid){throw new Policy.Rejected(400,"Invalid permission");}
        // Owner identities are bootstrapped locally, never delegated through the API.
        if(role==Role.OWNER)throw new Policy.Rejected(403,"Owner provisioning requires the server operator");
        lockMember(c,member);
        try(PreparedStatement s=query(c,"SELECT permission FROM member_permissions WHERE member_id=?",member);ResultSet r=s.executeQuery()) {
            if(r.next()&&r.getString(1).equals("OWNER"))throw new Policy.Rejected(403,"Owner permissions require the server operator");
        }
        String reason=Policy.text(body,"reason",1000);
        execute(c,"INSERT INTO member_permissions VALUES (?,?) ON CONFLICT(member_id) DO UPDATE SET permission=excluded.permission",member,role.name());
        JsonObject details=new JsonObject();details.addProperty("permission",role.name());details.addProperty("reason",reason);audit(c,actor,"PERMISSION_CHANGED",member,details);return details;
    }
    static JsonArray auditLog(Connection c,Actor actor,long after)throws SQLException {
        if(!actor.admin)throw new Policy.Rejected(403,"Administrator required");
        JsonArray entries=new JsonArray();
        try(PreparedStatement s=query(c,"SELECT id,actor,action,target,created_at,details FROM audit WHERE id>? ORDER BY id LIMIT 200",after);ResultSet r=s.executeQuery()) {
            while(r.next()){JsonObject row=new JsonObject();row.addProperty("id",r.getLong(1));row.addProperty("actor",r.getString(2));row.addProperty("action",r.getString(3));row.addProperty("target",r.getString(4));row.addProperty("at",r.getTimestamp(5).toInstant().toString());row.add("details",JsonParser.parseString(r.getString(6)));entries.add(row);}
        }return entries;
    }
    /** Periodic reconciliation also catches threshold edits and promotions earned while offline. */
    static void promotions(DiscordWorker.Connections connections) {
        try(Connection c=connections.open()) {
            c.setAutoCommit(false);
            execute(c,"SET LOCAL statement_timeout='10s'");
            execute(c,"WITH candidates AS (SELECT m.id AS member_id,a.name AS rank FROM members m "
                + "LEFT JOIN member_rank_state s ON s.member_id=m.id "
                + "LEFT JOIN rank_aliases labels ON labels.legacy_name=s.assigned_rank LEFT JOIN ranks assigned ON assigned.name=coalesce(labels.rank_name,s.assigned_rank) "
                + "CROSS JOIN LATERAL (SELECT rank AS name,threshold FROM retained_automatic_ranks WHERE member_id=m.id "
                + "ORDER BY threshold DESC LIMIT 1) a "
                + "WHERE m.active AND NOT coalesce(s.locked,false) AND NOT EXISTS (SELECT 1 FROM protected_ranks p WHERE p.name=coalesce(labels.rank_name,s.assigned_rank)) "
                + "AND (s.assigned_rank IS NULL OR a.threshold>coalesce(assigned.threshold,-1))), "
                + "notices AS (INSERT INTO promotion_notices SELECT member_id,rank FROM candidates WHERE true ON CONFLICT DO NOTHING RETURNING member_id,rank) "
                + "INSERT INTO discord_outbox(id,content) SELECT gen_random_uuid(),m.name || ' qualifies for promotion to ' || n.rank || '. Leadership review required; in-game ranks are changed manually.' FROM notices n JOIN members m ON m.id=n.member_id");
            c.commit();
        }catch(SQLException unavailable){System.err.println("Promotion reconciliation unavailable; will retry.");}
    }

    static JsonObject publishEvent(Connection c,Actor actor,UUID event,JsonObject body)throws SQLException {
        if(actor.role==Role.MEMBER)throw new Policy.Rejected(403,"Event manager required");
        if(!body.has("published")||!body.get("published").isJsonPrimitive()||!body.getAsJsonPrimitive("published").isBoolean())throw new Policy.Rejected(400,"Boolean published required");
        boolean published=body.get("published").getAsBoolean();
        try(PreparedStatement s=query(c,"UPDATE clan_events SET published=? WHERE id=?",published,event)) {
            if(s.executeUpdate()==0)throw new Policy.Rejected(404,"Event not found");
        }
        JsonObject details=new JsonObject();details.addProperty("published",published);audit(c,actor,"EVENT_PUBLISHED",event,details);return details;
    }
}
