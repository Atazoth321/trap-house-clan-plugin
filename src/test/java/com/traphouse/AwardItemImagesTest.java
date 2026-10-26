package com.traphouse;

import com.google.gson.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class AwardItemImagesTest {
    private JsonObject entry(String text){return new Gson().fromJson(text,JsonObject.class);}
    @Test public void lootIdentityUsesActualItemIdAndCollectionUsesExactRecordedName(){
        assertEquals(4151,AwardItemImages.itemId(entry("{sourceType:'LOOT',sourceKey:'415:4151'}")));
        assertEquals(27277,AwardItemImages.itemId(entry("{sourceType:'LOOT',sourceKey:'chest:tombs of amascut:27277'}")));
        assertEquals("baby mole",AwardItemImages.itemName(entry("{sourceType:'COLLECTION',sourceKey:'baby mole',category:'Pets',title:'Pet award'}")));
        assertEquals("Abyssal whip",AwardItemImages.itemName(entry("{category:'Drops',title:'Abyssal whip'}")));
        assertEquals("",AwardItemImages.itemName(entry("{category:'Events',title:'Boss event'}")));
    }
    @Test public void malformedOrNonItemKeysDoNotSelectAnUnrelatedItem(){
        for(String key:new String[]{"4151","1:-2","1:9999999999999","random","1:2:3"})assertEquals(-1,AwardItemImages.itemId(entry("{sourceType:'LOOT',sourceKey:'"+key+"'}")));
        assertEquals(-1,AwardItemImages.itemId(entry("{sourceType:'COMBAT',sourceKey:'1:4151'}")));
    }
}
