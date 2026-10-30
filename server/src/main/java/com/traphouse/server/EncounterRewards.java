package com.traphouse.server;

import java.util.*;

/** Additive encounter rewards; no inventory ownership or player chat observations. */
final class EncounterRewards {
    static List<AutomaticRewards.Rule> rules() {
        List<AutomaticRewards.Rule> rules=new ArrayList<>();
        for(int npc:new int[]{9021,9022,9023,9024,9035,9036,9037,9038}) {
            loot(rules,npc,23956,"Crystal armour seed",100);
            loot(rules,npc,25859,"Enhanced crystal weapon seed",750);
        }
        int[] moons={28988,28997,29000,29004,29007,29010,29013,29016,29019,29022,29025,29028};
        String[] names={"Blue moon spear","Dual macuahuitl","Eclipse atlatl","Eclipse moon chestplate","Eclipse moon tassets","Eclipse moon helm","Blue moon chestplate","Blue moon tassets","Blue moon helm","Blood moon chestplate","Blood moon tassets","Blood moon helm"};
        for(int i=0;i<moons.length;i++)rules.add(new AutomaticRewards.Rule("LOOT","chest:lunar chest:"+moons[i],names[i],"Drops",75,2));
        for(int npc=13599;npc<=13606;npc++) {
            loot(rules,npc,29574,"Burning claw",200);
            loot(rules,npc,29580,"Tormented synapse",350);
        }
        for(int npc:new int[]{13668,13669}) {
            loot(rules,npc,29799,"Araxyte fang",500);
            loot(rules,npc,29790,"Noxious point",100);
            loot(rules,npc,29792,"Noxious blade",100);
            loot(rules,npc,29794,"Noxious pommel",100);
        }
        String[] clues={"beginner","easy","medium","hard","elite","master"};
        int[] points={25,50,75,100,150,200};
        for(int i=0;i<clues.length;i++)rules.add(new AutomaticRewards.Rule("COLLECTION","clue completion:"+clues[i],"First "+clues[i]+" clue completion","Clues",points[i],1));
        return Collections.unmodifiableList(rules);
    }
    private static void loot(List<AutomaticRewards.Rule> rules,int npc,int item,String name,int points) {
        rules.add(new AutomaticRewards.Rule("LOOT",npc+":"+item,name,"Drops",points,2));
    }
    static int clueItem(String type,String key) {
        if(type.equals("LOOT")&&key.matches("clue:(beginner|easy|medium|hard|elite|master):[0-9]{1,6}"))return Integer.parseInt(key.substring(key.lastIndexOf(':')+1));
        if(!type.equals("COLLECTION"))return -1;
        if(key.matches("clue milestone:(beginner|easy|medium|hard|elite|master):(10|50|100)"))key="clue completion:"+key.split(":")[1];
        switch(key) {
            case "clue completion:beginner":return 23245;
            case "clue completion:easy":return 20546;
            case "clue completion:medium":return 20545;
            case "clue completion:hard":return 20544;
            case "clue completion:elite":return 20543;
            case "clue completion:master":return 19836;
            default:return -1;
        }
    }
}
