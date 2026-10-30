package com.traphouse;

import com.google.gson.*;
import net.runelite.api.*;
import net.runelite.client.game.ItemVariationMapping;

/** Reads only explicitly configured progress probes, on the game thread. Never runs scripts or opens interfaces. */
final class ProgressTracker {
    static JsonObject collect(Client client,String player,JsonArray definitions) {
        JsonObject body=new JsonObject(),values=new JsonObject();body.addProperty("player",player);
        int count=0;
        for(JsonElement element:definitions) {
            if(count++>=200)break;
            JsonObject goal=element.getAsJsonObject();
            try {
                String source=goal.get("source").getAsString(),key=goal.get("sourceKey").getAsString();
                Long value=read(client,source,key);
                if(value!=null&&value>=0)values.addProperty(goal.get("id").getAsString(),value);
            }catch(RuntimeException unavailable){ /* Unavailable cache data stays unknown, never marked completed. */ }
        }
        body.add("values",values);return body;
    }

    static Long read(Client client,String source,String key) {
        switch(source) {
            case "VARBIT":return (long)client.getVarbitValue(id(key));
            case "VARP":return (long)client.getVarpValue(id(key));
            case "TOTAL_LEVEL":return (long)client.getTotalLevel();
            case "SKILL":return (long)client.getRealSkillLevel(Skill.valueOf(key));
            case "QUEST":return Quest.valueOf(key).getState(client)==QuestState.FINISHED?1L:0L;
            case "CA_TIER":
                int threshold=client.getVarbitValue(id(key));
                if(threshold<=0)return null;
                return client.getVarbitValue(net.runelite.api.gameval.VarbitID.CA_POINTS)>=threshold?1L:0L;
            case "ITEM":
                int item=id(key);long quantity=0;
                for(InventoryID inventory:new InventoryID[]{InventoryID.INVENTORY,InventoryID.EQUIPMENT,InventoryID.BANK}) {
                    ItemContainer container=client.getItemContainer(inventory);
                    if(container!=null)for(Item entry:container.getItems()) {
                        if(matchesItem(item,entry.getId()))quantity+=entry.getQuantity();
                    }
                }
                return Math.min(Integer.MAX_VALUE,quantity);
            default:return null;
        }
    }
    private static boolean matchesItem(int required,int observed) {
        if(required!=6570&&required!=12954)return observed==required;
        // Normalize both sides: some families use a broken item as their canonical ID.
        int family=ItemVariationMapping.map(observed);
        if(family==ItemVariationMapping.map(required))return true;
        if(required==6570)return family==ItemVariationMapping.map(21295)
            ||family==ItemVariationMapping.map(21285); // Infernal max cape variants.
        return family==ItemVariationMapping.map(22322)
            ||family==ItemVariationMapping.map(27550); // Ghommal's Avernic variants.
    }
    private static int id(String key){int value=Integer.parseInt(key);if(value<0||value>1000000)throw new IllegalArgumentException("Invalid game ID");return value;}
}
