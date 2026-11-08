package com.traphouse.server;

import com.google.gson.JsonObject;
import java.sql.*;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import static com.traphouse.server.RankingStore.*;

/** Explicit owner verification of historical tenure, separate from automatic tracking. */
final class VerifiedMembership {
    static int points(LocalDate start,LocalDate end) {
        long weeks=ChronoUnit.DAYS.between(start,end)/7;
        if(weeks<1||weeks>4000)throw new Policy.Rejected(400,"Enter a range of at least 7 days and at most 4000 full weeks");
        return (int)weeks*MembershipRewards.POINTS;
    }
    static JsonObject award(Connection c,Actor actor,UUID member,JsonObject body)throws SQLException {
        RankSecurity.owner(actor);
        UUID id=Policy.uuid(Policy.text(body,"id",36));
        String reason=Policy.text(body,"reason",1000);
        LocalDate start,end;
        try{start=LocalDate.parse(Policy.text(body,"startDate",10));end=LocalDate.parse(Policy.text(body,"endDate",10));}
        catch(java.time.format.DateTimeParseException invalid){throw new Policy.Rejected(400,"Dates must use YYYY-MM-DD");}
        int points=points(start,end);
        if(end.isAfter(LocalDate.now(ZoneOffset.UTC)))throw new Policy.Rejected(400,"End date cannot be in the future");
        String name;
        try(PreparedStatement s=query(c,"SELECT name FROM members WHERE id=? FOR UPDATE",member);ResultSet r=s.executeQuery()) {
            if(!r.next())throw new Policy.Rejected(404,"Member not found");name=r.getString(1);
        }
        try(PreparedStatement s=query(c,"SELECT member_id,actor,start_date,end_date,reason FROM verified_membership_awards WHERE id=?",id);ResultSet r=s.executeQuery()) {
            if(r.next()) {
                if(!member.equals(r.getObject(1,UUID.class))||!actor.id.equals(r.getObject(2,UUID.class))||!start.equals(r.getDate(3).toLocalDate())||!end.equals(r.getDate(4).toLocalDate())||!reason.equals(r.getString(5)))
                    throw new Policy.Rejected(409,"Award ID reused with different details");
                return result(id.toString(),"DUPLICATE",points);
            }
        }
        try(PreparedStatement s=query(c,"SELECT started_at FROM membership_reward_state WHERE member_id=?",member);ResultSet r=s.executeQuery()) {
            if(!r.next())throw new Policy.Rejected(409,"Automatic tracking baseline is missing; run initialization");
            if(end.atStartOfDay(ZoneOffset.UTC).toInstant().isAfter(r.getTimestamp(1).toInstant()))
                throw new Policy.Rejected(400,"Historical range must end on or before "+r.getTimestamp(1).toInstant().atZone(ZoneOffset.UTC).toLocalDate());
        }
        try(PreparedStatement s=query(c,"SELECT 1 FROM verified_membership_awards WHERE member_id=? AND start_date<? AND end_date>?",member,Date.valueOf(end),Date.valueOf(start));ResultSet r=s.executeQuery()) {
            if(r.next())throw new Policy.Rejected(409,"This range overlaps an already awarded membership range");
        }
        String title="Verified clan membership: "+start+" to "+end;
        execute(c,"INSERT INTO events(id,member_id,client_id,type,event_key,observed_at,points) VALUES (?,?,?,'MEMBERSHIP',?,?,?)",id,member,id,title,Timestamp.from(end.atStartOfDay(ZoneOffset.UTC).toInstant()),points);
        execute(c,"INSERT INTO verified_membership_awards(id,member_id,actor,start_date,end_date,reason) VALUES (?,?,?,?,?,?)",id,member,actor.id,Date.valueOf(start),Date.valueOf(end),reason);
        RankingStore.award(c,new Actor(member,name,false),id,null,points,title,"Misc");
        JsonObject details=body.deepCopy();details.addProperty("points",points);details.addProperty("weeks",points/25);
        RankSecurity.audit(c,actor,"MEMBERSHIP_VERIFIED",member,details);
        return result(id.toString(),"AWARDED",points);
    }
}
