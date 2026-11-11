package com.traphouse.server;

import java.util.*;
import java.sql.*;

/** Small personal-loot rewards, once per item per UTC day across supported sources. */
final class AccessibleDropRewards {
    static List<AutomaticRewards.Rule> rules() {
        List<AutomaticRewards.Rule> rules=new ArrayList<>();
        loot(rules,new int[]{2212,2244,3161,3168,11292},11840,"Dragon boots",10);
        int[] dragons={270,271,272,273,139,274,275,7253,7254,7255,8080,8086};
        loot(rules,dragons,4087,"Dragon platelegs",15);
        loot(rules,dragons,4585,"Dragon plateskirt",15);
        loot(rules,dragons,1149,"Dragon med helm",10);
        loot(rules,new int[]{412,413,1543},4153,"Granite maul",10);
        loot(rules,new int[]{2075,2076,2077,2078,2079,2080,2081,2082,2083,2084,7251,7252},1333,"Rune scimitar",2);
        loot(rules,new int[]{1047,1048,1049,1050,1051},8901,"Black mask (10)",20);
        loot(rules,new int[]{410,411,426,427,428,429,430,431,432},11902,"Leaf-bladed sword",10);
        loot(rules,new int[]{465,466,467,468},6809,"Granite legs",20);
        loot(rules,new int[]{7792,7793,7794,7795},21643,"Granite boots",20);
        int[] mages={443,444,445,446,447};
        loot(rules,mages,4099,"Mystic hat (dark)",5);
        loot(rules,new int[]{2,3,4,5,6,7},4101,"Mystic robe top (dark)",10);
        loot(rules,new int[]{8,11},4103,"Mystic robe bottom (dark)",10);
        loot(rules,new int[]{448,449,450,451,452,453,454,455,456,457},4105,"Mystic gloves (dark)",5);
        loot(rules,mages,4107,"Mystic boots (dark)",5);
        loot(rules,new int[]{414},4109,"Mystic hat (light)",5);
        loot(rules,new int[]{410,411},4111,"Mystic robe top (light)",10);
        loot(rules,new int[]{426,427,428,429,430,431,432},4113,"Mystic robe bottom (light)",10);
        loot(rules,new int[]{419,420},4115,"Mystic gloves (light)",5);
        loot(rules,new int[]{417,418,9283,9284,9285,9286},4117,"Mystic boots (light)",5);
        return Collections.unmodifiableList(rules);
    }
    private static void loot(List<AutomaticRewards.Rule> rules,int[] npcs,int item,String name,int points) {
        for(int npc:npcs)rules.add(new AutomaticRewards.Rule("LOOT",npc+":"+item,name,"Drops",points,1));
    }
    static void balance(Connection c)throws SQLException {
        String migration="accessible-drop-balance-v2";
        try(PreparedStatement s=RankingStore.query(c,"SELECT 1 FROM configuration_migrations WHERE name=?",migration);ResultSet r=s.executeQuery()){if(r.next())return;}
        for(AutomaticRewards.Rule rule:rules()) {
            int previous=rule.points==20?10:rule.points==15?10:rule.points==10?5:rule.points==5?3:2;
            RankingStore.execute(c,"UPDATE rules SET points=? WHERE type='LOOT' AND event_key=? AND points=? AND daily_limit=1 AND category='Drops' AND display_name=? AND enabled",rule.points,rule.key,previous,rule.name);
        }
        RankingStore.execute(c,"INSERT INTO configuration_migrations(name) VALUES (?)",migration);
    }
}


