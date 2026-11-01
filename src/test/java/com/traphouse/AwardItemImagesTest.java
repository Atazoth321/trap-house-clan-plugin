package com.traphouse;

import com.google.gson.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class AwardItemImagesTest {
    @Test public void existingPetReceiptsUseCanonicalInGamePetSprites() {
        assertEquals(12655,AwardItemImages.itemId(entry("{sourceType:'COLLECTION',sourceKey:'pet kraken',category:'Pets'}")));
        assertEquals(27352,AwardItemImages.itemId(entry("{sourceType:'COLLECTION',sourceKey:\"tumeken's guardian\",category:'Pets'}")));
    }
    @Test public void expandedRewardsUseCasketSpritesOnlyForMilestonesAndItemSpritesForUniques() {
        assertEquals(2577,AwardItemImages.itemId(entry("{sourceType:'LOOT',sourceKey:'clue:medium:2577'}")));
        assertEquals(20014,AwardItemImages.itemId(entry("{sourceType:'LOOT',sourceKey:'clue:master:20014'}")));
        assertEquals(3481,AwardItemImages.itemId(entry("{sourceType:'LOOT',sourceKey:'clue:hard:3481'}")));
        assertEquals(20544,AwardItemImages.itemId(entry("{sourceType:'COLLECTION',sourceKey:'clue milestone:hard:50'}")));
        assertEquals(19836,AwardItemImages.itemId(entry("{sourceType:'COLLECTION',sourceKey:'clue milestone:master:100'}")));
        assertEquals(30626,AwardItemImages.itemId(entry("{sourceType:'LOOT',sourceKey:'12596:30626'}")));
        assertEquals(30070,AwardItemImages.itemId(entry("{sourceType:'LOOT',sourceKey:'14012:30070'}")));
        assertEquals(24511,AwardItemImages.itemId(entry("{sourceType:'LOOT',sourceKey:'9433:24511'}")));
    }
    @Test public void newRewardsResolveRealItemSpritesIncludingEachClueCasket() {
        assertEquals(29004,AwardItemImages.itemId(entry("{sourceType:'LOOT',sourceKey:'chest:lunar chest:29004'}")));
        assertEquals(23956,AwardItemImages.itemId(entry("{sourceType:'LOOT',sourceKey:'9021:23956'}")));
        assertEquals(29580,AwardItemImages.itemId(entry("{sourceType:'LOOT',sourceKey:'13599:29580'}")));
        assertEquals(29799,AwardItemImages.itemId(entry("{sourceType:'LOOT',sourceKey:'13668:29799'}")));
        String[] tiers={"beginner","easy","medium","hard","elite","master"};int[] ids={23245,20546,20545,20544,20543,19836};
        for(int i=0;i<tiers.length;i++)assertEquals(ids[i],AwardItemImages.itemId(entry("{sourceType:'COLLECTION',sourceKey:'clue completion:"+tiers[i]+"'}")));
    }
    private JsonObject entry(String text){return new Gson().fromJson(text,JsonObject.class);}
    @Test public void lootIdentityUsesActualItemIdAndCollectionUsesExactRecordedName(){
        assertEquals(4151,AwardItemImages.itemId(entry("{sourceType:'LOOT',sourceKey:'415:4151'}")));
        assertEquals(27277,AwardItemImages.itemId(entry("{sourceType:'LOOT',sourceKey:'chest:tombs of amascut:27277'}")));
        assertEquals(4708,AwardItemImages.itemId(entry("{sourceType:'LOOT',sourceKey:'chest:barrows:4708'}")));
        assertEquals("baby mole",AwardItemImages.itemName(entry("{sourceType:'COLLECTION',sourceKey:'baby mole',category:'Pets',title:'Pet award'}")));
        assertEquals("Abyssal whip",AwardItemImages.itemName(entry("{category:'Drops',title:'Abyssal whip'}")));
        assertEquals("",AwardItemImages.itemName(entry("{category:'Events',title:'Boss event'}")));
    }
    @Test public void malformedOrNonItemKeysDoNotSelectAnUnrelatedItem(){
        for(String key:new String[]{"4151","1:-2","1:9999999999999","random","1:2:3"})assertEquals(-1,AwardItemImages.itemId(entry("{sourceType:'LOOT',sourceKey:'"+key+"'}")));
        assertEquals(-1,AwardItemImages.itemId(entry("{sourceType:'COMBAT',sourceKey:'1:4151'}")));
    }
}
