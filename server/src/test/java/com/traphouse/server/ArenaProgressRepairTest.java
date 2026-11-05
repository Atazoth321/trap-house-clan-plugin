package com.traphouse.server;

import java.time.Instant;
import org.junit.Test;
import static org.junit.Assert.*;

public class ArenaProgressRepairTest {
    @Test public void incidentWindowMustBeOrderedAndBounded() {
        Instant start=Instant.parse("2026-11-04T20:00:00Z");
        ArenaProgressRepair.validateWindow(start,start.plusSeconds(86400));
        for(Instant end:new Instant[]{start,start.minusSeconds(1),start.plusSeconds(86401)}) {
            try {ArenaProgressRepair.validateWindow(start,end);fail("Invalid window accepted");}
            catch(IllegalArgumentException expected){}
        }
    }
}
