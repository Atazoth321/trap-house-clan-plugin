package com.traphouse.server;

import java.sql.*;
import java.util.*;

/** Additional named drop sources. Purchases, inventory scans and player chat do not qualify. */
final class AdditionalDropRewards {
    static List<AutomaticRewards.Rule> rules() {
        List<AutomaticRewards.Rule> rules=new ArrayList<>();
        loot(rules,new int[]{9293,9258},24268,"Basilisk jaw",250);
        loot(rules,new int[]{7144,7145,7146,7147,7148,7149},19529,"Zenyte shard",250);
        loot(rules,new int[]{6766,6767,7744,7745,8565},13576,"Dragon warhammer",400);
        loot(rules,new int[]{415,416,7241,7410,11239},13265,"Abyssal dagger",200);
        loot(rules,new int[]{5886,5887,5888,5889,5890,5891,5908},13273,"Unsired",150);
        loot(rules,new int[]{7888,7889},21742,"Granite hammer",75);
        loot(rules,new int[]{8713},23517,"Giant egg sac (full)",50);
        loot(rules,new int[]{4005,7250,7409},11235,"Dark bow",100);
        loot(rules,new int[]{9756,9757,9758,9759,9760,9761,9762,9763},24777,"Blood shard",200);
        loot(rules,new int[]{9049,9050},23953,"Crystal tool seed",200);
        loot(rules,new int[]{9049,9050},6571,"Uncut onyx (Zalcano)",100);
        // Sire pays for exchanged uniques rather than the Unsired drop itself.
        rules.add(new AutomaticRewards.Rule("LOOT","chest:unsired:13265","Abyssal dagger","Drops",200,2));
        String[] parts={"Bludgeon spine","Bludgeon claw","Bludgeon axon"};
        for(int i=0;i<parts.length;i++)rules.add(new AutomaticRewards.Rule("LOOT","chest:unsired:"+(13274+i),parts[i],"Drops",200,2));
        return Collections.unmodifiableList(rules);
    }
    private static void loot(List<AutomaticRewards.Rule> rules,int[] npcs,int item,String name,int points) {
        for(int npc:npcs)rules.add(new AutomaticRewards.Rule("LOOT",npc+":"+item,name,"Drops",points,2));
    }
    static void seed(Connection c)throws SQLException {
        seedOriginal(c);
        String migration="sire-exchanged-uniques-v2";
        try(PreparedStatement s=RankingStore.query(c,"SELECT 1 FROM configuration_migrations WHERE name=?",migration);ResultSet r=s.executeQuery()){if(r.next())return;}
        for(AutomaticRewards.Rule rule:rules()) {
            if(rule.key.endsWith(":13273"))RankingStore.execute(c,"UPDATE rules SET enabled=false WHERE type='LOOT' AND event_key=?",rule.key);
            if(rule.key.startsWith("chest:unsired:"))RankingStore.execute(c,"UPDATE rules SET enabled=true,points=200 WHERE type='LOOT' AND event_key=?",rule.key);
        }
        RankingStore.execute(c,"INSERT INTO configuration_migrations(name) VALUES (?)",migration);
    }
    private static void seedOriginal(Connection c)throws SQLException {
        try(Statement s=c.createStatement()){s.execute("SELECT pg_advisory_xact_lock(72419051001)");}
        String migration="additional-drop-rewards-v1";
        try(PreparedStatement s=RankingStore.query(c,"SELECT 1 FROM configuration_migrations WHERE name=?",migration);ResultSet r=s.executeQuery()){if(r.next())return;}
        for(AutomaticRewards.Rule rule:rules())RankingStore.execute(c,
            "INSERT INTO rules(type,event_key,points,daily_limit,enabled,category,display_name) VALUES (?,?,?,?,?,?,?) ON CONFLICT(type,event_key) DO NOTHING",
            rule.type,rule.key,rule.points,rule.limit,!rule.key.startsWith("chest:unsired:"),rule.category,rule.name);
        RankingStore.execute(c,"INSERT INTO configuration_migrations(name) VALUES (?)",migration);
    }
}
