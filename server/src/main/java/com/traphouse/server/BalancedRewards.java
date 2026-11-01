package com.traphouse.server;

import java.sql.*;
import java.util.*;

/** Future-only balance policy. Historic seed amounts remain available for safe migrations. */
final class BalancedRewards {
    static final int BARROWS_DAILY_LIMIT=4;
    private static final Map<String,AutomaticRewards.Rule> CATALOG;
    static {
        Map<String,AutomaticRewards.Rule> rules=new HashMap<>();
        for(AutomaticRewards.Rule rule:AutomaticRewards.rules())if(rule.type.equals("LOOT"))rules.put(rule.key,rule);
        for(AutomaticRewards.Rule rule:EncounterRewards.rules())if(rule.type.equals("LOOT"))rules.put(rule.key,rule);
        for(AutomaticRewards.Rule rule:ExpandedEncounterRewards.rules())if(rule.type.equals("LOOT"))rules.put(rule.key,rule);
        CATALOG=Collections.unmodifiableMap(rules);
    }
    static String sharedItem(String type,String key) {
        return type.equals("LOOT")&&CATALOG.containsKey(key)?key.substring(key.lastIndexOf(':')+1):null;
    }
    static boolean barrows(String type,String key){return sharedItem(type,key)!=null&&key.startsWith("chest:barrows:");}
    static int points(AutomaticRewards.Rule rule) {
        if(rule.category.equals("Pets"))return 500;
        switch(rule.key.substring(rule.key.lastIndexOf(':')+1)) {
            case "21034":return 250; // Dexterous prayer scroll
            case "21079":return 150; // Arcane prayer scroll
            case "21015":return 250; // Dinh's bulwark
            case "21012":return 350; // Dragon hunter crossbow
            case "21018":return 400; // Ancestral hat
            case "21003":return 750; // Elder maul
            case "21043":return 600; // Kodai insignia
            case "22477":return 400; // Avernic defender hilt
            case "22481":return 600; // Sanguinesti staff
            case "22326":return 300; // Justiciar faceguard
            case "22327":return 400; // Justiciar chestguard
            case "22328":return 350; // Justiciar legguards
            case "25975":return 200; // Lightbearer
            case "26219":return 250; // Osmumten's fang
            case "25985":return 250; // Elidinis' ward
            case "27226":return 400; // Masori mask
            case "27232":return 450; // Masori chaps
            default:return rule.points;
        }
    }
    static void apply(Connection c)throws SQLException {
        try(Statement s=c.createStatement()){s.execute("SELECT pg_advisory_xact_lock(72419051001)");}
        String migration="balanced-rewards-v1";
        try(PreparedStatement s=RankingStore.query(c,"SELECT 1 FROM configuration_migrations WHERE name=?",migration);ResultSet r=s.executeQuery()){if(r.next())return;}
        for(AutomaticRewards.Rule rule:AutomaticRewards.rules()) {
            RankingStore.execute(c,"UPDATE rules SET points=? WHERE type=? AND event_key=? AND points=? AND enabled",points(rule),rule.type,rule.key,rule.points);
        }
        adjust(c,"verified pet unlock",750,500);
        adjust(c,"verified rare drop",250,150);
        adjust(c,"clan event attendance",200,150);
        adjust(c,"verified diary tier",250,75);
        for(int i=0;i<4;i++) {
            String[] tiers={"easy","medium","hard","elite"};int[] rewards={75,150,250,400};
            RankingStore.execute(c,"INSERT INTO rules(type,event_key,points,daily_limit,enabled,category,display_name) VALUES ('MANUAL',?,?,1,true,'Diaries',?) ON CONFLICT(type,event_key) DO NOTHING",
                "verified "+tiers[i]+" diary tier",rewards[i],"Verified "+tiers[i]+" diary tier");
        }
        RankingStore.execute(c,"UPDATE goals SET points=50 WHERE id IN ('10000000-0000-4000-8000-000000000040','10000000-0000-4000-8000-000000000041') AND points=100 AND enabled");
        RankingStore.execute(c,"UPDATE goals SET points=100 WHERE id='10000000-0000-4000-8000-000000000042' AND points=200 AND enabled");
        // A larger earning allowance is not a lifetime balance reset. Preserve highs,
        // baseline, carry, timestamps, current-week usage, and all historic awards.
        try(Statement s=c.createStatement()) {
            s.execute("ALTER TABLE efficiency_reward_state DROP CONSTRAINT IF EXISTS efficiency_reward_state_weekly_points_check");
            s.execute("ALTER TABLE efficiency_reward_state ADD CONSTRAINT efficiency_reward_state_weekly_points_check CHECK(weekly_points BETWEEN 0 AND 250)");
        }
        RankingStore.execute(c,"INSERT INTO configuration_migrations(name) VALUES (?)",migration);
    }
    private static void adjust(Connection c,String key,int previous,int next)throws SQLException {
        RankingStore.execute(c,"UPDATE rules SET points=? WHERE type='MANUAL' AND event_key=? AND points=? AND enabled",next,key,previous);
    }
}
