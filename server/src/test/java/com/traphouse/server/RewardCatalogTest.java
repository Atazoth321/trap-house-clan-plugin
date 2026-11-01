package com.traphouse.server;

import com.google.gson.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class RewardCatalogTest {
    @Test public void efficiencyCatalogUsesActualRatesAndSharedCapWithoutInventingSeparateAwards() {
        JsonArray cards=RewardCatalog.efficiencyCards("");
        assertEquals(2,cards.size());
        for(JsonElement value:cards) {
            JsonObject card=value.getAsJsonObject();
            assertEquals(EfficiencyRewards.WEEKLY_CAP,card.get("weeklyCap").getAsInt());
            assertEquals(card.get("unit").getAsString().equals("EHP")?EfficiencyRewards.EHP_RATE:EfficiencyRewards.EHB_RATE,card.get("points").getAsBigDecimal());
            assertFalse(card.has("received"));
        }
        assertEquals(1,RewardCatalog.efficiencyCards("bossing").size());
        assertEquals(0,RewardCatalog.efficiencyCards("clue").size());
    }
}
