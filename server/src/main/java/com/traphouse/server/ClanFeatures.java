package com.traphouse.server;

import com.google.gson.*;
import java.sql.*;
import java.util.UUID;
import static com.traphouse.server.RankingStore.*;

/** Progress, event registration and race standings share the same verified member and ledger. */
final class ClanFeatures {
    static JsonObject member(Connection c,String name) throws SQLException {
        try(PreparedStatement s=query(c,"SELECT id,name FROM members WHERE normalized_name=? AND active",Policy.normalize(name));ResultSet r=s.executeQuery()) {
            if(!r.next())throw new Policy.Rejected(404,"Clan member not found");
            JsonObject result=dashboard(c,new Actor(r.getObject(1,UUID.class),r.getString(2),false),r.getString(2));
            result.remove("rewards"); result.remove("clanEvents");
            result.getAsJsonObject("profile").remove("permission");
            return result;
        }
    }

    static JsonObject sync(Connection c,Actor actor,JsonObject body) throws SQLException {
        if(body.has("points"))throw new Policy.Rejected(400,"Points are calculated by the server");
        if(!Policy.normalize(Policy.text(body,"player",12)).equals(Policy.normalize(actor.name)))throw new Policy.Rejected(403,"Character mismatch");
        if(!body.has("values")||!body.get("values").isJsonObject()||body.getAsJsonObject("values").size()>200)throw new Policy.Rejected(400,"At most 200 progress values required");
        lockMember(c,actor.id);
        for(java.util.Map.Entry<String,JsonElement> entry:body.getAsJsonObject("values").entrySet()) {
            UUID goal=Policy.uuid(entry.getKey());
            if(!entry.getValue().isJsonPrimitive()||!entry.getValue().getAsJsonPrimitive().isNumber())throw new Policy.Rejected(400,"Integer observation required");
            long value;
            try {value=entry.getValue().getAsBigDecimal().longValueExact();}catch(ArithmeticException invalid){throw new Policy.Rejected(400,"Integer observation required");}
            if(value<0||value>Integer.MAX_VALUE)throw new Policy.Rejected(400,"Observation outside range");
            try(PreparedStatement s=query(c,"SELECT target,points,category,name FROM goals WHERE id=? AND enabled AND source NOT IN ('EVENT','MANUAL')",goal);ResultSet r=s.executeQuery()) {
                if(!r.next())throw new Policy.Rejected(422,"Goal is not a configured client observation");
                progress(c,actor,goal,value,r.getLong(1),r.getInt(2),r.getString(3),r.getString(4));
            }
        }
        updateEventGoals(c,actor);
        return RankingStore.result(actor.id.toString(),"SYNCED",0);
    }

    static void updateEventGoals(Connection c,Actor actor) throws SQLException {
        try(PreparedStatement s=query(c,"SELECT id,source,source_key,target,points,category,name FROM goals WHERE enabled AND source IN ('EVENT','MANUAL') ORDER BY sort_order,id LIMIT 200");ResultSet r=s.executeQuery()) {
            while(r.next()) {
                String source=r.getString(2),key=r.getString(3);long count;
                if(source.equals("MANUAL")) {
                    try(PreparedStatement q=query(c,"SELECT count(*) FROM manual_rewards WHERE member_id=? AND event_key=? AND status='APPROVED'",actor.id,key);ResultSet counts=q.executeQuery()){counts.next();count=counts.getLong(1);}
                } else {
                    int separator=key.indexOf(':'); if(separator<1)continue;
                    String type=key.substring(0,separator),eventKey=key.substring(separator+1);
                    try(PreparedStatement q=query(c,"SELECT count(*) FROM events WHERE member_id=? AND type=? AND (?='*' OR event_key=?)",actor.id,type,eventKey,eventKey);ResultSet counts=q.executeQuery()){counts.next();count=counts.getLong(1);}
                }
                // Manual rewards already credit the ledger during independent approval.
                progress(c,actor,r.getObject(1,UUID.class),count,r.getLong(4),source.equals("MANUAL")?0:r.getInt(5),r.getString(6),r.getString(7));
            }
        }
    }

