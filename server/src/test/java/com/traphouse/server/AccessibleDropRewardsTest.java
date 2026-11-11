package com.traphouse.server;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class AccessibleDropRewardsTest {
    @Test public void smallRewardsShareTheirDailyCapAcrossRealSources() {
        Map<String,AutomaticRewards.Rule> rules=new HashMap<>();
        Set<String> items=new HashSet<>();
        for(AutomaticRewards.Rule rule:AccessibleDropRewards.rules()) {
            assertNull(rules.put(rule.key,rule));
            assertEquals(1,rule.limit);
            assertTrue(rule.points>=2&&rule.points<=20);
            String item=rule.key.substring(rule.key.lastIndexOf(':')+1);
            items.add(item);
            assertEquals(item,BalancedRewards.sharedItem(rule.type,rule.key));
        }
        assertEquals(20,items.size());
        assertEquals(10,rules.get("2212:11840").points);
        assertEquals(15,rules.get("270:4087").points);
        assertEquals(15,rules.get("275:4585").points);
        assertEquals("11840",BalancedRewards.sharedItem("LOOT","3168:11840"));
        // Wrong monsters, uncharged player-owned masks and arena items cannot qualify.
        for(String invalid:Arrays.asList("2075:11840","2212:4087","1047:8921","2075:20402","14172:4111")) {
            assertFalse(rules.containsKey(invalid));
            assertNull(BalancedRewards.sharedItem("LOOT",invalid));
        }
    }
}

