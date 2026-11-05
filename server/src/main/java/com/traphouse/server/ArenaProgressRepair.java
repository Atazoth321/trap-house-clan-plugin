package com.traphouse.server;

import com.google.gson.*;
import java.sql.*;
import java.time.Instant;
import java.util.UUID;
import static com.traphouse.server.RankingStore.*;

/** Operator incident repair. All writes share the caller's transaction and member lock. */
final class ArenaProgressRepair {
    static void validateWindow(Instant start, Instant end) {
        if (!start.isBefore(end) || java.time.Duration.between(start, end).compareTo(java.time.Duration.ofHours(24)) > 0)
            throw new IllegalArgumentException("Choose an incident window of at most 24 hours, with start before end");
    }

    static UUID member(Connection c, String name) throws SQLException {
        try (PreparedStatement s = query(c, "SELECT id FROM members WHERE normalized_name=? AND active FOR UPDATE", Policy.normalize(name)); ResultSet r = s.executeQuery()) {
            if (!r.next()) throw new IllegalArgumentException("Active member not found");
            return r.getObject(1, UUID.class);
        }
    }

    static JsonObject preview(Connection c, UUID member, Instant start, Instant end) throws SQLException {
        validateWindow(start, end);
        lockMember(c, member);
        JsonArray goals = new JsonArray();
        long total = 0;
        try (PreparedStatement s = query(c,
            "SELECT g.id,g.name,g.source,p.value,p.completed_at,p.award_event FROM goal_progress p JOIN goals g ON g.id=p.goal_id "
            + "WHERE p.member_id=? AND p.completed_at>=? AND p.completed_at<? AND g.source NOT IN ('EVENT','MANUAL') ORDER BY g.id",
            member, Timestamp.from(start), Timestamp.from(end)); ResultSet r = s.executeQuery()) {
            while (r.next()) {
                UUID goal = r.getObject(1, UUID.class);
                JsonObject row = new JsonObject();
                row.addProperty("id", goal.toString()); row.addProperty("name", r.getString(2)); row.addProperty("source", r.getString(3));
                row.addProperty("previousValue", r.getLong(4)); row.addProperty("completedAt", r.getTimestamp(5).toInstant().toString());
                row.addProperty("awardEvent", r.getString(6));
                JsonArray awards = new JsonArray();
                try (PreparedStatement q = query(c, "SELECT e.id,l.points FROM events e JOIN ledger l ON l.event_id=e.id WHERE e.member_id=? AND e.type='GOAL' AND e.event_key=? AND NOT EXISTS (SELECT 1 FROM reversed_goal_awards x WHERE x.event_id=e.id) ORDER BY e.id", member, goal.toString()); ResultSet a = q.executeQuery()) {
                    while (a.next()) {
                        JsonObject award = new JsonObject(); award.addProperty("id", a.getString(1)); award.addProperty("points", a.getInt(2));
                        awards.add(award); total += a.getInt(2);
                    }
                }
                row.add("awards", awards); goals.add(row);
            }
        }
        JsonObject plan = new JsonObject();
        plan.addProperty("memberId", member.toString()); plan.addProperty("start", start.toString()); plan.addProperty("end", end.toString());
        plan.add("goals", goals); plan.addProperty("pointsToReverse", total);
        plan.addProperty("previewHash", Policy.hash(plan.toString()));
        return plan;
    }

    static JsonObject apply(Connection c, UUID member, Instant start, Instant end, UUID owner, String hash, String reason) throws SQLException {
        if (reason == null || reason.trim().length() < 10 || reason.length() > 900)
            throw new IllegalArgumentException("Give a repair reason of 10-900 characters");
        Actor actor;
        try (PreparedStatement s = query(c, "SELECT m.name FROM members m JOIN member_permissions p ON p.member_id=m.id WHERE m.id=? AND m.active AND p.permission='OWNER' FOR UPDATE OF m,p", owner); ResultSet r = s.executeQuery()) {
            if (!r.next()) throw new IllegalArgumentException("An active backend Owner is required for deductions");
            actor = new Actor(owner, r.getString(1), RankSecurity.Role.OWNER);
        }
        JsonObject plan = preview(c, member, start, end);
        if (!plan.get("previewHash").getAsString().equals(hash))
            throw new IllegalArgumentException("Repair preview changed; preview again before applying");
        if (plan.getAsJsonArray("goals").size() == 0) throw new IllegalArgumentException("No matching completed goals to repair");
        UUID repair = UUID.randomUUID();
        long remaining = plan.get("pointsToReverse").getAsLong();
        // Use the existing permission checks, balance checks and permanent adjustment audit.
        while (remaining > 0) {
            int amount = (int) Math.min(100000L, remaining);
            JsonObject adjustment = new JsonObject(); adjustment.addProperty("id", UUID.randomUUID().toString());
            adjustment.addProperty("points", -amount); adjustment.addProperty("reason", "Arena repair " + repair + ": " + reason.trim());
            RankSecurity.adjust(c, actor, member, adjustment); remaining -= amount;
        }
        for (JsonElement element : plan.getAsJsonArray("goals")) {
            JsonObject goal = element.getAsJsonObject();
            for (JsonElement award : goal.getAsJsonArray("awards"))
                execute(c, "INSERT INTO reversed_goal_awards(event_id,repair_id) VALUES (?,?)", UUID.fromString(award.getAsJsonObject().get("id").getAsString()), repair);
            execute(c, "UPDATE goal_progress SET value=0,completed_at=NULL,award_event=NULL,updated_at=now() WHERE member_id=? AND goal_id=?", member, UUID.fromString(goal.get("id").getAsString()));
        }
        plan.addProperty("repairId", repair.toString()); plan.addProperty("reason", reason.trim()); plan.addProperty("source", "server operator CLI; authorized by supplied backend Owner");
        RankSecurity.audit(c, actor, "ARENA_PROGRESS_REPAIRED", member, plan);
        return plan;
    }
}
