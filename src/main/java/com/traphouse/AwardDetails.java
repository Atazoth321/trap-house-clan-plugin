package com.traphouse;

import com.google.gson.*;

/** Enrich older confirmed notifications using server-provided reward definitions. */
final class AwardDetails {
    static JsonObject enrich(JsonObject award, JsonArray sources) {
        JsonObject result=award.deepCopy();
        String category=RankingPanel.string(result,"category","");
        boolean legacy=category.isEmpty()||category.equals("Misc");
        String title=CelebrationOverlay.clean(RankingPanel.string(award,"title",""));
        for(JsonElement element:sources) {
            JsonObject rule=element.getAsJsonObject();
            if(!title.equalsIgnoreCase(CelebrationOverlay.clean(RankingPanel.string(rule,"name",""))))continue;
            String type=RankingPanel.string(rule,"type",""),key=RankingPanel.string(rule,"key","");
            if(!result.has("sourceType")||result.get("sourceType").isJsonNull())result.addProperty("sourceType",type);
            if(!result.has("sourceKey")||result.get("sourceKey").isJsonNull())result.addProperty("sourceKey",key);
            if(legacy) {
                if(type.equals("LOOT"))result.addProperty("category","Drops");
                else if(type.equals("COLLECTION")&&PetTracker.itemId(key)>0)result.addProperty("category","Pets");
            }
            break;
        }
        return result;
    }
    private AwardDetails(){}
}
