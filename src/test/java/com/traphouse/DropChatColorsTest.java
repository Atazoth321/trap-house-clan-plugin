package com.traphouse;

import org.junit.Test;
import static org.junit.Assert.*;

public class DropChatColorsTest {
    private final TrapHouseConfig config = new TrapHouseConfig() {};

    @Test public void colorsGameRewardNotices() {
        assertEquals("<col=ffc048>Valuable drop: Dragon bones</col>",
            DropChatColors.recolor("<col=ff0000>Valuable drop: Dragon bones</col>", config));
        assertEquals("<col=ffc048>Untradeable drop: Fire cape</col>",
            DropChatColors.recolor("Untradeable drop: Fire cape", config));
        assertEquals("<col=c080ff>You have a funny feeling like you're being followed.</col>",
            DropChatColors.recolor("You have a funny feeling like you're being followed.", config));
        assertEquals("<col=64d282>New item added to your collection log: Abyssal whip</col>",
            DropChatColors.recolor("New item added to your collection log: Abyssal whip", config));
    }

    @Test public void leavesOtherChatAndDisabledNoticesAlone() {
        assertNull(DropChatColors.recolor("You bury the bones.", config));
        assertNull(DropChatColors.recolor("[Trap House] You earned 1 clan point for a drop.", config));
        assertNull(DropChatColors.recolor("Valuable drop: Dragon bones", new TrapHouseConfig() {
            @Override public boolean dropChatColors() { return false; }
        }));
    }
}
