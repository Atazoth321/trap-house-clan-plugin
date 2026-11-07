package com.traphouse.server;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class JarRewardsTest {
    @Test public void sixteenBossJarsUseNamedCollectionUnlocksAndLegacyItemKeys() {
        Set<String> keys=new HashSet<>(),items=new HashSet<>();Map<String,Integer> points=new HashMap<>();
        for(AutomaticRewards.Rule rule:JarRewards.rules()) {
            assertTrue(keys.add(rule.key));assertEquals("COLLECTION",rule.type);assertEquals("Drops",rule.category);
            assertEquals(1,rule.limit);assertTrue(Arrays.asList(250,350,500).contains(rule.points));
            String item=JarRewards.item(rule.type,rule.key);assertNotNull(item);items.add(item);points.put(item,rule.points);
            assertNull(JarRewards.item("LOOT",rule.key));
        }
        assertEquals(new HashSet<>(Arrays.asList("12007","12885","12936","13245","13277","19701","21745","22106","23064","23525","24495","25521","25524","29786","32921","34030")),items);
        assertEquals(Integer.valueOf(250),points.get("23525"));
        assertEquals(Integer.valueOf(350),points.get("12936"));
        assertEquals(Integer.valueOf(500),points.get("25521"));
        assertEquals("12936",JarRewards.item("LOOT","2042:12936"));
        assertEquals("12936",JarRewards.item("LOOT","2044:12936"));
        for(String key:Arrays.asList("1:12007","8060:22106","8062:22106","16309:34030","16317:34030","14859:32921","chest:bank:13277","5886:13277"))assertNull(JarRewards.item("LOOT",key));
    }
}
