package com.traphouse;

import org.junit.Test;
import static org.junit.Assert.*;

public class RankingTrackerTest {
    @Test public void clueUniquesComeFromCasketContentsAndNpcDropsUseEncounterMetadata() {
        net.runelite.client.plugins.loottracker.LootReceived loot=new net.runelite.client.plugins.loottracker.LootReceived(
            "Clue Scroll (Medium)",0,net.runelite.http.api.loottracker.LootRecordType.EVENT,
            java.util.Arrays.asList(new net.runelite.client.game.ItemStack(2577,1),new net.runelite.client.game.ItemStack(2577,1)),1,null);
        java.util.List<RankingEvent> result=RankingTracker.containerLoot("Member",loot);
        assertEquals(2,result.size());assertEquals("clue:medium:2577",result.get(1).key);assertEquals("LOOT",result.get(1).type);
        loot.setType(net.runelite.http.api.loottracker.LootRecordType.PLAYER);
        assertTrue(RankingTracker.containerLoot("Member",loot).isEmpty());
        loot.setType(net.runelite.http.api.loottracker.LootRecordType.NPC);loot.setMetadata(9433);
        assertEquals("9433:2577",RankingTracker.containerLoot("Member",loot).get(0).key);
        loot.setMetadata(java.util.Collections.singletonMap("id",14012));
        assertEquals("14012:2577",RankingTracker.containerLoot("Member",loot).get(0).key);
        loot.setMetadata(12593);assertTrue(RankingTracker.containerLoot("Member",loot).isEmpty()); // Titan pet.
        loot.setMetadata(null);assertTrue(RankingTracker.containerLoot("Member",loot).isEmpty());
    }
    @Test public void clueMilestonesUseExactGameCompletionCountsAndCatchUpOncePerThreshold() {
        assertTrue(RankingTracker.clueMilestones("Member","You have completed 9 easy Treasure Trails.").isEmpty());
        assertEquals("clue milestone:easy:10",RankingTracker.clueMilestones("Member","You have completed <col=ff0000>10</col> easy Treasure Trails.").get(0).key);
        assertEquals(1,RankingTracker.clueMilestones("Member","You have completed 49 hard Treasure Trails.").size());
        assertEquals(2,RankingTracker.clueMilestones("Member","You have completed 50 hard Treasure Trails.").size());
        java.util.List<RankingEvent> events=RankingTracker.clueMilestones("Member","You have completed 101 master Treasure Trails.");
        assertEquals(3,events.size());assertEquals("clue milestone:master:100",events.get(2).key);
        assertTrue(RankingTracker.clueMilestones("Member","Player: You have completed 100 master Treasure Trails.").isEmpty());
        assertTrue(RankingTracker.clueMilestones("Member","You have completed 100 unknown Treasure Trails.").isEmpty());
        assertTrue(RankingTracker.clueMilestones("Member","You have completed 99999999999999999999999999999 hard Treasure Trails.").isEmpty());
    }
    @Test public void newEncounterContainersAndCluesUseOnlyPersonalLootEvents() {
        net.runelite.client.plugins.loottracker.LootReceived loot=new net.runelite.client.plugins.loottracker.LootReceived(
            "Lunar Chest",0,net.runelite.http.api.loottracker.LootRecordType.EVENT,
            java.util.Arrays.asList(new net.runelite.client.game.ItemStack(29004,1),new net.runelite.client.game.ItemStack(29004,1)),1,null);
        assertEquals(1,RankingTracker.containerLoot("Member",loot).size());
        assertEquals("chest:lunar chest:29004",RankingTracker.containerLoot("Member",loot).get(0).key);
        for(String tier:new String[]{"Beginner","Easy","Medium","Hard","Elite","Master"}) {
            loot.setName("Clue Scroll ("+tier+")");
            RankingEvent event=RankingTracker.containerLoot("Member",loot).get(0);
            assertEquals("COLLECTION",event.type);assertEquals("clue completion:"+tier.toLowerCase(java.util.Locale.ROOT),event.key);
        }
        loot.setType(net.runelite.http.api.loottracker.LootRecordType.NPC);
        assertTrue(RankingTracker.containerLoot("Member",loot).isEmpty());
        loot.setType(net.runelite.http.api.loottracker.LootRecordType.EVENT);loot.setItems(java.util.Collections.emptyList());
        assertTrue(RankingTracker.containerLoot("Member",loot).isEmpty());
        assertTrue(RankingTracker.serverLootNpc(9021));assertTrue(RankingTracker.serverLootNpc(9038));
        assertTrue(RankingTracker.serverLootNpc(13606));assertTrue(RankingTracker.serverLootNpc(13668));
        assertFalse(RankingTracker.serverLootNpc(12123)); // Quest Hunllef.
        assertFalse(RankingTracker.serverLootNpc(415));
        assertNull(RankingTracker.chat("Member","You have completed 1 master Treasure Trail."));
    }
    @Test public void raidLootUsesPersonalContainerItemsAndIgnoresNpcCallbacks() {
        java.util.List<net.runelite.client.game.ItemStack> items=java.util.Arrays.asList(
            new net.runelite.client.game.ItemStack(27277,1),new net.runelite.client.game.ItemStack(27277,1));
        net.runelite.client.plugins.loottracker.LootReceived event=new net.runelite.client.plugins.loottracker.LootReceived(
            "Tombs of Amascut",0,net.runelite.http.api.loottracker.LootRecordType.EVENT,items,1,null);
        java.util.List<RankingEvent> results=RankingTracker.containerLoot("Member",event);
        assertEquals(1,results.size());assertEquals("LOOT",results.get(0).type);
        assertEquals("chest:tombs of amascut:27277",results.get(0).key);
        event.setType(net.runelite.http.api.loottracker.LootRecordType.NPC);
        assertTrue(RankingTracker.containerLoot("Member",event).isEmpty());
        event.setType(net.runelite.http.api.loottracker.LootRecordType.EVENT);event.setName("Unknown chest");
        assertTrue(RankingTracker.containerLoot("Member",event).isEmpty());
        event.setName("Barrows");event.setItems(java.util.Collections.singletonList(new net.runelite.client.game.ItemStack(4708,1)));
        assertEquals("chest:barrows:4708",RankingTracker.containerLoot("Member",event).get(0).key);
    }
    @Test public void petCollectionUnlockRetainsExactIdentity() {
        RankingEvent event=RankingTracker.chat("Member","New item added to your collection log: <col=ff0000>Pet kraken</col>");
        assertEquals("COLLECTION",event.type);assertEquals("pet kraken",event.key);
        assertNull(RankingTracker.chat("Member","You have a funny feeling like you're being followed."));
    }
    @Test public void parsesOnlySupportedSystemMessages() {
        RankingEvent item = RankingTracker.chat("Member", "New item added to your collection log: <col=ff0000>Abyssal whip</col>");
        assertEquals("COLLECTION", item.type);
        assertEquals("abyssal whip", item.key);
        RankingEvent task = RankingTracker.chat("Member", "Congratulations, you've completed an Elite combat task: Perfect kill.");
        assertEquals("COMBAT", task.type);
        assertEquals("perfect kill", task.key);
        assertEquals("perfect kill", RankingTracker.chat("Member", "Congratulations, you've completed an elite combat task: Perfect kill (4 points).").key);
        assertNull(RankingTracker.chat("Member", "Member received a drop: Abyssal whip"));
        assertNull(RankingTracker.chat("Member", "hello"));
    }

    @Test public void uniqueObservationIdsAndStableRetryIdentity() {
        RankingEvent first = new RankingEvent("Member", "LOOT", "1:2");
        assertNotEquals(first.id, new RankingEvent("Member", "LOOT", "1:2").id);
        assertEquals(first.id, first.id);
    }

    @Test public void rejectsInsecureOrAmbiguousEndpoints() {
        assertEquals("https://clan.example", RankingClient.endpoint("https://clan.example/"));
        for (String value : new String[] {"http://clan.example", "https://user:pass@clan.example", "https://clan.example/path", "https://clan.example?token=x", "https://clan.example#x"}) {
            try { RankingClient.endpoint(value); fail(value); } catch (IllegalArgumentException expected) { }
        }
    }
}
