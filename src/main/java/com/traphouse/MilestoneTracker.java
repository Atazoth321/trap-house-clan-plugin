package com.traphouse;

import net.runelite.api.*;

/** Passive, bounded observations for the additional one-time milestone rewards. */
final class MilestoneTracker {
    static final int[] DIARIES={4458,4459,4460,4461,4483,4484,4485,4486,
        4462,4463,4464,4465,4491,4492,4493,4494,4475,4476,4477,4478,
        3578,3599,3611,4566,7925,7926,7927,7928,4495,4496,4497,4498,
        4487,4488,4489,4490,4479,4480,4481,4482,4471,4472,4473,4474,4466,4467,4468,4469};
    static Long read(Client client,String key) {
        switch(key) {
            case "COLLECTION_COUNT":
                return client.getVarpValue(net.runelite.api.gameval.VarPlayerID.COLLECTION_COUNT_MAX)>0
                    ?(long)client.getVarpValue(net.runelite.api.gameval.VarPlayerID.COLLECTION_COUNT):null;
            case "ALL_QUESTS":
                int maximum=client.getVarbitValue(net.runelite.api.gameval.VarbitID.QP_MAX);
                return maximum<=0?null:client.getVarpValue(VarPlayer.QUEST_POINTS)>=maximum?1L:0L;
            case "ALL_DIARIES":
                for(int flag:DIARIES)if(client.getVarbitValue(flag)!=1)return 0L;
                return 1L;
            case "QUIVER":return owns(client,28826,28828,28947,28949,28951,28953,28955,28957,33524,33526,33528,33530);
            case "MASORI_KIT":return owns(client,27372,27374,27363,27365,27366,27359,27361,27376);
            case "MENAPHITE_KIT":return owns(client,27255,27253);
            case "PURIFYING_SIGIL":return owns(client,30793);
            case "ANCIENT_BLOOD_KIT":return owns(client,28336,28254,28256,28258);
            default:return null;
        }
    }
    private static long owns(Client client,int... ids) {
        for(InventoryID location:new InventoryID[]{InventoryID.INVENTORY,InventoryID.EQUIPMENT,InventoryID.BANK}) {
            ItemContainer container=client.getItemContainer(location);
            if(container==null)continue;
            for(Item item:container.getItems())if(item.getQuantity()>0)for(int id:ids)if(item.getId()==id)return 1L;
        }
        return 0L;
    }
}
