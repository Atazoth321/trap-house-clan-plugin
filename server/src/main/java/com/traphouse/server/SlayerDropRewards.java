package com.traphouse.server;

import java.util.*;

/** Additional Slayer loot sources; existing whip rewards retain their price and cap. */
final class SlayerDropRewards {
    static List<AutomaticRewards.Rule> rules() {
        List<AutomaticRewards.Rule> rules=new ArrayList<>();
        loot(rules,new int[]{7410,11239},4151,"Abyssal whip",100,2);
        loot(rules,new int[]{448,449,450,451,452,453,454,455,456,457},7975,"Crawling hand",10,1);
        loot(rules,new int[]{419,420},7976,"Cockatrice head",15,1);
        loot(rules,new int[]{417,418,9283,9284,9285,9286},7977,"Basilisk head",15,1);
        loot(rules,new int[]{410,411},7978,"Kurask head",25,1);
        loot(rules,new int[]{415,416,7241,7410,11239},7979,"Abyssal head",50,1);
        loot(rules,new int[]{423,7249,7404,11238},20736,"Dust battlestaff",25,1);
        loot(rules,new int[]{465,466,467,468},11286,"Draconic visage",300,2);
        return Collections.unmodifiableList(rules);
    }
    private static void loot(List<AutomaticRewards.Rule> rules,int[] npcs,int item,String name,int points,int limit) {
        for(int npc:npcs)rules.add(new AutomaticRewards.Rule("LOOT",npc+":"+item,name,"Drops",points,limit));
    }
}
