package com.traphouse.server;

import com.google.gson.*;
import java.sql.*;
import java.time.Instant;
import java.util.UUID;

/** All mutations run in the caller's transaction. A member row lock serializes daily caps. */
final class RankingStore {
    static final class Actor {
        final UUID id;
        final String name;
        final boolean admin;
        final RankSecurity.Role role;
        Actor(UUID id, String name, boolean admin) { this(id,name,admin ? RankSecurity.Role.ADMINISTRATOR : RankSecurity.Role.MEMBER); }
        Actor(UUID id, String name, RankSecurity.Role role) { this.id=id; this.name=name; this.role=role; this.admin=role==RankSecurity.Role.OWNER || role==RankSecurity.Role.ADMINISTRATOR; }
    }

    static PreparedStatement query(Connection c, String sql, Object... args) throws SQLException {
        PreparedStatement statement = c.prepareStatement(sql);
        for (int i = 0; i < args.length; i++) statement.setObject(i + 1, args[i]);
        return statement;
    }

    static void execute(Connection c, String sql, Object... args) throws SQLException {
        try (PreparedStatement s = query(c, sql, args)) { s.executeUpdate(); }
    }

    static Actor authenticate(Connection c, String authorization) throws SQLException {
        if (authorization == null || !authorization.startsWith("Bearer ") || authorization.length() > 256) {
            throw new Policy.Rejected(401, "Member credential required");
        }
        try (PreparedStatement s = query(c, "SELECT m.id,m.name,coalesce(p.permission,CASE WHEN k.administrator THEN 'ADMINISTRATOR' ELSE 'MEMBER' END) FROM credentials k JOIN members m ON m.id=k.member_id LEFT JOIN member_permissions p ON p.member_id=m.id WHERE k.token_hash=? AND NOT k.revoked AND k.expires_at>now() AND m.active", Policy.hash(authorization.substring(7)));
             ResultSet r = s.executeQuery()) {
            if (!r.next()) throw new Policy.Rejected(401, "Invalid credential or inactive membership");
            return new Actor(r.getObject(1, UUID.class), r.getString(2), RankSecurity.Role.valueOf(r.getString(3)));
        }
    }

    static void lockMember(Connection c, UUID member) throws SQLException {
        try (PreparedStatement s = query(c, "SELECT active FROM members WHERE id=? FOR UPDATE", member); ResultSet r = s.executeQuery()) {
            if (!r.next() || !r.getBoolean(1)) throw new Policy.Rejected(403, "Active TRAP HOUSE membership required");
        }
    }

    static JsonObject event(Connection c, Actor actor, JsonObject body) throws SQLException {
        if (!Policy.normalize(Policy.text(body, "player", 12)).equals(Policy.normalize(actor.name))) {
            throw new Policy.Rejected(403, "Credential belongs to a different character");
        }
        UUID client = Policy.uuid(Policy.text(body, "id", 36));
        String type = Policy.text(body, "type", 12);
        Policy.observationType(type);
        String key = Policy.normalize(Policy.text(body, "key", 200));
        Instant observed = Policy.observed(Policy.text(body, "observedAt", 40), Instant.now());
        if (body.has("points")) throw new Policy.Rejected(400, "Points are calculated by the server");
        lockMember(c, actor.id);
        // Return the stored award for exact retries; unlocks additionally have a semantic unique index.
        try (PreparedStatement s = query(c, "SELECT id,points,type,event_key FROM events WHERE member_id=? AND client_id=?", actor.id, client); ResultSet r = s.executeQuery()) {
            if (r.next()) {
                if (!type.equals(r.getString(3)) || !key.equals(r.getString(4))) throw new Policy.Rejected(409, "Event ID reused with different evidence");
                return result(r.getObject(1).toString(), "DUPLICATE", r.getInt(2));
            }
        }
        int[] rule = rule(c, type, key);
        // A new request ID cannot repay the same collection unlock, even tomorrow.
        // Check before the daily cap so an already-paid pet is a clean duplicate.
        if(type.equals("COLLECTION")||type.equals("COMBAT")) {
            try(PreparedStatement s=query(c,"SELECT id FROM events WHERE member_id=? AND type=? AND event_key=?",actor.id,type,key);ResultSet r=s.executeQuery()) {
                if(r.next())return result(r.getString(1),"DUPLICATE",0);
            }
        }
        checkCap(c, actor.id, type, key, rule[1]);
        UUID id = UUID.randomUUID();
        int inserted;
        try (PreparedStatement s = query(c, "INSERT INTO events(id,member_id,client_id,type,event_key,observed_at,points) VALUES (?,?,?,?,?,?,?) ON CONFLICT DO NOTHING",
            id, actor.id, client, type, key, Timestamp.from(observed), rule[0])) { inserted = s.executeUpdate(); }
        if (inserted == 0) return result(client.toString(), "DUPLICATE", 0);
        award(c, actor, id, null, rule[0], type + ": " + key, category(c,type,key));
        ClanFeatures.updateEventGoals(c,actor);
        return result(id.toString(), "AWARDED", rule[0]);
    }

