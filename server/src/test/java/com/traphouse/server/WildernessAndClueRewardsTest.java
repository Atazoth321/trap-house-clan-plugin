package com.traphouse.server;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class WildernessAndClueRewardsTest {
    @Test public void newRulesAreUniqueAndShareCapsAcrossSourcesAndClueTiers() {
        List<AutomaticRewards.Rule> existing=new ArrayList<>(AutomaticRewards.rules());
        existing.addAll(EncounterRewards.rules());existing.addAll(ExpandedEncounterRewards.rules());
        existing.addAll(AdditionalDropRewards.rules());existing.addAll(BeginnerDropRewards.rules());
        existing.addAll(AccessibleDropRewards.rules());existing.addAll(SlayerDropRewards.rules());existing.addAll(BroadDropRewards.rules());
        Set<String> keys=new HashSet<>(),items=new HashSet<>();
        for(AutomaticRewards.Rule rule:existing)keys.add(rule.type+":"+rule.key);
        for(AutomaticRewards.Rule rule:WildernessAndClueRewards.rules()) {
            assertTrue(rule.key,keys.add(rule.type+":"+rule.key));
            String item=rule.key.substring(rule.key.lastIndexOf(':')+1);items.add(item);
            assertEquals(item,BalancedRewards.sharedItem(rule.type,rule.key));
            if(rule.key.startsWith("clue:")) {
                assertEquals(1,rule.limit);
                assertEquals(Integer.parseInt(item),EncounterRewards.clueItem(rule.type,rule.key));
            } else assertEquals(item.equals("21817")?1:2,rule.limit);
        }
        assertEquals(55,items.size());
        assertEquals(BalancedRewards.sharedItem("LOOT","clue:easy:3827"),BalancedRewards.sharedItem("LOOT","clue:hard:3827"));
        assertEquals(BalancedRewards.sharedItem("LOOT","7881:22547"),BalancedRewards.sharedItem("LOOT","7940:22547"));
        for(String invalid:Arrays.asList("7881:22550","11246:22547","clue:beginner:3827","7881:21816"))
            assertNull(BalancedRewards.sharedItem("LOOT",invalid));
    }
}
