package com.traphouse;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;

final class EventSummary {
    private EventSummary() { }

    static String target(ClanEvent event) {
        String target = event.metric.replace('_', ' ');
        return target.isEmpty() ? target : target.substring(0, 1).toUpperCase(Locale.ROOT) + target.substring(1);
    }

    static String timing(ClanEvent event, Instant now) {
        if (!now.isBefore(event.endsAt)) return "Ended";
        boolean upcoming = now.isBefore(event.startsAt);
        long minutes = Math.max(1, (Duration.between(now, upcoming ? event.startsAt : event.endsAt).getSeconds() + 59) / 60);
        long days = minutes / 1440;
        long hours = minutes % 1440 / 60;
        String duration = days > 0 ? days + "d " + hours + "h"
            : hours > 0 ? hours + "h " + minutes % 60 + "m" : minutes + "m";
        return upcoming ? "Starts in " + duration : duration + " left";
    }
}
