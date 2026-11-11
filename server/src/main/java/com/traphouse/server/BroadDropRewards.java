package com.traphouse.server;

import java.util.*;

/** Additional exact personal boss drops and clue reward-container items. */
final class BroadDropRewards {
    static List<AutomaticRewards.Rule> rules() {
        List<AutomaticRewards.Rule> rules=new ArrayList<>();
        loot(rules,new int[]{2265},6724,"Seercull",50);
        loot(rules,new int[]{2266},6562,"Mud battlestaff",50);
        loot(rules,new int[]{3129},11787,"Steam battlestaff",50);
        loot(rules,new int[]{499},11998,"Smoke battlestaff",75);
        loot(rules,new int[]{7792,7793,7794,7795},21637,"Wyvern visage",350);
        int[] hydra={8615,8616,8617,8618,8619,8620,8621,8622};
        loot(rules,hydra,22969,"Hydra's heart",50);
        loot(rules,hydra,22971,"Hydra's fang",50);
        loot(rules,hydra,22973,"Hydra's eye",50);

        String[] gods={"Zamorak","Guthix","Saradomin","Ancient","Bandos","Armadyl"};
        int[][] armour={{10368,10370,10372,10374},{10376,10378,10380,10382},{10384,10386,10388,10390},
            {12490,12492,12494,12496},{12498,12500,12502,12504},{12506,12508,12510,12512}};
        String[] parts={"bracers","d'hide body","chaps","coif"};
        int[] boots={19936,19927,19933,19921,19924,19930};
        int[] shields={23194,23188,23191,23197,23203,23200};
        for(int i=0;i<gods.length;i++) {
            for(int j=0;j<parts.length;j++)clue(rules,"hard",armour[i][j],gods[i]+" "+parts[j],j==1||j==2?25:15);
            clue(rules,"hard",boots[i],gods[i]+" d'hide boots",35);
            clue(rules,"hard",shields[i],gods[i]+" d'hide shield",35);
        }
        clue(rules,"medium",2579,"Wizard boots",50);
        clue(rules,"medium",12598,"Holy sandals",100);
        clue(rules,"medium",23389,"Spiked manacles",100);
        clue(rules,"hard",2581,"Robin hood hat",100);
        clue(rules,"elite",12596,"Rangers' tunic",100);
        clue(rules,"elite",23249,"Rangers' tights",100);
        clue(rules,"elite",19997,"Holy wraps",100);
        clue(rules,"elite",23246,"Fremennik kilt",75);
        return Collections.unmodifiableList(rules);
    }
    private static void loot(List<AutomaticRewards.Rule> rules,int[] npcs,int item,String name,int points) {
        for(int npc:npcs)rules.add(new AutomaticRewards.Rule("LOOT",npc+":"+item,name,"Drops",points,2));
    }
    private static void clue(List<AutomaticRewards.Rule> rules,String tier,int item,String name,int points) {
        rules.add(new AutomaticRewards.Rule("LOOT","clue:"+tier+":"+item,name,"Clues",points,1));
    }
}
