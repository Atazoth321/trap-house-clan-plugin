package com.traphouse;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.runelite.client.util.Text;

/** Accessed on the client thread. Deduplicates alerts across refreshes and world hops. */
final class EventAlerts {
    private final Set<Integer> seen = new HashSet<>();
    private final Set<Integer> dayReminded = new HashSet<>();
    private final Set<Integer> hourReminded = new HashSet<>();
    private boolean initialized;

    void beginSession() { initialized = false; }

    List<String> poll(ClanEvent event, Instant now, boolean newEvents, boolean reminders) {
        List<String> messages = new ArrayList<>();
        boolean active = event != null && !now.isBefore(event.startsAt) && now.isBefore(event.endsAt);
        if (!initialized) {
            initialized = true;
            if (active) seen.add(event.id);
        }
        if (!active) return messages;
        String title = Text.removeTags(event.title) + " (" + EventSummary.target(event) + ")";
        if (seen.add(event.id) && newEvents) messages.add("New event started: " + title + ".");
        long seconds = java.time.Duration.between(now, event.endsAt).getSeconds();
        if (seconds <= 3600) {
            dayReminded.add(event.id);
            if (hourReminded.add(event.id) && reminders) messages.add(title + " ends in " + EventSummary.timing(event, now).replace(" left", "") + ".");
        } else if (seconds <= 86400 && dayReminded.add(event.id) && reminders) {
            messages.add(title + " ends in " + EventSummary.timing(event, now).replace(" left", "") + ".");
        }
        return messages;
    }
}
