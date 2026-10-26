package com.traphouse;

import com.google.gson.*;
import java.nio.file.*;
import java.util.*;
import net.runelite.api.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class MilestoneDefinitionsTest {
    @Test public void everyNewProbeUsesSupportedGameKeysAndFitsSyncLimits()throws Exception {
        JsonArray goals=new Gson().fromJson(Files.readString(Paths.get("server/src/main/resources/milestones.json")),JsonArray.class);
        Set<String> ids=new HashSet<>(),observations=new HashSet<>();
        int skills=0,quests=0,totals=0;
        for(JsonElement entry:goals) {
            JsonObject goal=entry.getAsJsonObject();
            assertTrue(ids.add(UUID.fromString(goal.get("id").getAsString()).toString()));
            String source=goal.get("source").getAsString(),key=goal.get("sourceKey").getAsString();
            int target=goal.get("target").getAsInt();assertTrue(target>0);
            assertTrue(observations.add(source+":"+key+":"+target));
            switch(source) {
                case "SKILL":assertNotEquals(Skill.OVERALL,Skill.valueOf(key));assertTrue(target<=99);skills++;break;
                case "QUEST":assertNotNull(Quest.valueOf(key));assertEquals(1,target);quests++;break;
                case "TOTAL_LEVEL":assertEquals("total",key);assertNotEquals(2000,target);totals++;break;
                default:fail("Unsupported milestone source: "+source);
            }
        }
        assertEquals(96,skills);assertEquals(24,quests);assertEquals(11,totals);
        assertTrue("Starter and expanded goals must fit the server/client 200-definition limit",goals.size()+19<=200);
    }
}
