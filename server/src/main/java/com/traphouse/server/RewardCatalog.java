package com.traphouse.server;

import com.google.gson.*;
import java.sql.*;
import java.util.*;
import static com.traphouse.server.RankingStore.query;

/** Current enabled rewards with this member's confirmed award history. */
final class RewardCatalog {
    static JsonArray read(Connection c,UUID member,String category,String search,String sort)throws SQLException {
        List<JsonObject> cards=new ArrayList<>();
        if(category.equals("All")||category.equals("Drops"))
            for(JsonElement drop:PointsLog.dropRewards(c,member,search,sort))cards.add(drop.getAsJsonObject());
        if(!category.equals("Drops")) {
            String sql="WITH earned AS (SELECT coalesce(e.type,'MANUAL') AS type,coalesce(e.event_key,m.event_key) AS event_key,count(*) AS occurrences,sum(l.points) AS earned_points FROM ledger l LEFT JOIN events e ON e.id=l.event_id LEFT JOIN manual_rewards m ON m.id=l.reward_id WHERE l.member_id=? GROUP BY coalesce(e.type,'MANUAL'),coalesce(e.event_key,m.event_key)), catalog AS ("
                +"SELECT rule.type,coalesce(nullif(rule.display_name,''),rule.event_key) AS title,rule.points,rule.daily_limit,min(rule.event_key) AS source_key,coalesce(sum(earned.occurrences),0) AS received_count,coalesce(sum(earned.earned_points),0) AS earned_points,CASE WHEN rule.category<>'' THEN rule.category WHEN rule.type='LOOT' THEN 'Drops' WHEN rule.type='COMBAT' THEN 'Combat' WHEN rule.type='COLLECTION' THEN 'Collection' ELSE 'Misc' END AS category,false AS once_only,null::text AS item_id FROM rules rule LEFT JOIN earned ON earned.type=rule.type AND earned.event_key=rule.event_key WHERE rule.enabled AND rule.points>0 GROUP BY rule.type,coalesce(nullif(rule.display_name,''),rule.event_key),rule.points,rule.daily_limit,rule.category "
                +"UNION ALL SELECT 'GOAL',g.name,g.points,1,g.id::text,coalesce(earned.occurrences,0),coalesce(earned.earned_points,0),g.category,true,CASE WHEN g.source='ITEM' THEN g.source_key ELSE null END FROM goals g LEFT JOIN earned ON earned.type='GOAL' AND earned.event_key=g.id::text WHERE g.enabled AND g.points>0 AND g.source<>'MANUAL') "
                +"SELECT * FROM catalog WHERE category<>'Drops' AND (?='All' OR category=?) AND strpos(lower(title),lower(?))>0 ORDER BY title,type,source_key LIMIT 1000";
            try(PreparedStatement s=query(c,sql,member,category,category,search);ResultSet r=s.executeQuery()) {
                while(r.next()) {
                    JsonObject card=new JsonObject();
                    card.addProperty("sourceType",r.getString("type"));card.addProperty("title",r.getString("title"));
                    card.addProperty("points",r.getInt("points"));card.addProperty("dailyLimit",r.getInt("daily_limit"));
                    card.addProperty("sourceKey",r.getString("source_key"));card.addProperty("category",r.getString("category"));
                    int clueItem=EncounterRewards.clueItem(r.getString("type"),r.getString("source_key"));
                    if(clueItem>0)card.addProperty("itemId",clueItem);
                    if("LOOT".equals(r.getString("type")))card.addProperty("capScope",BalancedRewards.sharedItem("LOOT",r.getString("source_key"))==null?"source":"item");
                    long received=r.getLong("received_count");card.addProperty("receivedCount",received);card.addProperty("received",received>0);
                    card.addProperty("earnedPoints",r.getLong("earned_points"));
                    card.addProperty("onceOnly",r.getBoolean("once_only")||Arrays.asList("COMBAT","COLLECTION").contains(r.getString("type")));
                    String item=r.getString("item_id");
                    if(item!=null&&item.matches("[0-9]{1,6}"))card.addProperty("itemId",Integer.parseInt(item));
                    cards.add(card);
                }
            }
        }
        Comparator<JsonObject> byName=Comparator.comparing(e->e.get("title").getAsString(),String.CASE_INSENSITIVE_ORDER);
        cards.sort(sort.equals("highest")?Comparator.<JsonObject>comparingInt(e->e.get("points").getAsInt()).reversed().thenComparing(byName):byName);
        JsonArray result=new JsonArray();for(JsonObject card:cards)result.add(card);return result;
    }
}
