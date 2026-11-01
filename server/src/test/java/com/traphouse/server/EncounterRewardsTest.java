package com.traphouse.server;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class EncounterRewardsTest {
    @Test public void encounterRewardsShareItemCapsAcrossVariantsAndCluesAreSeparate() {
        Set<String> keys=new HashSet<>();int clues=0,moons=0,cluePoints=0;
        for(AutomaticRewards.Rule rule:EncounterRewards.rules()) {
            assertTrue(keys.add(rule.type+":"+rule.key));assertEquals(Policy.normalize(rule.key),rule.key);
            if(rule.category.equals("Clues")) {
                clues++;cluePoints+=rule.points;assertEquals("COLLECTION",rule.type);assertEquals(1,rule.limit);
                assertTrue(EncounterRewards.clueItem(rule.type,rule.key)>0);
            } else {
                assertEquals("Drops",rule.category);assertEquals(2,rule.limit);
                assertEquals(rule.key.substring(rule.key.lastIndexOf(':')+1),BalancedRewards.sharedItem(rule.type,rule.key));
                if(rule.key.startsWith("chest:lunar chest:")){moons++;assertEquals(75,rule.points);}
            }
        }
        assertEquals(6,clues);assertEquals(600,cluePoints);assertEquals(12,moons);
        assertFalse(keys.contains("LOOT:12123:23956"));
        assertNull(BalancedRewards.sharedItem("LOOT","12123:23956"));
    }
}
