package com.traphouse.server;
import org.junit.Test;
import static org.junit.Assert.*;
public class WiseOldManStatsTest {
    @Test public void importsAccountTypeXpBossesAndClueTiersWithoutUnrankedSentinels() {
        com.google.gson.JsonObject stats=WiseOldManStats.parse("{\"ehp\":12,\"ehb\":20,\"type\":\"hardcore\",\"latestSnapshot\":{\"data\":{\"skills\":{\"overall\":{\"experience\":5000000000}},\"bosses\":{\"vorkath\":{\"kills\":524},\"zulrah\":{\"kills\":-1}},\"activities\":{\"clue_scrolls_hard\":{\"score\":80},\"clue_scrolls_all\":{\"score\":100}}}}}").getAsJsonObject("browser");
        assertEquals("HARDCORE_IRONMAN",stats.get("accountType").getAsString());assertEquals(5_000_000_000L,stats.get("xp").getAsLong());assertEquals(524,stats.getAsJsonObject("bosses").get("vorkath").getAsInt());assertFalse(stats.getAsJsonObject("bosses").has("zulrah"));assertEquals(80,stats.getAsJsonObject("clues").get("hard").getAsInt());assertEquals(100,stats.getAsJsonObject("clues").get("all").getAsInt());
    }
    @Test public void retainsSnapshotTimestampForServerSideBaselineAndReplayChecks() {
        assertEquals("2026-10-25T12:00:00Z",WiseOldManStats.parse("{\"ehp\":859.45695,\"ehb\":111.83056,\"updatedAt\":\"2026-10-25T12:00:00Z\"}").get("snapshotAt").getAsString());
    }
    @Test public void importsOnlyValidPublicEfficiencyValues() {
        assertEquals(12.5,WiseOldManStats.parse("{\"ehp\":12.5,\"ehb\":42}").get("ehp").getAsDouble(),0.001);
        for(String value:new String[]{"{}","{\"ehp\":-1,\"ehb\":0}","{\"ehp\":\"secret\",\"ehb\":0}"}) {
            try{WiseOldManStats.parse(value);fail(value);}catch(IllegalArgumentException expected){}
        }
    }
}
