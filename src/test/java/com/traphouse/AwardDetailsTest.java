package com.traphouse;

import com.google.gson.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class AwardDetailsTest {
    @Test public void legacyPetsResolveWhileUnknownAwardsAndExplicitCategoriesArePreserved() {
        JsonArray sources=new JsonArray();JsonObject rule=new JsonObject();
        rule.addProperty("name","Baby mole");rule.addProperty("type","COLLECTION");rule.addProperty("key","baby mole");sources.add(rule);
        JsonObject award=new JsonObject();award.addProperty("title","Baby mole");award.addProperty("category","Misc");
        JsonObject pet=AwardDetails.enrich(award,sources);
        assertEquals("Pets",pet.get("category").getAsString());assertEquals(12646,AwardItemImages.itemId(pet));
        award.addProperty("category","Milestones");assertEquals("Milestones",AwardDetails.enrich(award,sources).get("category").getAsString());
        award.addProperty("title","Unknown reward");award.remove("category");
        assertFalse(AwardDetails.enrich(award,sources).has("category"));
        assertFalse(AwardDetails.enrich(award,sources).has("sourceKey"));
    }
}
