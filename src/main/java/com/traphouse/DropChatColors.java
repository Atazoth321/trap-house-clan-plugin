package com.traphouse;

import java.awt.Color;
import java.util.Locale;
import net.runelite.client.util.Text;

/** Colors the game's own reward notices without adding duplicate chat lines. */
final class DropChatColors {
    static String recolor(String message, TrapHouseConfig config) {
        if (!config.dropChatColors() || message == null) return null;
        String plain = Text.removeTags(message);
        Color color;
        if (plain.startsWith("You have a funny feeling like you're being followed")
            || plain.startsWith("You feel something weird sneaking into your backpack")
            || plain.startsWith("You have a funny feeling like you would have been followed")) {
            color = config.rankingPetMessageColor();
        } else if (plain.startsWith("New item added to your collection log:")) {
            color = config.rankingMilestoneMessageColor();
        } else if (plain.startsWith("Valuable drop:") || plain.startsWith("Untradeable drop:")
            || plain.startsWith("You received a drop:")) {
            color = config.rankingDropMessageColor();
        } else {
            return null;
        }
        if (color == null) return null;
        // Keep non-color markup (such as item images) intact.
        String content = message.replaceAll("(?i)</?col(?:=[^>]*)?>", "");
        return "<col=" + String.format(Locale.ROOT, "%06x", color.getRGB() & 0xffffff)
            + ">" + content + "</col>";
    }
}
