package com.traphouse.server;

import com.google.gson.*;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class MilestoneRewardsTest {
    @Test public void increasedRewardsRemainModerateAndBelowColonel()throws Exception {
        long total=0;
        for(JsonElement entry:MilestoneCatalog.rewards()) {
            int old=entry.getAsJsonObject().get("points").getAsInt(),higher=MilestoneCatalog.boostedReward(old);
            assertTrue(higher>old);assertEquals(0,higher%5);total+=higher;
        }
        assertTrue(total>=6000);assertTrue(total<10000);
        assertEquals(60,MilestoneCatalog.boostedReward(40));
        assertEquals(375,MilestoneCatalog.boostedReward(250));
    }
    @Test public void rewardCoverageHasNoDuplicateOrUnconfiguredMilestones()throws Exception {
        Map<String,Integer> rewards=new HashMap<>();long total=0;
        for(JsonElement entry:MilestoneCatalog.rewards()) {
            JsonObject goal=entry.getAsJsonObject();String id=UUID.fromString(goal.get("id").getAsString()).toString();
            int points=goal.get("points").getAsInt();assertTrue(points>0);assertTrue(points<=100000);
            assertNull(rewards.put(id,points));total+=points;
        }
        for(JsonElement goal:MilestoneCatalog.read())assertNotNull(rewards.get(goal.getAsJsonObject().get("id").getAsString()));
        assertEquals(143,rewards.size());
        assertTrue("Completing all seeded milestones alone must not reach Sergeant",total<6000);
        assertEquals(4960,total);
        assertFalse("Pet tracking is outside this reward policy",rewards.containsKey("10000000-0000-4000-8000-000000000030"));
    }
    @Test public void successiveSkillGoalsIncreaseInValue()throws Exception {
        Map<String,Integer> rewards=new HashMap<>();
        for(JsonElement entry:MilestoneCatalog.rewards()) {JsonObject goal=entry.getAsJsonObject();rewards.put(goal.get("id").getAsString(),goal.get("points").getAsInt());}
        Map<String,Integer> previous=new HashMap<>();
        for(JsonElement entry:MilestoneCatalog.read()) {
            JsonObject goal=entry.getAsJsonObject();if(!goal.get("source").getAsString().equals("SKILL"))continue;
            String skill=goal.get("sourceKey").getAsString();int points=rewards.get(goal.get("id").getAsString());
            assertTrue(points>previous.getOrDefault(skill,0));previous.put(skill,points);
        }
    }
}