    static int[] rule(Connection c, String type, String key) throws SQLException {
        try (PreparedStatement s = query(c, "SELECT points,daily_limit FROM rules WHERE type=? AND event_key=? AND enabled", type, key); ResultSet r = s.executeQuery()) {
            if (!r.next()) throw new Policy.Rejected(422, "No enabled scoring rule for this observation");
            return new int[] {r.getInt(1), r.getInt(2)};
        }
    }

    static void checkCap(Connection c, UUID member, String type, String key, int limit) throws SQLException {
        String sql = type.equals("MANUAL")
            ? "SELECT count(*) FROM manual_rewards WHERE member_id=? AND event_key=? AND status='APPROVED' AND reviewed_at >= date_trunc('day',now() AT TIME ZONE 'UTC') AT TIME ZONE 'UTC'"
            : "SELECT count(*) FROM events WHERE member_id=? AND event_key=? AND type=? AND received_at >= date_trunc('day',now() AT TIME ZONE 'UTC') AT TIME ZONE 'UTC'";
        try (PreparedStatement s = type.equals("MANUAL") ? query(c, sql, member, key) : query(c, sql, member, key, type); ResultSet r = s.executeQuery()) {
            r.next();
            if (r.getLong(1) >= limit) throw new Policy.Rejected(422, "Daily award limit reached");
        }
    }

    static JsonObject requestReward(Connection c, Actor actor, JsonObject body) throws SQLException {
        UUID request = Policy.uuid(Policy.text(body, "id", 36));
        String key = Policy.normalize(Policy.text(body, "key", 200));
        String reason = Policy.text(body, "reason", 1000);
        if (body.has("points") || body.has("memberId")) throw new Policy.Rejected(400, "Reward points and recipient are server controlled");
        lockMember(c, actor.id);
        try (PreparedStatement s = query(c, "SELECT id,points,status,event_key,reason FROM manual_rewards WHERE member_id=? AND request_id=?", actor.id, request); ResultSet r = s.executeQuery()) {
            if (r.next()) {
                if (!key.equals(r.getString(4)) || !reason.equals(r.getString(5))) throw new Policy.Rejected(409, "Request ID reused with different evidence");
                return result(r.getObject(1).toString(), r.getString(3), r.getInt(2));
            }
        }
        int[] rule = rule(c, "MANUAL", key);
        try (PreparedStatement s = query(c, "SELECT count(*) FROM manual_rewards WHERE member_id=? AND status='PENDING'", actor.id); ResultSet r = s.executeQuery()) {
            r.next();
            if (r.getLong(1) >= 20) throw new Policy.Rejected(422, "Too many pending rewards");
        }
        UUID id = UUID.randomUUID();
        execute(c, "INSERT INTO manual_rewards(id,member_id,request_id,event_key,reason,points,daily_limit) VALUES (?,?,?,?,?,?,?)", id, actor.id, request, key, reason, rule[0], rule[1]);
        execute(c, "INSERT INTO audit(actor,action,target) VALUES (?,'REWARD_REQUESTED',?)", actor.id, id);
        return result(id.toString(), "PENDING", rule[0]);
    }

    static JsonObject review(Connection c, Actor actor, UUID id, boolean approve) throws SQLException {
        if (!actor.admin) throw new Policy.Rejected(403, "Administrator required");
        try (PreparedStatement s = query(c, "SELECT member_id,event_key,points,status FROM manual_rewards WHERE id=? FOR UPDATE", id); ResultSet r = s.executeQuery()) {
            if (!r.next()) throw new Policy.Rejected(404, "Reward not found");
            UUID member = r.getObject(1, UUID.class);
            Policy.independentReviewer(actor.admin, actor.id, member);
            String key = r.getString(2);
            int points = r.getInt(3);
            String status = r.getString(4);
            if (!status.equals("PENDING")) return result(id.toString(), status, points);
            lockMember(c, member);
            if (approve) {
                int[] currentRule = rule(c, "MANUAL", key);
                // A rule change invalidates pending requests rather than silently changing the award.
                if (currentRule[0] != points) throw new Policy.Rejected(409, "Rule changed; reject and request again");
                checkCap(c, member, "MANUAL", key, currentRule[1]);
            }
            String state = approve ? "APPROVED" : "REJECTED";
            execute(c, "UPDATE manual_rewards SET status=?,reviewed_by=?,reviewed_at=now() WHERE id=?", state, actor.id, id);
            JsonObject reviewDetails=new JsonObject();
            reviewDetails.addProperty("memberId",member.toString());
            reviewDetails.addProperty("points",approve?points:0);
            reviewDetails.addProperty("key",key);
            RankSecurity.audit(c,actor,"REWARD_"+state,id,reviewDetails);
            if (approve) {
                String name;
                try (PreparedStatement m = query(c, "SELECT name FROM members WHERE id=?", member); ResultSet row = m.executeQuery()) { row.next(); name = row.getString(1); }
                Actor recipient = new Actor(member, name, false);
                award(c, recipient, null, id, points, "Approved reward: " + key, category(c,"MANUAL",key));
                ClanFeatures.updateEventGoals(c,recipient);
            }
            return result(id.toString(), state, points);
        }
    }

