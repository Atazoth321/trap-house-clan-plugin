package com.traphouse;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

public class EventProgressTest {
    @Test public void findsPlayerOutsideTopTenAndUsesRecordedGap() {
        List<LeaderboardEntry> scores = new ArrayList<>();
        for (int index = 0; index < 12; index++) scores.add(new LeaderboardEntry("Player " + index, 1200 - index * 100));
        ClanEvent event = new ClanEvent(1, "Test", "fishing", Instant.EPOCH, Instant.MAX, scores);
        assertEquals("You: #12 / 100 XP gained / 100 XP behind #11", EventProgress.describe(event, "player_11", true));
        assertEquals("You: #1 / 1,200 kills gained / Leading!", EventProgress.describe(event, "Player 0", false));
        assertEquals("No recorded score for you in this event.", EventProgress.describe(event, "Absent", false));
        assertEquals("Log in to see your event progress.", EventProgress.describe(event, null, false));
    }
}
