package com.traphouse.server;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class BalancedRewardsTest {
    @Test public void commonUniquesPayLessThanRareRewardsAndPetsRemainOnceOnly() {
        Map<String,Integer> points=new HashMap<>();int barrows=0,pets=0;
        for(AutomaticRewards.Rule rule:AutomaticRewards.rules()) {
            int reward=BalancedRewards.points(rule);assertTrue(reward>0);assertTrue(reward<=1000);points.put(rule.key,reward);
            if(rule.category.equals("Pets")){assertEquals(500,reward);assertEquals(1,rule.limit);pets++;}
            if(rule.key.startsWith("chest:barrows:")){assertEquals(50,reward);barrows++;}
        }
        assertEquals(24,barrows);assertEquals(55,pets);
        assertEquals(Integer.valueOf(150),points.get("chest:chambers of xeric:21079"));
        assertEquals(Integer.valueOf(250),points.get("chest:tombs of amascut:26219"));
        assertEquals(Integer.valueOf(1000),points.get("chest:tombs of amascut:27277"));
        assertEquals(4,BalancedRewards.BARROWS_DAILY_LIMIT);
    }
    @Test public void onlyAllowlistedLootHasSharedItemCaps() {
        assertEquals("12922",BalancedRewards.sharedItem("LOOT","2042:12922"));
        assertEquals("12922",BalancedRewards.sharedItem("LOOT","2043:12922"));
        assertEquals("27277",BalancedRewards.sharedItem("LOOT","chest:tombs of amascut:27277"));
        assertNull(BalancedRewards.sharedItem("LOOT","999999:12922"));
        assertNull(BalancedRewards.sharedItem("MANUAL","verified rare drop"));
        assertFalse(BalancedRewards.barrows("LOOT","chest:unknown:4708"));
        assertTrue(BalancedRewards.barrows("LOOT","chest:barrows:4708"));
    }
}