    private static void progress(Connection c,Actor actor,UUID goal,long value,long target,int points,String category,String name) throws SQLException {
        execute(c,"INSERT INTO goal_progress(member_id,goal_id,value) VALUES (?,?,?) ON CONFLICT(member_id,goal_id) DO UPDATE SET value=greatest(goal_progress.value,excluded.value),updated_at=now()",actor.id,goal,Math.min(value,target));
        try(PreparedStatement s=query(c,"SELECT value,completed_at,award_event FROM goal_progress WHERE member_id=? AND goal_id=?",actor.id,goal);ResultSet r=s.executeQuery()) {
            r.next(); if(r.getLong(1)<target||(r.getTimestamp(2)!=null&&(points==0||r.getObject(3)!=null)))return;
        }
        UUID event=null;
        if(points>0) {
            event=UUID.randomUUID();
            execute(c,"INSERT INTO events(id,member_id,client_id,type,event_key,observed_at,points) VALUES (?,?,?,'GOAL',?,now(),?)",event,actor.id,UUID.randomUUID(),goal.toString(),points);
            award(c,actor,event,null,points,name,category);
        }
        execute(c,"UPDATE goal_progress SET completed_at=now(),award_event=? WHERE member_id=? AND goal_id=?",event,actor.id,goal);
    }

    static JsonObject register(Connection c,Actor actor,UUID event,boolean join) throws SQLException {
        lockMember(c,actor.id);
        try(PreparedStatement s=query(c,"SELECT capacity,published AND registration_closes>now() AND ends_at>now() FROM clan_events WHERE id=? FOR UPDATE",event);ResultSet r=s.executeQuery()) {
            if(!r.next())throw new Policy.Rejected(404,"Event not found");
            if(!r.getBoolean(2))throw new Policy.Rejected(422,"Registration is closed");
            if(join) {
                try(PreparedStatement q=query(c,"SELECT count(*),count(*) FILTER(WHERE member_id=?) FROM event_registrations WHERE event_id=?",actor.id,event);ResultSet count=q.executeQuery()) {
                    count.next(); if(count.getLong(2)>0)return result(event.toString(),"REGISTERED",0);
                    if(count.getLong(1)>=r.getInt(1))throw new Policy.Rejected(422,"Event is full");
                }
                execute(c,"INSERT INTO event_registrations(event_id,member_id) VALUES (?,?)",event,actor.id);
            } else execute(c,"DELETE FROM event_registrations WHERE event_id=? AND member_id=?",event,actor.id);
            execute(c,"INSERT INTO audit(actor,action,target) VALUES (?,?,?)",actor.id,join?"EVENT_REGISTERED":"EVENT_CANCELLED",event);
            return result(event.toString(),join?"REGISTERED":"CANCELLED",0);
        }
    }

    static void enrich(Connection c,Actor actor,JsonObject data) throws SQLException {
        JsonObject breakdown=new JsonObject();
        try(PreparedStatement s=query(c,"SELECT category,sum(points) FROM point_entries WHERE member_id=? GROUP BY category",actor.id);ResultSet r=s.executeQuery()){while(r.next())breakdown.addProperty(r.getString(1),r.getLong(2));}
        data.getAsJsonObject("profile").add("breakdown",breakdown);
        JsonObject metrics=new JsonObject();
        metrics.addProperty("ehpRate",EfficiencyRewards.EHP_RATE);metrics.addProperty("ehbRate",EfficiencyRewards.EHB_RATE);metrics.addProperty("weeklyCap",EfficiencyRewards.WEEKLY_CAP);
        try(PreparedStatement s=query(c,"SELECT ehp,ehb,fetched_at FROM member_metrics WHERE member_id=?",actor.id);ResultSet r=s.executeQuery()){
            if(r.next()){metrics.addProperty("ehp",r.getBigDecimal(1));metrics.addProperty("ehb",r.getBigDecimal(2));metrics.addProperty("fetchedAt",r.getTimestamp(3).toInstant().toString());}
        }
        try(PreparedStatement s=query(c,"SELECT baseline_at,week_start,weekly_points FROM efficiency_reward_state WHERE member_id=?",actor.id);ResultSet r=s.executeQuery()) {
            if(r.next()) {
                metrics.addProperty("baselineAt",r.getTimestamp(1).toInstant().toString());
                metrics.addProperty("weeklyPoints",EfficiencyRewards.week(java.time.Instant.now()).equals(r.getTimestamp(2).toInstant())?r.getInt(3):0);
            }
        }
        data.getAsJsonObject("profile").add("metrics",metrics);
        JsonArray goals=new JsonArray();
        try(PreparedStatement s=query(c,"SELECT g.id,g.name,g.description,g.category,g.section,g.source,g.source_key,g.target,CASE WHEN g.source='MANUAL' THEN coalesce(r.points,0) ELSE g.points END,coalesce(p.value,0),p.completed_at IS NOT NULL FROM goals g LEFT JOIN goal_progress p ON p.goal_id=g.id AND p.member_id=? LEFT JOIN rules r ON r.type='MANUAL' AND r.event_key=g.source_key AND r.enabled WHERE g.enabled ORDER BY g.sort_order,g.id LIMIT 200",actor.id);ResultSet r=s.executeQuery()) {
            while(r.next()) {
                JsonObject goal=new JsonObject();goal.addProperty("id",r.getString(1));goal.addProperty("name",r.getString(2));goal.addProperty("description",r.getString(3));goal.addProperty("category",r.getString(4));goal.addProperty("section",r.getString(5));goal.addProperty("source",r.getString(6));goal.addProperty("sourceKey",r.getString(7));goal.addProperty("target",r.getLong(8));goal.addProperty("points",r.getInt(9));goal.addProperty("progress",r.getLong(10));goal.addProperty("completed",r.getBoolean(11));
                if(r.getString(6).equals("MANUAL")&&r.getInt(9)>0)goal.addProperty("claimKey",r.getString(7));goals.add(goal);
            }
        }
        data.add("goals",goals);
        JsonArray events=new JsonArray();
        try(PreparedStatement s=query(c,"SELECT e.id,e.title,e.description,e.starts_at,e.registration_closes>now(),(SELECT count(*) FROM event_registrations r WHERE r.event_id=e.id),EXISTS(SELECT 1 FROM event_registrations r WHERE r.event_id=e.id AND r.member_id=?) FROM clan_events e WHERE e.published AND e.ends_at>now() ORDER BY e.starts_at,e.id LIMIT 100",actor.id);ResultSet r=s.executeQuery()) {
            while(r.next()){JsonObject event=new JsonObject();event.addProperty("id",r.getString(1));event.addProperty("title",r.getString(2));event.addProperty("description",r.getString(3));event.addProperty("startsAt",r.getTimestamp(4).toInstant().toString());event.addProperty("registrationOpen",r.getBoolean(5));event.addProperty("registrations",r.getLong(6));event.addProperty("registered",r.getBoolean(7));events.add(event);}
        }
        data.add("clanEvents",events);data.add("races",races(c));
    }

