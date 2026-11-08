package com.traphouse.server;

import java.time.LocalDate;
import org.junit.Test;
import static org.junit.Assert.*;

public class VerifiedMembershipTest {
    @Test public void onlyFullWeeksEarnPoints() {
        LocalDate start=LocalDate.of(2024,2,25);
        assertEquals(25,VerifiedMembership.points(start,start.plusDays(7)));
        assertEquals(25,VerifiedMembership.points(start,start.plusDays(13)));
        assertEquals(50,VerifiedMembership.points(start,start.plusDays(14)));
    }
    @Test public void invalidRangesAreRejected() {
        LocalDate start=LocalDate.of(2024,1,1);
        for(long days:new long[]{-14,0,6,28007}) {
            try{VerifiedMembership.points(start,start.plusDays(days));fail();}catch(Policy.Rejected expected){assertEquals(400,expected.status);}
        }
    }
}