    static String category(Connection c,String type,String key) throws SQLException {
        try(PreparedStatement s=query(c,"SELECT category FROM rules WHERE type=? AND event_key=?",type,key);ResultSet r=s.executeQuery()) {
            if(r.next()&&java.util.Set.of("Drops","Pets","Milestones","Combat","Collection","Diaries","Challenges","Events","Misc").contains(r.getString(1)))return r.getString(1);
        }
        return type.equals("LOOT")?"Drops":type.equals("COMBAT")?"Combat":type.equals("COLLECTION")?"Collection":"Misc";
    }

    static void award(Connection c, Actor actor, UUID event, UUID reward, int points, String description,String category) throws SQLException {
        execute(c, "INSERT INTO ledger(id,member_id,points,event_id,reward_id,category) VALUES (?,?,?,?,?,?)", UUID.randomUUID(), actor.id, points, event, reward,category);
        execute(c, "INSERT INTO discord_outbox(id,content) VALUES (?,?)", UUID.randomUUID(), actor.name + " earned " + points + " TRAP HOUSE points. " + description);
    }

    static JsonObject dashboard(Connection c, Actor actor, String player) throws SQLException {
        if (!Policy.normalize(actor.name).equals(Policy.normalize(player))) throw new Policy.Rejected(403, "Character mismatch");
        JsonObject data = new JsonObject();
        long points;
        try (PreparedStatement s = query(c, "SELECT coalesce(sum(points),0) FROM point_entries WHERE member_id=?", actor.id); ResultSet r = s.executeQuery()) { r.next(); points = r.getLong(1); }
        JsonObject profile = new JsonObject();
        profile.addProperty("name", actor.name);
        profile.addProperty("points", points);
        String rank = "Unranked", next = "Maximum rank reached";
        JsonArray ranks = new JsonArray();
        boolean foundNext = false;
        try (PreparedStatement s = query(c, "SELECT name,threshold FROM ranks WHERE name NOT IN (SELECT name FROM protected_ranks) AND name NOT IN (SELECT legacy_name FROM rank_aliases) ORDER BY threshold"); ResultSet r = s.executeQuery()) {
            while (r.next()) {
                JsonObject row = new JsonObject();
                row.addProperty("name", r.getString(1)); row.addProperty("threshold", r.getLong(2)); ranks.add(row);
                if (points >= r.getLong(2)) rank = r.getString(1);
                else if (!foundNext) { next = r.getString(1) + " at " + r.getLong(2) + " (" + (r.getLong(2) - points) + " remaining)"; foundNext = true; }
            }
        }
        profile.addProperty("rank", rank); profile.addProperty("nextThreshold", next);
        RankSecurity.enrich(c, actor.id, profile); profile.addProperty("permission",actor.role.name());
        data.add("profile", profile); data.add("ranks", ranks);
        JsonArray staffRanks=new JsonArray();
        try(PreparedStatement s=query(c,"SELECT name FROM protected_ranks WHERE name NOT IN (SELECT legacy_name FROM rank_aliases) ORDER BY CASE name WHEN 'Owner' THEN 0 WHEN 'Deputy Owner' THEN 1 WHEN 'Master' THEN 2 WHEN 'Major' THEN 3 WHEN 'Proselyte' THEN 4 ELSE 5 END,name");ResultSet r=s.executeQuery()) {
            while(r.next()){JsonObject row=new JsonObject();row.addProperty("name",r.getString(1));row.addProperty("protected",true);staffRanks.add(row);}
        }
        data.add("protectedRanks",staffRanks);
        JsonArray leaders = new JsonArray();
        try (PreparedStatement s = query(c, "SELECT m.name,coalesce(sum(l.points),0) AS total,coalesce(labels.rank_name,s.assigned_rank),coalesce(s.locked,false),p.name IS NOT NULL,a.rank FROM members m LEFT JOIN point_entries l ON m.id=l.member_id LEFT JOIN member_rank_state s ON s.member_id=m.id LEFT JOIN rank_aliases labels ON labels.legacy_name=s.assigned_rank LEFT JOIN protected_ranks p ON p.name=coalesce(labels.rank_name,s.assigned_rank) LEFT JOIN retained_automatic_ranks a ON a.member_id=m.id WHERE m.active GROUP BY m.id,m.name,s.assigned_rank,labels.rank_name,s.locked,p.name,a.rank ORDER BY total DESC,m.normalized_name,m.id LIMIT 1000"); ResultSet r = s.executeQuery()) {
            while (r.next()) { JsonObject row = new JsonObject(); row.addProperty("name", r.getString(1)); row.addProperty("points", r.getLong(2)); String eligible="Unranked";for(JsonElement tier:ranks){if(tier.getAsJsonObject().get("threshold").getAsLong()<=r.getLong(2))eligible=tier.getAsJsonObject().get("name").getAsString();}eligible=r.getString(6)==null?eligible:r.getString(6);row.addProperty("automaticRank",eligible);row.addProperty("assignedRank",r.getString(3));row.addProperty("rankLocked",r.getBoolean(4));row.addProperty("protectedRank",r.getBoolean(5));row.addProperty("rank",r.getString(3)==null?eligible:r.getString(3));leaders.add(row); }
        }
        data.add("leaderboard", leaders);
        JsonArray sources = new JsonArray();
        try (PreparedStatement s = query(c, "SELECT type,event_key,points,daily_limit,display_name FROM rules WHERE enabled ORDER BY type,event_key LIMIT 1000"); ResultSet r = s.executeQuery()) {
            while (r.next()) { JsonObject row = new JsonObject(); row.addProperty("type", r.getString(1)); row.addProperty("key", r.getString(2)); row.addProperty("points", r.getInt(3)); row.addProperty("dailyLimit", r.getInt(4));row.addProperty("name",r.getString(5).isEmpty()?r.getString(2):r.getString(5)); sources.add(row); }
        }
        data.add("sources", sources);
        JsonArray history = new JsonArray();
        try (PreparedStatement s = query(c, "SELECT l.points,l.created_at,coalesce(e.type,CASE WHEN a.id IS NOT NULL THEN 'ADJUSTMENT' ELSE 'MANUAL' END),coalesce(g.name,e.event_key,r.event_key,a.reason) FROM point_entries l LEFT JOIN point_adjustments a ON a.id=l.id LEFT JOIN events e ON e.id=l.event_id LEFT JOIN manual_rewards r ON r.id=l.reward_id LEFT JOIN goals g ON e.type='GOAL' AND g.id::text=e.event_key WHERE l.member_id=? ORDER BY l.created_at DESC,l.id LIMIT 20", actor.id); ResultSet r = s.executeQuery()) {
            while (r.next()) { JsonObject row = new JsonObject(); row.addProperty("points", r.getInt(1)); row.addProperty("at", r.getTimestamp(2).toInstant().toString()); row.addProperty("type", r.getString(3)); row.addProperty("key", r.getString(4)); history.add(row); }
        }
        data.add("history", history);
        JsonArray rewards = new JsonArray();
        try (PreparedStatement s = query(c, "SELECT id,event_key,points,status FROM manual_rewards WHERE member_id=? ORDER BY requested_at DESC,id LIMIT 20", actor.id); ResultSet r = s.executeQuery()) {
            while (r.next()) { JsonObject row = new JsonObject(); row.addProperty("id", r.getString(1)); row.addProperty("key", r.getString(2)); row.addProperty("points", r.getInt(3)); row.addProperty("status", r.getString(4)); rewards.add(row); }
        }
        data.add("rewards", rewards);
        ClanFeatures.enrich(c,actor,data);
        return data;
    }

    static JsonArray pending(Connection c, Actor actor) throws SQLException {
        if (!actor.admin) throw new Policy.Rejected(403, "Administrator required");
        JsonArray rewards = new JsonArray();
        try (PreparedStatement s = query(c, "SELECT r.id,m.name,r.event_key,r.reason,r.points FROM manual_rewards r JOIN members m ON m.id=r.member_id WHERE r.status='PENDING' ORDER BY r.requested_at,r.id LIMIT 100"); ResultSet r = s.executeQuery()) {
            while (r.next()) { JsonObject row = new JsonObject(); row.addProperty("id", r.getString(1)); row.addProperty("name", r.getString(2)); row.addProperty("key", r.getString(3)); row.addProperty("reason", r.getString(4)); row.addProperty("points", r.getInt(5)); rewards.add(row); }
        }
        return rewards;
    }

    static JsonObject result(String id, String status, int points) {
        JsonObject result = new JsonObject();
        result.addProperty("id", id); result.addProperty("status", status); result.addProperty("points", points);
        return result;
    }
}
