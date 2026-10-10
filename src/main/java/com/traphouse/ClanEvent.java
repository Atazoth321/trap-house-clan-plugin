package com.traphouse;

import java.time.Instant;
import java.util.List;

final class ClanEvent {
    final int id;
    final String title;
    final String metric;
    final Instant startsAt;
    final Instant endsAt;
    final List<LeaderboardEntry> scores;

    ClanEvent(int id, String title, String metric, Instant startsAt, Instant endsAt, List<LeaderboardEntry> scores) {
        this.id = id;
        this.title = title;
        this.metric = metric;
        this.startsAt = startsAt;
        this.endsAt = endsAt;
        this.scores = scores;
    }
}
