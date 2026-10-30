package com.traphouse;

import java.util.Locale;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.runelite.client.util.Text;

/** Only game system messages are passed here; player chat is never evidence. */
final class RankingTracker {
    static List<RankingEvent> containerLoot(String player,net.runelite.client.plugins.loottracker.LootReceived event) {
        if(event.getType()==net.runelite.http.api.loottracker.LootRecordType.NPC) {
            Object metadata=event.getMetadata();
            if(metadata instanceof Map)metadata=((Map<?,?>)metadata).get("id");
            if(!(metadata instanceof Number))return Collections.emptyList();
            int npc=((Number)metadata).intValue();
            if(!expandedLootNpc(npc))return Collections.emptyList();
            List<RankingEvent> result=new ArrayList<>();Set<Integer> seen=new HashSet<>();
            for(net.runelite.client.game.ItemStack item:event.getItems())if(seen.add(item.getId()))result.add(new RankingEvent(player,"LOOT",npc+":"+item.getId()));
            return result;
        }
        if(event.getType()!=net.runelite.http.api.loottracker.LootRecordType.EVENT)return Collections.emptyList();
        String source=normalize(event.getName());
        if(source.matches("clue scroll \\((beginner|easy|medium|hard|elite|master)\\)")) {
            if(event.getItems().isEmpty())return Collections.emptyList();
            String tier=source.substring(source.indexOf('(')+1,source.length()-1);
            List<RankingEvent> result=new ArrayList<>();Set<Integer> seen=new HashSet<>();
            result.add(new RankingEvent(player,"COLLECTION","clue completion:"+tier));
            for(net.runelite.client.game.ItemStack item:event.getItems())if(seen.add(item.getId()))result.add(new RankingEvent(player,"LOOT","clue:"+tier+":"+item.getId()));
            return result;
        }
        if(!Arrays.asList("chambers of xeric","theatre of blood","tombs of amascut","barrows","lunar chest").contains(source))return Collections.emptyList();
        List<RankingEvent> result=new ArrayList<>();Set<Integer> seen=new HashSet<>();
        for(net.runelite.client.game.ItemStack item:event.getItems()) {
            if(seen.add(item.getId()))result.add(new RankingEvent(player,"LOOT","chest:"+source+":"+item.getId()));
        }
        return result;
    }
    static boolean serverLootNpc(int npc) {
        return (npc>=9021&&npc<=9024)||(npc>=9035&&npc<=9038)
            ||(npc>=13599&&npc<=13606)||npc==13668||npc==13669;
    }
    static boolean expandedLootNpc(int npc) {
        return npc==12596||(npc>=14147&&npc<=14149)||(npc>=14009&&npc<=14013)
            ||npc==377||npc==378||(npc>=9416&&npc<=9433)||(npc>=9460&&npc<=9464)||(npc>=11153&&npc<=11155);
    }
    private static final Pattern CLUE_COUNT=Pattern.compile("^You have completed ([0-9]+) (beginner|easy|medium|hard|elite|master) Treasure Trails?\\.$");
    static List<RankingEvent> clueMilestones(String player,String message) {
        Matcher count=CLUE_COUNT.matcher(Text.removeTags(message).trim());
        if(!count.matches())return Collections.emptyList();
        long completions;
        try{completions=Long.parseLong(count.group(1));}catch(NumberFormatException invalid){return Collections.emptyList();}
        List<RankingEvent> result=new ArrayList<>();
        for(int target:new int[]{10,50,100})if(completions>=target)result.add(new RankingEvent(player,"COLLECTION","clue milestone:"+count.group(2)+":"+target));
        return result;
    }
    private static final Pattern COLLECTION = Pattern.compile("^New item added to your collection log: (.+)$");
    private static final Pattern ACHIEVEMENT = Pattern.compile("^Congratulations, you've completed an? (?:Easy|Medium|Hard|Elite|Master|Grandmaster) combat task: (.+?)\\.?$", Pattern.CASE_INSENSITIVE);

    static RankingEvent chat(String player, String message) {
        String plain = Text.removeTags(message).trim();
        Matcher collection = COLLECTION.matcher(plain);
        if (collection.matches()) return new RankingEvent(player, "COLLECTION", normalize(collection.group(1)));
        Matcher achievement = ACHIEVEMENT.matcher(plain);
        if (achievement.matches()) return new RankingEvent(player, "COMBAT", normalize(achievement.group(1).replaceFirst("\\s+\\(\\d+ points?\\)$", "")));
        return null;
    }

    static String normalize(String value) {
        return value.replace('\u00a0', ' ').trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }
}
