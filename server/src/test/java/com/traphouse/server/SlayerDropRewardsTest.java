package com.traphouse.server;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class SlayerDropRewardsTest {
    @Test public void expansionHasNoDuplicateRulesAndSharesExistingItemCaps() {
        Set<String> keys=new HashSet<>();
        for(AutomaticRewards.Rule rule:AutomaticRewards.rules())if(rule.type.equals("LOOT"))keys.add(rule.key);
        for(AutomaticRewards.Rule rule:AdditionalDropRewards.rules())keys.add(rule.key);
        for(AutomaticRewards.Rule rule:SlayerDropRewards.rules()) {
            assertTrue(rule.key,keys.add(rule.key));
            String item=rule.key.substring(rule.key.lastIndexOf(':')+1);
            assertEquals(item,BalancedRewards.sharedItem(rule.type,rule.key));
            if(item.equals("4151")){assertEquals(100,rule.points);assertEquals(2,rule.limit);}
            else if(item.equals("11286")){assertEquals(300,rule.points);assertEquals(2,rule.limit);}
            else assertEquals(1,rule.limit);
        }
        assertNull(BalancedRewards.sharedItem("LOOT","2075:4151"));
        assertEquals("4151",BalancedRewards.sharedItem("LOOT","7410:4151"));
        assertEquals("4151",BalancedRewards.sharedItem("LOOT","415:4151"));
    }
}
