package com.traphouse.server;
import org.junit.Test;
import static org.junit.Assert.*;
public class WiseOldManStatsTest {
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
