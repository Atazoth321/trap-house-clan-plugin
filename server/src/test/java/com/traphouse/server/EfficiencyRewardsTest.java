package com.traphouse.server;

import java.math.BigDecimal;
import java.time.Instant;
import org.junit.Test;
import static org.junit.Assert.*;

public class EfficiencyRewardsTest {
    private BigDecimal n(String number){return new BigDecimal(number);}
    @Test public void calculatesPositiveGainsAtApprovedRates() {
        assertEquals(0,n("20").compareTo(EfficiencyRewards.earned(n("100"),n("50"),n("101"),n("51"))));
        assertEquals(0,BigDecimal.ZERO.compareTo(EfficiencyRewards.earned(n("100"),n("50"),n("99"),n("49"))));
        assertEquals(10,EfficiencyRewards.payout(EfficiencyRewards.earned(n("100"),n("50"),n("101"),n("49")),0));
    }
    @Test public void capsCombinedGainsAndKeepsFractionalValuesBelowOnePoint() {
        assertEquals(250,EfficiencyRewards.payout(n("999999"),0));
        assertEquals(3,EfficiencyRewards.payout(n("20"),247));
        assertEquals(0,EfficiencyRewards.payout(n("20"),250));
        assertEquals(0,EfficiencyRewards.payout(n("0.99"),0));
        assertEquals(1,EfficiencyRewards.payout(n("1.01"),0));
    }
    @Test public void weeksStartOnMondayAtMidnightUtc() {
        assertEquals(Instant.parse("2026-10-19T00:00:00Z"),EfficiencyRewards.week(Instant.parse("2026-10-25T23:59:59Z")));
        assertEquals(Instant.parse("2026-10-26T00:00:00Z"),EfficiencyRewards.week(Instant.parse("2026-10-26T00:00:00Z")));
    }
    @Test public void clientsCannotSubmitEfficiencyAsAnActivity() {
        try{Policy.observationType("EFFICIENCY");fail();}catch(Policy.Rejected rejected){assertEquals(400,rejected.status);}
    }
}
