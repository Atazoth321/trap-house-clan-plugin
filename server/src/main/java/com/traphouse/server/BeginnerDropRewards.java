package com.traphouse.server;

import java.util.*;

/** Accessible combat and skilling rewards, matched to actual loot rather than ownership. */
final class BeginnerDropRewards {
    static List<AutomaticRewards.Rule> rules() {
        List<AutomaticRewards.Rule> rules=new ArrayList<>();
        loot(rules,new int[]{7221,7222},28798,"Scurrius' spine",50);
        loot(rules,new int[]{7416},20756,"Hill giant club",100);
        loot(rules,new int[]{8195},22372,"Bryophyta's essence",150);
        loot(rules,new int[]{12720,12721,12722,12723,12724,12725,12726,12727,12728,12729},28813,"Broken zombie axe",150);
        loot(rules,new int[]{12490,12491,12492,12493,12494,12495,12496,12497,12498},28583,"Warped sceptre (uncharged)",100);
        loot(rules,new int[]{4501},11037,"Brine sabre",75);
        loot(rules,new int[]{410,411,7405},20727,"Leaf-bladed battleaxe",100);
        int[] wyrms={8610,8611,10398,10399};
        loot(rules,wyrms,21009,"Dragon sword",150);
        loot(rules,wyrms,21028,"Dragon harpoon",200);
        int[] drakes={8612,8613,10400,10401};
        loot(rules,drakes,22957,"Drake's claw",150);
        loot(rules,drakes,22960,"Drake's tooth",150);
        loot(rules,new int[]{8583},22994,"Bottomless compost bucket",150);
        loot(rules,new int[]{7286},21275,"Dark claw",100);
        int[] wild={6619,6618,6615};
        for(int i=0;i<wild.length;i++) {
            loot(rules,new int[]{wild[i]},11928+i,"Odium shard "+(i+1),100);
            loot(rules,new int[]{wild[i]},11931+i,"Malediction shard "+(i+1),100);
        }
        chest(rules,"wintertodt",20716,"Tome of fire (empty)",150);
        chest(rules,"tempoross",25576,"Tome of water (empty)",150);
        chest(rules,"tempoross",25582,"Fish barrel",100);
        chest(rules,"tempoross",25580,"Tackle box",75);
        chest(rules,"guardians of the rift",26813,"Abyssal needle",150);
        chest(rules,"guardians of the rift",26822,"Abyssal lantern",200);
        return Collections.unmodifiableList(rules);
    }
    private static void loot(List<AutomaticRewards.Rule> rules,int[] npcs,int item,String name,int points) {
        for(int npc:npcs)rules.add(new AutomaticRewards.Rule("LOOT",npc+":"+item,name,"Drops",points,2));
    }
    private static void chest(List<AutomaticRewards.Rule> rules,String source,int item,String name,int points) {
        rules.add(new AutomaticRewards.Rule("LOOT","chest:"+source+":"+item,name,"Drops",points,2));
    }
}
