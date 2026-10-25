package com.traphouse;

import org.junit.Test;
import static org.junit.Assert.*;

public class RankingTrackerTest {
    @Test public void parsesOnlySupportedSystemMessages() {
        RankingEvent item = RankingTracker.chat("Member", "New item added to your collection log: <col=ff0000>Abyssal whip</col>");
        assertEquals("COLLECTION", item.type);
        assertEquals("abyssal whip", item.key);
        RankingEvent task = RankingTracker.chat("Member", "Congratulations, you've completed an Elite combat task: Perfect kill.");
        assertEquals("COMBAT", task.type);
        assertEquals("perfect kill", task.key);
        assertEquals("perfect kill", RankingTracker.chat("Member", "Congratulations, you've completed an elite combat task: Perfect kill (4 points).").key);
        assertNull(RankingTracker.chat("Member", "Member received a drop: Abyssal whip"));
        assertNull(RankingTracker.chat("Member", "hello"));
    }

    @Test public void uniqueObservationIdsAndStableRetryIdentity() {
        RankingEvent first = new RankingEvent("Member", "LOOT", "1:2");
        assertNotEquals(first.id, new RankingEvent("Member", "LOOT", "1:2").id);
        assertEquals(first.id, first.id);
    }

    @Test public void rejectsInsecureOrAmbiguousEndpoints() {
        assertEquals("https://clan.example", RankingClient.endpoint("https://clan.example/"));
        for (String value : new String[] {"http://clan.example", "https://user:pass@clan.example", "https://clan.example/path", "https://clan.example?token=x", "https://clan.example#x"}) {
            try { RankingClient.endpoint(value); fail(value); } catch (IllegalArgumentException expected) { }
        }
    }
}
