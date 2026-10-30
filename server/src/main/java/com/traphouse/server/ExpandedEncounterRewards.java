package com.traphouse.server;

import java.util.*;

/** Later additive catalog, separately versioned from the initial encounter rewards. */
final class ExpandedEncounterRewards {
    static List<AutomaticRewards.Rule> rules() {
        List<AutomaticRewards.Rule> rules=new ArrayList<>();
        for(int npc:new int[]{12596,14147,14148,14149}) {
            loot(rules,npc,30626,"Deadeye prayer scroll",100);
            loot(rules,npc,30627,"Mystic vigour prayer scroll",100);
        }
        for(int npc:new int[]{14009,14010,14011,14012,14013}) {
            loot(rules,npc,30085,"Hueycoatl hide",100);
            loot(rules,npc,30070,"Dragon hunter wand",500);
        }
        int[] nightmare={377,378,9416,9417,9418,9419,9420,9421,9422,9423,9424,9425,9426,9427,9428,9429,9430,9431,9432,9433,9460,9461,9462,9463,9464,11153,11154,11155};
        for(int npc:nightmare) {
            loot(rules,npc,24419,"Inquisitor's great helm",500);
            loot(rules,npc,24420,"Inquisitor's hauberk",500);
            loot(rules,npc,24421,"Inquisitor's plateskirt",500);
            loot(rules,npc,24417,"Inquisitor's mace",750);
            loot(rules,npc,24511,"Harmonised orb",750);
            loot(rules,npc,24514,"Volatile orb",750);
            loot(rules,npc,24517,"Eldritch orb",750);
        }
        clue(rules,"medium",2577,"Ranger boots",300);
        int[] thirdAge={10330,10332,10334,10336,10338,10340,10342,10344,10346,10348,10350,10352,12422,12424,12426,12437,20011,20014,23242,23336,23339,23342,23345};
        String[] thirdNames={"3rd age range top","3rd age range legs","3rd age range coif","3rd age vambraces","3rd age robe top","3rd age robe","3rd age mage hat","3rd age amulet","3rd age platelegs","3rd age platebody","3rd age full helmet","3rd age kiteshield","3rd age wand","3rd age bow","3rd age longsword","3rd age cloak","3rd age axe","3rd age pickaxe","3rd age plateskirt","3rd age druidic robe top","3rd age druidic robe bottoms","3rd age druidic staff","3rd age druidic cloak"};
        for(String tier:new String[]{"hard","elite","master"})for(int i=0;i<thirdAge.length;i++) {
            boolean armour=i<12||thirdAge[i]==23242;
            if(tier.equals("hard")&&!armour)continue;
            if(tier.equals("elite")&&(thirdAge[i]==20011||thirdAge[i]==20014||i>=19))continue;
            clue(rules,tier,thirdAge[i],thirdNames[i],1000);
        }
        int[] gilded={3481,3483,3485,3486,3488,12389,12391,20146,20149,20152,20155,20158,20161,23258,23261,23264,23267,23276,23279,23282};
        String[] gildedNames={"Gilded platebody","Gilded platelegs","Gilded plateskirt","Gilded full helm","Gilded kiteshield","Gilded scimitar","Gilded boots","Gilded med helm","Gilded chainbody","Gilded sq shield","Gilded 2h sword","Gilded spear","Gilded hasta","Gilded coif","Gilded d'hide vambraces","Gilded d'hide body","Gilded d'hide chaps","Gilded pickaxe","Gilded axe","Gilded spade"};
        for(String tier:new String[]{"hard","elite"})for(int i=0;i<gilded.length;i++) {
            if(tier.equals("hard")&&i>=5)continue;
            clue(rules,tier,gilded[i],gildedNames[i],250);
        }
        String[] tiers={"beginner","easy","medium","hard","elite","master"};int[] base={25,50,75,100,150,200};int[] targets={10,50,100};
        for(int i=0;i<tiers.length;i++)for(int j=0;j<targets.length;j++)
            rules.add(new AutomaticRewards.Rule("COLLECTION","clue milestone:"+tiers[i]+":"+targets[j],targets[j]+" "+tiers[i]+" clue completions","Clues",base[i]*(j+1),1));
        return Collections.unmodifiableList(rules);
    }
    private static void loot(List<AutomaticRewards.Rule> rules,int npc,int item,String name,int points) {
        rules.add(new AutomaticRewards.Rule("LOOT",npc+":"+item,name,"Drops",points,2));
    }
    private static void clue(List<AutomaticRewards.Rule> rules,String tier,int item,String name,int points) {
        rules.add(new AutomaticRewards.Rule("LOOT","clue:"+tier+":"+item,name,"Clues",points,2));
    }
}
