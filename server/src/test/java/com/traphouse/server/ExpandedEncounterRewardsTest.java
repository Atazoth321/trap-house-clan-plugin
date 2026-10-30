package com.traphouse.server;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class ExpandedEncounterRewardsTest {
    @Test public void uniqueCatalogHasSharedCapsAndOnlyClueLootOrMilestonesInClues() {
        Set<String> keys=new HashSet<>(),clueItems=new HashSet<>();int milestones=0,total=0;
        for(AutomaticRewards.Rule rule:ExpandedEncounterRewards.rules()) {
            assertTrue(keys.add(rule.type+":"+rule.key));assertEquals(Policy.normalize(rule.key),rule.key);
            if(rule.type.equals("COLLECTION")) {
                assertEquals("Clues",rule.category);assertEquals(1,rule.limit);assertTrue(rule.key.startsWith("clue milestone:"));
                assertTrue(EncounterRewards.clueItem(rule.type,rule.key)>0);milestones++;total+=rule.points;
            } else {
                assertEquals(2,rule.limit);assertNotNull(BalancedRewards.sharedItem(rule.type,rule.key));
                if(rule.category.equals("Clues")) {
                    assertTrue(rule.key.startsWith("clue:"));assertTrue(EncounterRewards.clueItem(rule.type,rule.key)>0);
                    clueItems.add(rule.key.substring(rule.key.lastIndexOf(':')+1));
                } else assertEquals("Drops",rule.category);
            }
        }
        assertEquals(18,milestones);assertEquals(3600,total);assertEquals(44,clueItems.size());
        assertTrue(keys.contains("LOOT:clue:medium:2577"));assertTrue(keys.contains("LOOT:12596:30626"));
        assertTrue(keys.contains("LOOT:14012:30070"));assertTrue(keys.contains("LOOT:9433:24511"));
        assertFalse(keys.contains("LOOT:12593:30626"));assertFalse(keys.contains("LOOT:clue:master:28226")); // Crafted felling axe.
    }
}
