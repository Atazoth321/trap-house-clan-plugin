package com.traphouse.server;

import java.sql.*;
import java.time.*;
import java.util.UUID;

/** Server-owned, full seven-day membership periods; caller owns the transaction. */
final class MembershipRewards {
    static final int POINTS=25;
    static long completedWeeks(Instant start,Instant now) {
        return Math.max(0,Duration.between(start,now).getSeconds()/604800);
    }
    static void award(Connection c,UUID member,Instant now)throws SQLException {
        // Same lock order as other point mutations. Active status does not affect tenure.
        String name;
        try(PreparedStatement s=RankingStore.query(c,"SELECT name FROM members WHERE id=? FOR UPDATE",member);ResultSet r=s.executeQuery()) {
            if(!r.next())return;
            name=r.getString(1);
        }
        try(PreparedStatement s=RankingStore.query(c,"SELECT started_at,paid_weeks FROM membership_reward_state WHERE member_id=? FOR UPDATE",member);ResultSet r=s.executeQuery()) {
            if(!r.next())return;
            Instant start=r.getTimestamp(1).toInstant();long paid=r.getLong(2),completed=completedWeeks(start,now);
            // Bound recovery work per transaction after extended downtime.
            long through=Math.min(completed,paid+52);
            for(long week=paid+1;week<=through;week++) {
                UUID event=UUID.randomUUID();
                String key="Full week in clan: "+start+" / "+week;
                RankingStore.execute(c,"INSERT INTO events(id,member_id,client_id,type,event_key,observed_at,points) VALUES (?,?,?,'MEMBERSHIP',?,?,?)",
                    event,member,event,key,Timestamp.from(start.plusSeconds(week*604800)),POINTS);
                RankingStore.award(c,new RankingStore.Actor(member,name,false),event,null,POINTS,"Full week in clan","Misc");
            }
            if(through>paid)RankingStore.execute(c,"UPDATE membership_reward_state SET paid_weeks=? WHERE member_id=?",through,member);
        }
    }
    static void run(DiscordWorker.Connections connections) {
        try(Connection c=connections.open()) {
            c.setAutoCommit(false);
            // One due member per tick keeps the shared background worker responsive.
            UUID member=null;
            try(PreparedStatement s=RankingStore.query(c,"SELECT m.id FROM members m JOIN membership_reward_state s ON s.member_id=m.id WHERE extract(epoch FROM (now()-s.started_at)) >= (s.paid_weeks+1)*604800 ORDER BY s.started_at,m.id LIMIT 1");ResultSet r=s.executeQuery()) {
                if(r.next())member=r.getObject(1,UUID.class);
            }
            if(member!=null)award(c,member,Instant.now());
            c.commit();
        }catch(SQLException failure) {
            System.err.println("Weekly membership rewards failed (SQLSTATE "+failure.getSQLState()+"); will retry.");
        }
    }
}
