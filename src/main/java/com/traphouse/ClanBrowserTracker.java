package com.traphouse;

import com.google.gson.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import net.runelite.api.*;
import net.runelite.api.events.ScriptPreFired;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.gameval.VarbitID;

/** Passive cache and collection-page observations. Never opens interfaces or runs game scripts. */
final class ClanBrowserTracker {
    private final Map<String,JsonObject> pages=new LinkedHashMap<>();
    private final Map<Integer,Integer> quantities=new HashMap<>();
    private final Map<String,Integer> pendingDrops=new HashMap<>();
    private final Map<String,Long> drops=new HashMap<>();
    private final List<int[]> pendingPages=new ArrayList<>();
    private int pageCursor,sendCursor;
    private boolean catalogueLoaded;
    private static final Map<String,Integer> COUNTERS=new LinkedHashMap<>();
    static {
        String[] mapping={"abyssal_sire=1526","alchemical_hydra=2074","amoxliatl=4403","araxxor=4260","barrows_chests=1502","bryophyta=1733","callisto_and_artio=1510","venenatis_and_spindel=1511","vetion_and_calvarion=1512","cerberus=1525","chaos_elemental=1513","chaos_fanatic=1519","commander_zilyana=1505","corporeal_beast=1517","crazy_archaeologist=1521","dagannoth_kings=1507","deranged_archaeologist=1661","doom_of_mokhaiotl=4182","duke_sucellus=3967","general_graardor=1504","giant_mole=1515","grotesque_guardians=1690","hespori=2075","the_gauntlet=2383","the_corrupted_gauntlet=2384","king_black_dragon=1514","kraken=1523","kreearra=1503","kril_tsutsaroth=1506","the_leviathan=3968","moons_of_peril=4186","mimic=2221","nex=3269","the_nightmare=2664","phosanis_nightmare=2671","obor=1529","phantom_muspah=3752","royal_titans=4648","sarachnis=2233","scorpia=1520","scurrius=4079","skotizo=1527","tempoross=2934","thermonuclear_smoke_devil=1524","vardorvis=3970","vorkath=1691","the_whisperer=3969","wintertodt=1528","yama=4701","zalcano=2352","zulrah=1518","chambers_of_xeric=1532","theatre_of_blood=1748","tombs_of_amascut=3646","barbarian_assault=1605","guardians_of_the_rift=3397","hallowed_sepulchre=2936","last_man_standing=2396","mastering_mixology=4480","soul_wars=2871","gloughs_experiments=1685","tormented_demons=4240"};
        for(String pair:mapping){String[] parts=pair.split("=");COUNTERS.put(parts[0],Integer.parseInt(parts[1]));}
        COUNTERS.put("grotesque_guardians",1669);COUNTERS.put("the_gauntlet",2353);COUNTERS.put("the_corrupted_gauntlet",2354);
        COUNTERS.put("the_fight_caves",1522);COUNTERS.put("the_inferno",1585);COUNTERS.put("kalphite_queen",1516);COUNTERS.put("the_hueycoatl",4404);
        optionalCounter("fortis_colosseum","TOTAL_SOL_KILLS");optionalCounter("maggot_king","TOTAL_MAGGOT_KING_KILLS");optionalCounter("shellbane_gryphon","TOTAL_GRYPHON_BOSS_KILLS");optionalCounter("the_fractured_archive","TOTAL_COMPLETED_THEFRACTUREDARCHIVE");
    }
    private static void optionalCounter(String page,String field){try{COUNTERS.put(page,VarPlayerID.class.getField(field).getInt(null));}catch(ReflectiveOperationException unavailable){/* Available when this game cache/API includes the encounter. */}}
    synchronized void reset(){pages.clear();quantities.clear();pendingDrops.clear();drops.clear();pendingPages.clear();pageCursor=sendCursor=0;catalogueLoaded=false;}
    static String key(String label){return label.toLowerCase(Locale.ROOT).replaceAll("['()]","").replaceAll("[^a-z0-9]+","_").replaceAll("^_|_$","");}
    synchronized void item(Client client,ScriptPreFired event) {
        if(event.getScriptId()!=4100||client.getVarbitValue(VarbitID.COLLECTION_POH_HOST_BOOK_OPEN)!=0)return;
        Object[] args=event.getScriptEvent().getArguments();
        if(args==null||args.length<3||!(args[1] instanceof Integer)||!(args[2] instanceof Integer))return;
        int id=(Integer)args[1],count=(Integer)args[2];if(id<=0||count<0)return;
        int mapped=client.getEnum(3721).getIntValue(id);quantities.merge(mapped>0?mapped:id,count,Math::max);
    }
    synchronized void loot(Client client,String source,Iterable<net.runelite.client.game.ItemStack> items) {
        if(source==null)return;String sourceKey=key(source);
        if(sourceKey.startsWith("clue_scroll_"))sourceKey=sourceKey.substring("clue_scroll_".length())+"_treasure_trails";
        if(sourceKey.equals("barrows"))sourceKey="barrows_chests";if(sourceKey.equals("lunar_chest"))sourceKey="moons_of_peril";
        switch(sourceKey){case "dagannoth_prime":case "dagannoth_rex":case "dagannoth_supreme":sourceKey="dagannoth_kings";break;case "callisto":case "artio":sourceKey="callisto_and_artio";break;case "venenatis":case "spindel":sourceKey="venenatis_and_spindel";break;case "vetion":case "calvarion":sourceKey="vetion_and_calvarion";break;case "demonic_gorilla":case "tortured_gorilla":sourceKey="gloughs_experiments";break;case "dusk":case "dawn":sourceKey="grotesque_guardians";break;case "tzkal_zuk":sourceKey="the_inferno";break;case "tztok_jad":sourceKey="the_fight_caves";break;default:break;}
        Set<Integer> dropped=new HashSet<>();for(net.runelite.client.game.ItemStack item:items){int mapped=client.getEnum(3721).getIntValue(item.getId());dropped.add(mapped>0?mapped:item.getId());}
        for(Map.Entry<String,JsonObject> entry:pages.entrySet()) {
            if(!entry.getKey().equals(sourceKey)&&!(sourceKey.contains("_treasure_trails")&&entry.getKey().startsWith(sourceKey+"_")))continue;
            for(JsonElement item:entry.getValue().getAsJsonArray("items"))if(dropped.contains(item.getAsJsonObject().get("id").getAsInt())){pendingDrops.put(entry.getKey(),client.getTickCount()+2);break;}
        }
    }
    synchronized void tick(Client client) {
        try {
            if(!catalogueLoaded) {
                List<int[]> definitions=new ArrayList<>();for(int tab:client.getEnum(2102).getIntVals())for(int page:client.getEnum(client.getStructComposition(tab).getIntValue(683)).getIntVals())if(definitions.size()<250)definitions.add(new int[]{tab,page});
                pendingPages.clear();pendingPages.addAll(definitions);
                catalogueLoaded=!pendingPages.isEmpty();
            }
            for(int chunk=0;chunk<4&&pageCursor<pendingPages.size();chunk++,pageCursor++) {
                int[] ids=pendingPages.get(pageCursor);StructComposition definition=client.getStructComposition(ids[1]);
                String label=definition.getStringValue(689);if(label==null||label.isBlank())continue;
                JsonObject page=new JsonObject();page.addProperty("key",key(label));page.addProperty("label",label);
                page.addProperty("section",ids[0]==471?"Bosses":ids[0]==472?"Raids":ids[0]==473?"Clues":ids[0]==474?"Minigames":"Other");
                Set<Integer> unique=new LinkedHashSet<>();for(int item:client.getEnum(definition.getIntValue(690)).getIntVals()){int mapped=client.getEnum(3721).getIntValue(item);if(item>0)unique.add(mapped>0?mapped:item);}
                if(unique.isEmpty()||unique.size()>500)continue;JsonArray items=new JsonArray();for(int id:unique){JsonObject item=new JsonObject();item.addProperty("id",id);items.add(item);}page.add("items",items);pages.put(key(label),page);
            }
            Iterator<Map.Entry<String,Integer>> pending=pendingDrops.entrySet().iterator();
            while(pending.hasNext()) {
                Map.Entry<String,Integer> drop=pending.next();if(client.getTickCount()<drop.getValue())continue;
                Long counter=counter(client,drop.getKey());if(counter!=null)drops.put(drop.getKey(),counter);
                pending.remove();
            }
        }catch(RuntimeException unavailable){/* Cache data may be unavailable during loading. Retry next tick. */}
    }
    private Long counter(Client client,String page) {
        String[] tiers={"beginner","easy","medium","hard","elite","master"};
        for(int i=0;i<tiers.length;i++)if(page.startsWith(tiers[i]+"_treasure_trails"))return (long)client.getVarbitValue(11996+i);
        Integer id=COUNTERS.get(page);if(id==null)return null;
        long value=client.getVarpValue(id);
        if(page.equals("dagannoth_kings"))value+=client.getVarpValue(1508)+client.getVarpValue(1509);
        if(page.equals("venenatis_and_spindel"))value+=client.getVarpValue(3762);
        if(page.equals("the_gauntlet"))value+=client.getVarpValue(2354);
        if(page.equals("chambers_of_xeric"))value+=client.getVarpValue(1735);
        if(page.equals("theatre_of_blood"))value+=client.getVarpValue(3057);
        if(page.equals("tombs_of_amascut"))value+=client.getVarpValue(3645)+client.getVarpValue(3647);
        return value>=0?value:null;
    }
    synchronized JsonObject collect(Client client,String player) {
        JsonObject body=new JsonObject(),stats=new JsonObject();body.addProperty("player",player);
        if(client.getAccountType()!=null)stats.addProperty("accountType",client.getAccountType().name());
        stats.addProperty("xp",client.getOverallExperience());stats.addProperty("combat",client.getVarbitValue(VarbitID.CA_POINTS));
        if(client.getVarpValue(VarPlayerID.COLLECTION_COUNT_MAX)>0)stats.addProperty("clog",client.getVarpValue(VarPlayerID.COLLECTION_COUNT));
        JsonObject clues=new JsonObject();String[] tiers={"beginner","easy","medium","hard","elite","master"};for(int i=0;i<tiers.length;i++)clues.addProperty(tiers[i],client.getVarbitValue(11996+i));stats.add("clues",clues);
        // Boss totals come from public hiscores; combined collection counters are used only for their matching log pages.
        for(JsonObject page:pages.values())if(page.get("key").getAsString().equals("all_pets")) {
            boolean known=true;int count=0;for(JsonElement item:page.getAsJsonArray("items")){Integer quantity=quantities.get(item.getAsJsonObject().get("id").getAsInt());if(quantity==null)known=false;else if(quantity>0)count++;}if(known)stats.addProperty("pets",count);
        }
        body.add("stats",stats);JsonArray batch=new JsonArray();body.add("pages",batch);
        List<JsonObject> definitions=new ArrayList<>(pages.values());
        for(int count=0;count<Math.min(5,definitions.size());count++) {
            int index=sendCursor%definitions.size();JsonObject page=definitions.get(index).deepCopy();String key=page.get("key").getAsString();
            for(JsonElement value:page.getAsJsonArray("items")){JsonObject item=value.getAsJsonObject();Integer quantity=quantities.get(item.get("id").getAsInt());if(quantity!=null)item.addProperty("quantity",quantity);}
            Long counter=counter(client,key);if(counter!=null)page.addProperty("counter",counter);if(counter!=null&&drops.containsKey(key)&&drops.get(key)<=counter)page.addProperty("lastDropCounter",drops.get(key));
            batch.add(page);
            if(body.toString().getBytes(StandardCharsets.UTF_8).length>15500){batch.remove(batch.size()-1);break;}
            sendCursor=(index+1)%definitions.size();
        }
        return body;
    }
}
