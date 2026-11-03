package com.traphouse.server;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class BeginnerDropRewardsTest {
    @Test public void allRequestedDropsHaveApprovedRatesAndSharedItemCaps() {
        Map<Integer,Integer> expected=new HashMap<>();
        int[] items={28798,20756,22372,28813,28583,11037,20727,21009,21028,22957,22960,22994,21275,11928,11929,11930,11931,11932,11933,20716,25576,25582,25580,26813,26822};
        int[] points={50,100,150,150,100,75,100,150,200,150,150,150,100,100,100,100,100,100,100,150,150,100,75,150,200};
        for(int i=0;i<items.length;i++)expected.put(items[i],points[i]);
        Set<Integer> found=new HashSet<>();Set<String> keys=new HashSet<>();
        for(AutomaticRewards.Rule rule:BeginnerDropRewards.rules()) {
            int item=Integer.parseInt(rule.key.substring(rule.key.lastIndexOf(':')+1));
            assertEquals(expected.get(item),Integer.valueOf(rule.points));found.add(item);
            assertTrue(keys.add(rule.key));assertEquals("LOOT",rule.type);assertEquals("Drops",rule.category);assertEquals(2,rule.limit);
            assertEquals(String.valueOf(item),BalancedRewards.sharedItem(rule.type,rule.key));
        }
        assertEquals(expected.keySet(),found);
        for(String invalid:Arrays.asList("2004:22957","11192:22994","12502:28583","12740:28813","14172:20727","6615:11928","chest:tempoross:20716","12720:28810"))assertFalse(keys.contains(invalid));
    }
}
