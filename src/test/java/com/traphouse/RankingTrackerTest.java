package com.traphouse;

import org.junit.Test;
import static org.junit.Assert.*;

public class RankingTrackerTest {
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
