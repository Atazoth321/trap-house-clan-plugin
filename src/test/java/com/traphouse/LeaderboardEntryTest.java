package com.traphouse;

import org.junit.Test;
import static org.junit.Assert.*;

public class LeaderboardEntryTest {
    @Test public void recognizesBossCompetitionMetrics() {
        assertTrue(WiseOldManClient.isBossMetric("sarachnis"));
        assertTrue(WiseOldManClient.isBossMetric("lunar_chests"));
        assertTrue(WiseOldManClient.isBossMetric("chambers_of_xeric_challenge_mode"));
        assertFalse(WiseOldManClient.isBossMetric("overall"));
        assertFalse(WiseOldManClient.isBossMetric("woodcutting"));
    }
}
