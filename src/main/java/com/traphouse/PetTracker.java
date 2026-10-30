package com.traphouse;

import java.util.*;
import java.util.function.Predicate;
import net.runelite.api.*;

/** Existing untradeable pets in visible inventory/loaded bank containers, read on the game thread. */
final class PetTracker {
    private static final Map<Integer,String> PETS;
    private static final Map<String,Integer> SPRITES;
    static {
        Map<Integer,String> pets=new HashMap<>();Map<String,Integer> sprites=new HashMap<>();
        pet(pets,sprites,"Abyssal orphan",13262);
        pet(pets,sprites,"Baby chinchompa",13323,13324,13325,13326);
        pet(pets,sprites,"Baby mole",12646,25613);
        pet(pets,sprites,"Beaver",13322,28229,28230,28231,28232,28233,28234,28235,28236,28237,28669,28670,31279,31280,31281,31282);
        pet(pets,sprites,"Bloodhound",19730);
        pet(pets,sprites,"Callisto cub",13178);
        pet(pets,sprites,"Chompy chick",13071);
        pet(pets,sprites,"Giant squirrel",20659,24701,30151);
        pet(pets,sprites,"Hellpuppy",13247);
        pet(pets,sprites,"Herbi",21509);
        pet(pets,sprites,"Heron",13320,25600);
        pet(pets,sprites,"Ikkle hydra",22746,22748,22750,22752);
        pet(pets,sprites,"Jal-nib-rek",21291,22319);
        pet(pets,sprites,"Kalphite princess",12647,12654);
        pet(pets,sprites,"Noon",21748,21750);
        pet(pets,sprites,"Olmlet",20851,22376,22378,22380,22382,22384,24656,24658);
        pet(pets,sprites,"Pet chaos elemental",11995);
        pet(pets,sprites,"Pet dagannoth prime",12644);
        pet(pets,sprites,"Pet dagannoth rex",12645);
        pet(pets,sprites,"Pet dagannoth supreme",12643);
        pet(pets,sprites,"Pet dark core",12816,22318);
        pet(pets,sprites,"Pet general graardor",12650);
        pet(pets,sprites,"Pet k'ril tsutsaroth",12652);
        pet(pets,sprites,"Pet kraken",12655);
        pet(pets,sprites,"Pet kree'arra",12649);
        pet(pets,sprites,"Pet penance queen",12703);
        pet(pets,sprites,"Pet smoke devil",12648,22663);
        pet(pets,sprites,"Pet snakeling",12921,12939,12940);
        pet(pets,sprites,"Pet zilyana",12651);
        pet(pets,sprites,"Phoenix",20693,24483,24484,24485,24486);
        pet(pets,sprites,"Prince black dragon",12653);
        pet(pets,sprites,"Rift guardian",20665,20667,20669,20671,20673,20675,20677,20679,20681,20683,20685,20687,20689,20691,21990,26899);
        pet(pets,sprites,"Rock golem",13321,21187,21188,21189,21190,21191,21192,21193,21194,21195,21196,21197,21340,21358,21359,21360,31276,31277,31278);
        pet(pets,sprites,"Rocky",20663,24847,24849);
        pet(pets,sprites,"Scorpia's offspring",13181);
        pet(pets,sprites,"Skotos",21273);
        pet(pets,sprites,"Smolcano",23760);
        pet(pets,sprites,"Sraracha",23495,25842,25843);
        pet(pets,sprites,"Tangleroot",20661,24555,24557,24559,24561,24563);
        pet(pets,sprites,"Tzrek-jad",13225);
        pet(pets,sprites,"Venenatis spiderling",13177);
        pet(pets,sprites,"Vet'ion jr.",13179,13180,27650,27651);
        pet(pets,sprites,"Vorki",21992);
        pet(pets,sprites,"Youngllef",23757,23759);
        pet(pets,sprites,"Lil' zik",22473,25748,25749,25750,25751,25752);
        pet(pets,sprites,"Little nightmare",24491,25836);
        pet(pets,sprites,"Lil' creator",25348);
        pet(pets,sprites,"Tiny tempor",25602);
        pet(pets,sprites,"Abyssal protector",26901);
        pet(pets,sprites,"Tumeken's guardian",27352,27354,27382,27383,27384,27385,27386,27387);
        pet(pets,sprites,"Muphin",27590,27592,27593);
        pet(pets,sprites,"Butch",28248);
        pet(pets,sprites,"Baron",28250);
        pet(pets,sprites,"Lil'viathan",28252);
        pet(pets,sprites,"Wisp",28246);
        PETS=Collections.unmodifiableMap(pets);SPRITES=Collections.unmodifiableMap(sprites);
    }
    private static void pet(Map<Integer,String> pets,Map<String,Integer> sprites,String name,int... ids) {
        String key=RankingTracker.normalize(name);sprites.put(key,ids[0]);
        for(int id:ids)if(pets.put(id,key)!=null)throw new IllegalStateException("Duplicate pet item ID");
    }
    static String key(int itemId){return PETS.get(itemId);}
    static int itemId(String key){return SPRITES.getOrDefault(key,-1);}
    private String player;
    private final Set<String> submitted=new HashSet<>();
    synchronized void reset(){player=null;submitted.clear();}
    synchronized void scan(Client client,String character,Predicate<RankingEvent> enqueue) {
        String current=RankingTracker.normalize(character);
        if(!current.equals(player)){submitted.clear();player=current;}
        for(InventoryID location:new InventoryID[]{InventoryID.INVENTORY,InventoryID.BANK}) {
            ItemContainer container=client.getItemContainer(location);
            if(container==null)continue; // Bank is unavailable until loaded by opening it.
            for(Item item:container.getItems()) {
                if(item==null||item.getQuantity()<=0)continue;
                String key=key(item.getId());
                if(key!=null&&!submitted.contains(key)&&enqueue.test(new RankingEvent(character,"COLLECTION",key)))submitted.add(key);
            }
        }
    }
}
