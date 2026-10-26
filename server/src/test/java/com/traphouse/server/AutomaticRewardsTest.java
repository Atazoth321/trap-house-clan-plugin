package com.traphouse.server;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class AutomaticRewardsTest {
    @Test public void namedAllowlistHasUniqueKeysAndBoundedRewards() {
        Set<String> keys=new HashSet<>();Set<String> items=new HashSet<>();int pets=0;
        for(AutomaticRewards.Rule rule:AutomaticRewards.rules()) {
            assertTrue("Duplicate rule: "+rule.key,keys.add(rule.type+":"+rule.key));
            assertEquals(Policy.normalize(rule.key),rule.key);
            assertTrue(rule.points>0);assertTrue(rule.points<=1000);
            assertFalse(rule.name.isEmpty());assertFalse(rule.key.contains("*"));
            if(rule.type.equals("COLLECTION")) {
                assertEquals("Pets",rule.category);assertEquals(750,rule.points);assertEquals(1,rule.limit);pets++;
            } else {
                assertEquals("LOOT",rule.type);assertEquals("Drops",rule.category);assertEquals(2,rule.limit);items.add(rule.name);
            }
        }
        assertEquals(55,pets);assertEquals(50,items.size());
        assertTrue(keys.contains("LOOT:2215:11832"));
        assertTrue(keys.contains("LOOT:chest:tombs of amascut:27277"));
        assertTrue(keys.contains("COLLECTION:pet kraken"));
        assertFalse(keys.contains("LOOT:6632:11832")); // Pet Graardor must never be a loot source.
    }
}
