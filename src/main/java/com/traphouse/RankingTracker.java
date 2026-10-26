package com.traphouse;

import java.util.Locale;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.runelite.client.util.Text;

/** Only game system messages are passed here; player chat is never evidence. */
final class RankingTracker {
    static List<RankingEvent> containerLoot(String player,net.runelite.client.plugins.loottracker.LootReceived event) {
        if(event.getType()!=net.runelite.http.api.loottracker.LootRecordType.EVENT)return Collections.emptyList();
        String source=normalize(event.getName());
        if(!source.equals("chambers of xeric")&&!source.equals("theatre of blood")&&!source.equals("tombs of amascut")&&!source.equals("barrows"))return Collections.emptyList();
        List<RankingEvent> result=new ArrayList<>();Set<Integer> seen=new HashSet<>();
        for(net.runelite.client.game.ItemStack item:event.getItems()) {
            if(seen.add(item.getId()))result.add(new RankingEvent(player,"LOOT","chest:"+source+":"+item.getId()));
        }
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
