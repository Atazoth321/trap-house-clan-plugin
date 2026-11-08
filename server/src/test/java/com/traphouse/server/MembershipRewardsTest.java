package com.traphouse.server;

import java.time.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class MembershipRewardsTest {
    @Test public void requiresFullSevenDays() {
        Instant start=Instant.parse("2026-03-07T17:23:00Z");
        assertEquals(0,MembershipRewards.completedWeeks(start,start.minusSeconds(1)));
        assertEquals(0,MembershipRewards.completedWeeks(start,start.plusSeconds(604799)));
        assertEquals(1,MembershipRewards.completedWeeks(start,start.plusSeconds(604800)));
        assertEquals(2,MembershipRewards.completedWeeks(start,start.plusSeconds(1209600)));
    }
}
