package com.traphouse;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.runelite.client.util.Text;

/** Only game system messages are passed here; player chat is never evidence. */
final class RankingTracker {
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
