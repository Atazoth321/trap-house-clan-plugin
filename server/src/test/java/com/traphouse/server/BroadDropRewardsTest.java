package com.traphouse.server;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class BroadDropRewardsTest {
    @Test public void expansionIsAdditiveAndCapsAreSharedByItem() {
        Set<String> keys=new HashSet<>();
        List<AutomaticRewards.Rule> existing=new ArrayList<>(AutomaticRewards.rules());
        existing.addAll(EncounterRewards.rules());existing.addAll(ExpandedEncounterRewards.rules());
        existing.addAll(AdditionalDropRewards.rules());existing.addAll(BeginnerDropRewards.rules());
        existing.addAll(AccessibleDropRewards.rules());existing.addAll(SlayerDropRewards.rules());
        for(AutomaticRewards.Rule rule:existing)keys.add(rule.type+":"+rule.key);
        Set<String> items=new HashSet<>();int clues=0;
        for(AutomaticRewards.Rule rule:BroadDropRewards.rules()) {
            assertTrue(rule.key,keys.add(rule.type+":"+rule.key));
            String item=rule.key.substring(rule.key.lastIndexOf(':')+1);items.add(item);
            assertEquals(item,BalancedRewards.sharedItem(rule.type,rule.key));
            if(rule.key.startsWith("clue:")) {
                clues++;assertEquals(1,rule.limit);assertEquals("Clues",rule.category);
                assertEquals(Integer.parseInt(item),EncounterRewards.clueItem(rule.type,rule.key));
            } else {assertEquals(2,rule.limit);assertEquals("Drops",rule.category);}
        }
        assertEquals(52,items.size());assertEquals(44,clues);
        assertNull(BalancedRewards.sharedItem("LOOT","clue:easy:19936"));
        assertNull(BalancedRewards.sharedItem("LOOT","2267:6562"));
    }
}
