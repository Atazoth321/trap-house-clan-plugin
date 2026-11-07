package com.traphouse.server;

import java.util.*;
import java.sql.*;
import com.google.gson.JsonObject;

/** Personal drops and collection-log entries share a canonical once-only jar reward. */
final class JarRewards {
    private static final Map<String,String> ITEMS;
    private static final Map<String,String> COLLECTION_ITEMS;
    static {
        Map<String,String> items=new HashMap<>();
        Map<String,String> collections=new HashMap<>();
        for(AutomaticRewards.Rule rule:legacyRules()) {
            String item=rule.key.substring(rule.key.lastIndexOf(':')+1);
            items.put(rule.key,item);collections.put(Policy.normalize(rule.name),item);
        }
        ITEMS=Collections.unmodifiableMap(items);
        COLLECTION_ITEMS=Collections.unmodifiableMap(collections);
    }
    static String item(String type,String key){
        if(type.equals("LOOT"))return ITEMS.get(key);
        if(type.equals("COLLECTION"))return COLLECTION_ITEMS.get(key);
        return null;
    }
    static String collectionKey(String item) {
        for(Map.Entry<String,String> entry:COLLECTION_ITEMS.entrySet())if(entry.getValue().equals(item))return entry.getKey();
        return null;
    }
    static List<AutomaticRewards.Rule> rules() {
        Map<String,AutomaticRewards.Rule> rules=new LinkedHashMap<>();
        for(AutomaticRewards.Rule old:legacyRules()) {
            String key=Policy.normalize(old.name);
            rules.putIfAbsent(key,new AutomaticRewards.Rule("COLLECTION",key,old.name,"Drops",old.points,1));
        }
        return Collections.unmodifiableList(new ArrayList<>(rules.values()));
    }
    static void seed(Connection c)throws SQLException {
        try(Statement s=c.createStatement()){s.execute("SELECT pg_advisory_xact_lock(72419051001)");}
        String migration="boss-jar-collection-rewards-v2";
        try(PreparedStatement s=RankingStore.query(c,"SELECT 1 FROM configuration_migrations WHERE name=?",migration);ResultSet r=s.executeQuery()){if(r.next())return;}
        for(AutomaticRewards.Rule rule:rules()) {
            String item=item(rule.type,rule.key);
            // Carry forward operator edits; a disabled legacy source keeps the jar disabled.
            int points=rule.points,limit=1;boolean enabled=false;
            try(PreparedStatement s=RankingStore.query(c,"SELECT points,daily_limit,enabled FROM rules WHERE type='LOOT' AND event_key=ANY(?) ORDER BY enabled, (points<>?) DESC,event_key LIMIT 1",
                c.createArrayOf("text",legacyRules().stream().filter(old->ITEMS.get(old.key).equals(item)).map(old->old.key).toArray(String[]::new)),rule.points);ResultSet r=s.executeQuery()) {
                if(r.next()){points=r.getInt(1);limit=r.getInt(2);enabled=r.getBoolean(3);}
            }
            RankingStore.execute(c,"INSERT INTO rules(type,event_key,points,daily_limit,enabled,category,display_name) VALUES ('COLLECTION',?,?,?,?, 'Drops',?) ON CONFLICT(type,event_key) DO NOTHING",rule.key,points,limit,enabled,rule.name);
        }
        for(AutomaticRewards.Rule old:legacyRules())RankingStore.execute(c,"UPDATE rules SET enabled=false WHERE type='LOOT' AND event_key=?",old.key);
        RankingStore.execute(c,"INSERT INTO configuration_migrations(name) VALUES (?)",migration);
    }
    static void decorate(Connection c,UUID member,JsonObject card)throws SQLException {
        String type=card.get("sourceType").getAsString(),key=card.get("sourceKey").getAsString();
        String item=item(type,key);if(item==null)return;
        card.addProperty("onceOnly",true);card.addProperty("itemId",Integer.parseInt(item));
        if(!type.equals("COLLECTION"))return;
        try(PreparedStatement s=RankingStore.query(c,"SELECT count(*),coalesce(sum(l.points),0) FROM events e JOIN ledger l ON l.event_id=e.id WHERE e.member_id=? AND ((e.type='COLLECTION' AND e.event_key=?) OR (e.type='LOOT' AND substring(e.event_key from '[^:]+$')=?))",member,key,item);ResultSet r=s.executeQuery()) {
            r.next();card.addProperty("receivedCount",r.getLong(1));card.addProperty("received",r.getLong(1)>0);card.addProperty("earnedPoints",r.getLong(2));
        }
    }
    static List<AutomaticRewards.Rule> legacyRules() {
        List<AutomaticRewards.Rule> rules=new ArrayList<>();
        loot(rules,new int[]{494},12007,"Jar of dirt",250);
        loot(rules,new int[]{963,965},12885,"Jar of sand",500);
        loot(rules,new int[]{2042,2043,2044},12936,"Jar of swamp",350);
        loot(rules,new int[]{5862,5863,5866},13245,"Jar of souls",350);
        rules.add(new AutomaticRewards.Rule("LOOT","chest:unsired:13277","Jar of miasma","Drops",500,1));
        loot(rules,new int[]{7286},19701,"Jar of darkness",500);
        loot(rules,new int[]{7888,7889},21745,"Jar of stone",350);
        loot(rules,new int[]{8061},22106,"Jar of decay",500);
        loot(rules,new int[]{8615,8616,8617,8618,8619,8620,8621,8622},23064,"Jar of chemicals",500);
        loot(rules,new int[]{8713},23525,"Jar of eyes",250);
        loot(rules,new int[]{377,378,9416,9417,9418,9419,9420,9421,9422,9423,9424,9425,9426,9427,9428,9429,9430,9431,9432,9433,9460,9461,9462,9463,9464,11153,11154,11155},24495,"Jar of dreams",500);
        loot(rules,new int[]{319},25521,"Jar of spirits",500);
        loot(rules,new int[]{499},25524,"Jar of smoke",250);
        loot(rules,new int[]{13668,13669},29786,"Jar of venom",350);
        loot(rules,new int[]{14860},32921,"Jar of feathers",350);
        loot(rules,new int[]{16305,16306,16307,16308},34030,"Jar of light",500);
        return Collections.unmodifiableList(rules);
    }
    private static void loot(List<AutomaticRewards.Rule> rules,int[] npcs,int item,String name,int points) {
        for(int npc:npcs)rules.add(new AutomaticRewards.Rule("LOOT",npc+":"+item,name,"Drops",points,1));
    }
}
