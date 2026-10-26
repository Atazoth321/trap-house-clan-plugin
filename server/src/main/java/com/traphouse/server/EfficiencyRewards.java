package com.traphouse.server;

import java.math.*;
import java.sql.*;
import java.time.*;
import java.time.temporal.TemporalAdjusters;
import java.util.UUID;

/** Positive rewards from server-fetched WOM deltas only. Never accepts client observations. */
final class EfficiencyRewards {
    static final BigDecimal EHP_RATE=BigDecimal.valueOf(10), EHB_RATE=BigDecimal.valueOf(10);
    static final int WEEKLY_CAP=250;
    static Instant week(Instant now) {
        return now.atZone(ZoneOffset.UTC).toLocalDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).atStartOfDay(ZoneOffset.UTC).toInstant();
    }
    static BigDecimal earned(BigDecimal oldEhp,BigDecimal oldEhb,BigDecimal ehp,BigDecimal ehb) {
        return ehp.subtract(oldEhp).max(BigDecimal.ZERO).multiply(EHP_RATE)
            .add(ehb.subtract(oldEhb).max(BigDecimal.ZERO).multiply(EHB_RATE));
    }
    static int payout(BigDecimal earned,int paid) {
        return earned.max(BigDecimal.ZERO).min(BigDecimal.valueOf(Math.max(0,WEEKLY_CAP-paid))).setScale(0,RoundingMode.DOWN).intValueExact();
    }
    static void observe(Connection c,RankingStore.Actor actor,BigDecimal ehp,BigDecimal ehb,Instant snapshot,Instant now)throws SQLException {
        RankingStore.lockMember(c,actor.id);
        // Old data may be displayed, but cannot establish an earning baseline or grant rewards.
        if(snapshot==null||snapshot.isBefore(now.minus(Duration.ofHours(24)))||snapshot.isAfter(now.plusSeconds(300)))return;
        ehp=ehp.setScale(8,RoundingMode.DOWN);ehb=ehb.setScale(8,RoundingMode.DOWN);
        Instant week=week(now);
        try(PreparedStatement s=RankingStore.query(c,"SELECT ehp_high,ehb_high,last_snapshot,week_start,weekly_points,carry FROM efficiency_reward_state WHERE member_id=? FOR UPDATE",actor.id);ResultSet r=s.executeQuery()) {
            if(!r.next()) {
                RankingStore.execute(c,"INSERT INTO efficiency_reward_state(member_id,ehp_high,ehb_high,last_snapshot,baseline_at,week_start) VALUES (?,?,?,?,?,?)",actor.id,ehp,ehb,Timestamp.from(snapshot),Timestamp.from(now),Timestamp.from(week));
                return;
            }
            if(!snapshot.isAfter(r.getTimestamp(3).toInstant()))return;
            BigDecimal oldEhp=r.getBigDecimal(1),oldEhb=r.getBigDecimal(2);
            boolean sameWeek=week.equals(r.getTimestamp(4).toInstant());
            int paid=sameWeek?r.getInt(5):0;
            BigDecimal amount=earned(oldEhp,oldEhb,ehp,ehb).add(sameWeek?r.getBigDecimal(6):BigDecimal.ZERO);
            int points=payout(amount,paid);
            BigDecimal carry=paid+points>=WEEKLY_CAP?BigDecimal.ZERO:amount.subtract(BigDecimal.valueOf(points));
            if(points>0) {
                UUID event=UUID.randomUUID();
                UUID request=UUID.nameUUIDFromBytes(("efficiency-v1:"+actor.id+":"+snapshot).getBytes(java.nio.charset.StandardCharsets.UTF_8));
                RankingStore.execute(c,"INSERT INTO events(id,member_id,client_id,type,event_key,observed_at,points) VALUES (?,?,?,'EFFICIENCY','WOM EHP/EHB gains',?,?)",event,actor.id,request,Timestamp.from(snapshot),points);
                RankingStore.award(c,actor,event,null,points,"Wise Old Man efficiency gains (weekly cap "+WEEKLY_CAP+")","Efficiency");
            }
            RankingStore.execute(c,"UPDATE efficiency_reward_state SET ehp_high=?,ehb_high=?,last_snapshot=?,week_start=?,weekly_points=?,carry=? WHERE member_id=?",
                oldEhp.max(ehp),oldEhb.max(ehb),Timestamp.from(snapshot),Timestamp.from(week),paid+points,carry,actor.id);
        }
    }
}
