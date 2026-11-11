package com.traphouse.server;

import java.util.*;

/** Exact revenant loot and lower-tier Treasure Trail rewards. */
final class WildernessAndClueRewards {
    static List<AutomaticRewards.Rule> rules() {
        List<AutomaticRewards.Rule> rules=new ArrayList<>();
        // Ordinary Revenant Cave encounters, excluding special and quest copies.
        int[] revenants={7881,7931,7932,7933,7934,7935,7936,7937,7938,7939,7940};
        int[] items={22542,22547,22552,22557,21804,21807,21810,21813,22299,22302,22305,21817};
        String[] names={"Viggora's chainmace (u)","Craw's bow (u)","Thammaron's sceptre (u)","Amulet of avarice",
            "Ancient crystal","Ancient emblem","Ancient totem","Ancient statuette","Ancient medallion","Ancient effigy","Ancient relic","Bracelet of ethereum (uncharged)"};
        int[] points={300,350,250,100,50,25,40,75,100,150,250,5};
        for(int i=0;i<items.length;i++)for(int npc:revenants)
            rules.add(new AutomaticRewards.Rule("LOOT",npc+":"+items[i],names[i],"Drops",points[i],items[i]==21817?1:2));

        int[] beginner={23285,23288,23291,23294,23297,23300,23303,23306,23312,23315,23318};
        String[] beginnerNames={"Mole slippers","Frog slippers","Bear feet","Demon feet","Jester cape","Shoulder parrot",
            "Monk's robe top (t)","Monk's robe (t)","Sandwich lady hat","Sandwich lady top","Sandwich lady bottom"};
        for(int i=0;i<beginner.length;i++)clue(rules,"beginner",beginner[i],beginnerNames[i],10);
        int[] easy={10394,10396,10398,12297,23360,20211,20214,20217};
        String[] easyNames={"Flared trousers","Pantaloons","Sleeping cap","Black pickaxe","Ham joint","Team cape zero","Team cape X","Team cape i"};
        int[] easyPoints={50,25,25,10,75,25,25,25};
        for(int i=0;i<easy.length;i++)clue(rules,"easy",easy[i],easyNames[i],easyPoints[i]);

        String[] gods={"Saradomin","Zamorak","Guthix","Bandos","Armadyl","Ancient"};
        int[] firstPages={3827,3831,3835,12613,12617,12621};
        for(String tier:new String[]{"easy","medium","hard"})for(int god=0;god<gods.length;god++)for(int page=0;page<4;page++)
            clue(rules,tier,firstPages[god]+page,gods[god]+" page "+(page+1),10);
        return Collections.unmodifiableList(rules);
    }
    private static void clue(List<AutomaticRewards.Rule> rules,String tier,int item,String name,int points) {
        rules.add(new AutomaticRewards.Rule("LOOT","clue:"+tier+":"+item,name,"Clues",points,1));
    }
}
