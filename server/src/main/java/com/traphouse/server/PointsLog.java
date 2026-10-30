package com.traphouse.server;

import com.google.gson.*;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.*;
import static com.traphouse.server.RankingStore.query;

/** Read-only, bounded member ledger browser. All user values are bound parameters. */
final class PointsLog {
    static final List<String> CATEGORIES=Arrays.asList("All","Drops","Pets","Milestones","Diaries","Challenges","Events","Combat","Collection","Efficiency","Misc");
    static Map<String,String> parameters(String raw) {
        if(raw==null||raw.length()>2000)throw new Policy.Rejected(400,"Invalid log query");
        Map<String,String> p=new HashMap<>();
        for(String part:raw.split("&")) {
            String[] pair=part.split("=",2);
            if(pair.length!=2)throw new Policy.Rejected(400,"Invalid log query");
            String key=URLDecoder.decode(pair[0],StandardCharsets.UTF_8),value=URLDecoder.decode(pair[1],StandardCharsets.UTF_8);
            if(!Arrays.asList("player","category","sort","search","group","page").contains(key)||p.put(key,value)!=null)throw new Policy.Rejected(400,"Invalid log parameter");
        }
        p.putIfAbsent("category","All");p.putIfAbsent("sort","newest");p.putIfAbsent("search","");p.putIfAbsent("group","false");p.putIfAbsent("page","1");
        if(!p.getOrDefault("player","").matches("[A-Za-z0-9 _-]{1,12}")||!CATEGORIES.contains(p.get("category"))||!Arrays.asList("newest","oldest","highest").contains(p.get("sort"))||p.get("search").length()>100||!Arrays.asList("true","false").contains(p.get("group"))||!p.get("page").matches("[1-9][0-9]{0,5}"))throw new Policy.Rejected(400,"Invalid log filter");
        return p;
    }
    static JsonObject read(Connection c,String raw)throws SQLException {
        Map<String,String> p=parameters(raw);UUID member;String name;
        try(PreparedStatement s=query(c,"SELECT id,name FROM members WHERE normalized_name=? AND active",Policy.normalize(p.get("player")));ResultSet r=s.executeQuery()) {
            if(!r.next())throw new Policy.Rejected(404,"Member unavailable");member=r.getObject(1,UUID.class);name=r.getString(2);
        }
        String base="WITH entries AS (SELECT l.category,l.points,l.created_at,coalesce(g.name,a.reason,nullif(rule.display_name,''),e.event_key,r.event_key,'Award') AS title,coalesce(e.type,'MANUAL') AS source_type,coalesce(e.event_key,r.event_key,'') AS source_key FROM point_entries l LEFT JOIN point_adjustments a ON a.id=l.id LEFT JOIN events e ON e.id=l.event_id LEFT JOIN manual_rewards r ON r.id=l.reward_id LEFT JOIN goals g ON e.type='GOAL' AND g.id::text=e.event_key LEFT JOIN rules rule ON rule.type=coalesce(e.type,'MANUAL') AND rule.event_key=coalesce(e.event_key,r.event_key) WHERE l.member_id=?), filtered AS (SELECT * FROM entries WHERE (?='All' OR category=?) AND strpos(lower(title),lower(?))>0), rows AS (";
        base+=p.get("group").equals("true")?"SELECT category,title,source_type,source_key,sum(points) AS points,max(created_at) AS created_at,count(*) AS occurrences FROM filtered GROUP BY category,title,source_type,source_key) ":"SELECT *,1 AS occurrences FROM filtered) ";
        Object[] args={member,p.get("category"),p.get("category"),p.get("search")};
        JsonObject result=new JsonObject();long count,total;
        try(PreparedStatement s=query(c,base+"SELECT count(*),coalesce(sum(points),0) FROM rows",args);ResultSet r=s.executeQuery()){r.next();count=r.getLong(1);total=r.getLong(2);}
        int pages=(int)Math.max(1,(count+23)/24),page=Math.min(Integer.parseInt(p.get("page")),pages);
        String order=p.get("sort").equals("highest")?"points DESC,created_at DESC":p.get("sort").equals("oldest")?"created_at ASC":"created_at DESC";
        JsonArray entries=new JsonArray();
        try(PreparedStatement s=query(c,base+"SELECT category,title,points,created_at,occurrences,source_type,source_key FROM rows ORDER BY "+order+",category,title,points LIMIT 24 OFFSET "+((page-1)*24),args);ResultSet r=s.executeQuery()) {
            while(r.next()){JsonObject e=new JsonObject();e.addProperty("category",r.getString(1));e.addProperty("title",r.getString(2));e.addProperty("points",r.getLong(3));e.addProperty("at",r.getTimestamp(4).toInstant().toString());e.addProperty("occurrences",r.getLong(5));e.addProperty("sourceType",r.getString(6));e.addProperty("sourceKey",r.getString(7));entries.add(e);}
        }
        result.addProperty("player",name);result.addProperty("count",count);result.addProperty("total",total);result.addProperty("page",page);result.addProperty("pages",pages);result.add("entries",entries);
        if(p.get("category").equals("Drops"))result.add("availableDrops",dropRewards(c,member,p.get("search"),p.get("sort")));
        result.add("availableRewards",RewardCatalog.read(c,member,p.get("category"),p.get("search"),p.get("sort")));
        return result;
    }
    static JsonArray dropRewards(Connection c,UUID member,String search,String sort)throws SQLException {
        JsonArray rewards=new JsonArray();
        // Read current enabled server policy, not historical awards or hardcoded prices.
        // Collapse identical NPC phase rules into a single item card.
        String sql="WITH earned AS (SELECT coalesce(e.type,'MANUAL') AS type,coalesce(e.event_key,m.event_key) AS event_key,count(*) AS occurrences,sum(l.points) AS earned_points FROM ledger l LEFT JOIN events e ON e.id=l.event_id LEFT JOIN manual_rewards m ON m.id=l.reward_id WHERE l.member_id=? GROUP BY coalesce(e.type,'MANUAL'),coalesce(e.event_key,m.event_key)) SELECT rule.type,coalesce(nullif(rule.display_name,''),rule.event_key) AS title,rule.points,rule.daily_limit,min(rule.event_key) AS source_key,coalesce(sum(earned.occurrences),0),coalesce(sum(earned.earned_points),0) FROM rules rule LEFT JOIN earned ON earned.type=rule.type AND earned.event_key=rule.event_key WHERE rule.enabled AND (rule.category='Drops' OR (rule.category='' AND rule.type='LOOT')) AND strpos(lower(coalesce(nullif(rule.display_name,''),rule.event_key)),lower(?))>0 GROUP BY rule.type,coalesce(nullif(rule.display_name,''),rule.event_key),rule.points,rule.daily_limit ORDER BY "+(sort.equals("highest")?"points DESC,title":"title,points DESC")+",rule.type,source_key LIMIT 1000";
        try(PreparedStatement s=query(c,sql,member,search);ResultSet r=s.executeQuery()) {
            while(r.next()) {
                JsonObject item=new JsonObject();item.addProperty("sourceType",r.getString(1));item.addProperty("title",r.getString(2));
                item.addProperty("points",r.getInt(3));item.addProperty("dailyLimit",r.getInt(4));item.addProperty("sourceKey",r.getString(5));item.addProperty("category","Drops");
                item.addProperty("receivedCount",r.getLong(6));item.addProperty("earnedPoints",r.getLong(7));item.addProperty("received",r.getLong(6)>0);
                rewards.add(item);
            }
        }
        return rewards;
    }
}
