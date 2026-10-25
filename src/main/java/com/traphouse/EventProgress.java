package com.traphouse;

import java.util.Locale;
import net.runelite.client.util.Text;

final class EventProgress {
    private EventProgress() { }

    static String describe(ClanEvent event, String player, boolean skill) {
        if (player == null || player.isEmpty()) return "Log in to see your event progress.";
        if (event == null) return "No event selected.";
        for (int index = 0; index < event.scores.size(); index++) {
            LeaderboardEntry entry = event.scores.get(index);
            if (!Text.standardize(entry.name.replace('_', ' ')).equals(Text.standardize(player.replace('_', ' ')))) continue;
            String unit = skill ? "XP" : "kills";
            String progress = String.format(Locale.US, "You: #%d / %,d %s gained", index + 1, entry.score, unit);
            if (index == 0) return progress + " / Leading!";
            long gap = event.scores.get(index - 1).score - entry.score;
            return progress + (gap == 0 ? " / Tied with the player above" : String.format(Locale.US, " / %,d %s behind #%d", gap, unit, index));
        }
        return "No recorded score for you in this event.";
    }
}
