package com.traphouse.server;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class AutomaticRewardsTest {
    @Test public void easyTestDropOnlyRewardsCowhideFromOrdinaryCows() {
        Set<String> keys=new HashSet<>();
        for(AutomaticRewards.Rule rule:AutomaticRewards.testDropRules()) {
            assertEquals("LOOT",rule.type);assertEquals("Drops",rule.category);
            assertEquals(1,rule.points);assertEquals(1,rule.limit);assertTrue(keys.add(rule.key));
        }
        assertEquals(new HashSet<>(Arrays.asList("2790:1739","2791:1739","2793:1739","2795:1739")),keys);
    }
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
                if(rule.key.startsWith("chest:barrows:"))assertEquals(50,rule.points);
            }
        }
        assertEquals(55,pets);assertEquals(118,items.size());
        assertTrue(keys.contains("LOOT:2215:11832"));
        assertTrue(keys.contains("LOOT:chest:tombs of amascut:27277"));
        assertTrue(keys.contains("COLLECTION:pet kraken"));
        assertTrue(keys.contains("LOOT:319:12819"));
        assertTrue(keys.contains("LOOT:11278:26372"));
        assertTrue(keys.contains("LOOT:chest:barrows:4708"));
        assertFalse(keys.contains("LOOT:6632:11832")); // Pet Graardor must never be a loot source.
    }
}
