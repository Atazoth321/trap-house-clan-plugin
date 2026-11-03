package com.traphouse.server;
import org.junit.Test;
import static org.junit.Assert.*;

public class GoalArtworkTest {
    @Test public void newMilestonesHaveExplicitItemSprites() {
        String[] keys={"QUIVER","MASORI_KIT","MENAPHITE_KIT","PURIFYING_SIGIL","ANCIENT_BLOOD_KIT","ALL_QUESTS","ALL_DIARIES","COLLECTION_COUNT"};
        int[] icons={28947,27372,27255,30793,28336,9813,19476,22711};
        for(int i=0;i<keys.length;i++)assertEquals(icons[i],GoalArtwork.itemId("MILESTONE",keys[i]));
        assertEquals(6570,GoalArtwork.itemId("ITEM","6570"));assertEquals(9813,GoalArtwork.itemId("QUEST","DRAGON_SLAYER_II"));
        assertEquals(-1,GoalArtwork.itemId("MILESTONE","unknown"));assertEquals(-1,GoalArtwork.itemId(null,null));
    }
}
