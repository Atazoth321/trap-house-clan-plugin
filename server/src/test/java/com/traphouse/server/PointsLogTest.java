package com.traphouse.server;

import org.junit.Test;
import static org.junit.Assert.*;

public class PointsLogTest {
    @Test public void acceptsBoundedFiltersAndDecodesNames() {
        java.util.Map<String,String> p=PointsLog.parameters("player=TRAP+Raider&category=Pets&sort=highest&search=dragon&group=true&page=2");
        assertEquals("TRAP Raider",p.get("player"));assertEquals("2",p.get("page"));assertEquals("true",p.get("group"));
    }
    @Test public void rejectsUnknownDuplicateOrUnboundedParameters() {
        for(String query:new String[]{"player=Member&category=Injected","player=Member&sort=points;DROP","player=Member&page=0","player=Member&page=9999999","player=Member&group=yes","player=Member&player=Other","player=Member&permission=OWNER","category=Drops"}) {
            try{PointsLog.parameters(query);fail(query);}catch(Policy.Rejected expected){assertEquals(400,expected.status);}
        }
    }
}