    private static JsonArray races(Connection c) throws SQLException {
        JsonArray races=new JsonArray();
        try(PreparedStatement s=query(c,"SELECT id,title,description,starts_at,ends_at,score_type,score_key FROM races WHERE published AND ends_at>now()-interval '30 days' ORDER BY starts_at DESC,id LIMIT 10");ResultSet r=s.executeQuery()) {
            while(r.next()) {
                UUID id=r.getObject(1,UUID.class);JsonObject race=new JsonObject();race.addProperty("id",id.toString());race.addProperty("title",r.getString(2));race.addProperty("description",r.getString(3));race.addProperty("startsAt",r.getTimestamp(4).toInstant().toString());race.addProperty("endsAt",r.getTimestamp(5).toInstant().toString());
                JsonArray standings=new JsonArray();
                java.util.Map<Integer,Integer> prizes=new java.util.HashMap<>();
                try(PreparedStatement p=query(c,"SELECT place,points FROM race_prizes WHERE race_id=?",id);ResultSet prize=p.executeQuery()){while(prize.next())prizes.put(prize.getInt(1),prize.getInt(2));}
                try(PreparedStatement q=query(c,"SELECT m.name,count(*) AS score FROM events e JOIN members m ON m.id=e.member_id WHERE m.active AND e.received_at>=? AND e.received_at<? AND e.type=? AND e.event_key=? GROUP BY m.id,m.name ORDER BY score DESC,m.normalized_name,m.id LIMIT 1000",r.getTimestamp(4),r.getTimestamp(5),r.getString(6),r.getString(7));ResultSet rows=q.executeQuery()) {
                    int place=0;while(rows.next()){JsonObject row=new JsonObject();row.addProperty("name",rows.getString(1));row.addProperty("score",rows.getLong(2));
                        row.addProperty("projectedPoints",prizes.getOrDefault(++place,0));standings.add(row);}
                }
                race.add("standings",standings);JsonArray firsts=new JsonArray();
                try(PreparedStatement q=query(c,"SELECT label,event_type,event_key,points FROM race_firsts WHERE race_id=? ORDER BY label,id LIMIT 50",id);ResultSet rows=q.executeQuery()) {
                    while(rows.next()){JsonObject first=new JsonObject();first.addProperty("label",rows.getString(1));first.addProperty("points",rows.getInt(4));
                        try(PreparedStatement winner=query(c,"SELECT m.name FROM events e JOIN members m ON m.id=e.member_id WHERE m.active AND e.received_at>=? AND e.received_at<? AND e.type=? AND e.event_key=? ORDER BY e.received_at,e.id LIMIT 1",r.getTimestamp(4),r.getTimestamp(5),rows.getString(2),rows.getString(3));ResultSet found=winner.executeQuery()){first.addProperty("winner",found.next()?found.getString(1):"Unclaimed");}firsts.add(first);}
                }
                race.add("firsts",firsts);races.add(race);
            }
        }
        return races;
    }
}
