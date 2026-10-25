package com.traphouse;

import java.time.Instant;
import java.util.Collections;
import org.junit.Test;
import static org.junit.Assert.assertEquals;

public class EventSummaryTest {
    private final Instant start = Instant.parse("2026-10-25T12:00:00Z");
    private final ClanEvent event = new ClanEvent(1, "Boss week", "lunar_chests", start,
        start.plusSeconds(86400), Collections.emptyList());

    @Test public void formatsTarget() {
        assertEquals("Lunar chests", EventSummary.target(event));
    }

    @Test public void distinguishesUpcomingActiveAndEndedAtBoundaries() {
        assertEquals("Starts in 1m", EventSummary.timing(event, start.minusSeconds(1)));
        assertEquals("1d 0h left", EventSummary.timing(event, start));
        assertEquals("1m left", EventSummary.timing(event, event.endsAt.minusSeconds(1)));
        assertEquals("Ended", EventSummary.timing(event, event.endsAt));
        assertEquals("Ended", EventSummary.timing(event, event.endsAt.plusSeconds(1)));
    }

    @Test public void roundsRemainingTimeUpToNextMinute() {
        assertEquals("1h 1m left", EventSummary.timing(event, event.endsAt.minusSeconds(3601)));
    }
}
