package com.traphouse.server;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class AdditionalDropRewardsTest {
    @Test public void namedSourcesHaveExpectedRewardsAndExcludeNonCombatCopies() {
        Map<String,AutomaticRewards.Rule> rules=new HashMap<>();
        for(AutomaticRewards.Rule rule:AdditionalDropRewards.rules()) {
            assertNull(rules.put(rule.key,rule));assertEquals("LOOT",rule.type);assertEquals("Drops",rule.category);assertEquals(2,rule.limit);
            assertNotNull(BalancedRewards.sharedItem(rule.type,rule.key));
        }
        String[] keys={"9293:24268","7144:19529","6766:13576","415:13265","5908:13273","7889:21742","8713:23517","4005:11235","9756:24777","9049:23953","9049:6571","chest:unsired:13274","chest:unsired:13275","chest:unsired:13276"};
        int[] points={250,250,400,200,150,75,50,100,200,200,100,200,200,200};
        for(int i=0;i<keys.length;i++)assertEquals(keys[i],points[i],rules.get(keys[i]).points);
        for(String key:Arrays.asList("7152:19529","7573:13576","7574:13576","11961:13273","12124:23953","9599:24777","14174:13265"))assertFalse(rules.containsKey(key));
        assertFalse(rules.containsKey("9049:6573")); // Zalcano drops an uncut onyx.
    }
}
