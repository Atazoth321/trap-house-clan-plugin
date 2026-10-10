package com.traphouse;

/** A participant's kill or XP gains during a Wise Old Man competition. */
final class LeaderboardEntry {
    final String name;
    final long score;

    LeaderboardEntry(String name, long score) {
        this.name = name;
        this.score = score;
    }
}
