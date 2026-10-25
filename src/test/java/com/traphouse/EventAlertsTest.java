package com.traphouse;

import java.time.Instant;
import java.util.Collections;
import org.junit.Test;
import static org.junit.Assert.*;

public class EventAlertsTest {
    private final Instant now = Instant.parse("2026-10-25T12:00:00Z");

    private ClanEvent event(int id, Instant start, Instant end) {
        return new ClanEvent(id, "Test event", "fishing", start, end, Collections.emptyList());
    }

    @Test public void doesNotDuplicateLoginButAnnouncesNewlyDiscoveredEvent() {
        EventAlerts alerts = new EventAlerts();
        assertTrue(alerts.poll(event(1, now.minusSeconds(60), now.plusSeconds(200000)), now, true, true).isEmpty());
        assertEquals(1, alerts.poll(event(2, now, now.plusSeconds(200000)), now, true, true).size());
        assertTrue(alerts.poll(event(2, now, now.plusSeconds(200000)), now, true, true).isEmpty());
    }

    @Test public void detectsStartAfterNoEventOrUpcomingEvent() {
        EventAlerts alerts = new EventAlerts();
        assertTrue(alerts.poll(null, now, true, true).isEmpty());
        ClanEvent upcoming = event(1, now.plusSeconds(60), now.plusSeconds(200000));
        assertTrue(alerts.poll(upcoming, now, true, true).isEmpty());
        assertEquals(1, alerts.poll(upcoming, now.plusSeconds(60), true, true).size());
    }

    @Test public void remindersFireOnceAtEachThresholdAndSurviveSessionChanges() {
        EventAlerts alerts = new EventAlerts();
        ClanEvent event = event(1, now.minusSeconds(60), now.plusSeconds(86400));
        assertEquals(1, alerts.poll(event, now, true, true).size());
        alerts.beginSession();
        assertTrue(alerts.poll(event, now.plusSeconds(1), true, true).isEmpty());
        assertEquals(1, alerts.poll(event, event.endsAt.minusSeconds(3600), true, true).size());
        assertTrue(alerts.poll(event, event.endsAt.minusSeconds(1), true, true).isEmpty());
        assertTrue(alerts.poll(event, event.endsAt, true, true).isEmpty());
    }

    @Test public void loginInFinalHourProducesOneReminderAndDisabledAlertsStaySilent() {
        ClanEvent event = event(1, now.minusSeconds(60), now.plusSeconds(1800));
        assertEquals(1, new EventAlerts().poll(event, now, true, true).size());
        assertTrue(new EventAlerts().poll(event, now, false, false).isEmpty());
    }
}
