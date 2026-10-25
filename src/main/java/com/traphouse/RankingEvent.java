package com.traphouse;

import java.time.Instant;
import java.util.UUID;

final class RankingEvent {
    final String id = UUID.randomUUID().toString();
    final String observedAt = Instant.now().toString();
    final String player;
    final String type;
    final String key;

    RankingEvent(String player, String type, String key) {
        this.player = player;
        this.type = type;
        this.key = key;
    }
}
