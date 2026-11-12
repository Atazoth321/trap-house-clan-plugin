package com.traphouse;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.runelite.api.*;
import net.runelite.api.gameval.DBTableID;

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
                if(maximum<=0)return questCompletion(client);
                return client.getVarpValue(VarPlayer.QUEST_POINTS)>=maximum?1L:0L;
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
    private static Long questCompletion(Client client) {
        // The maximum-QP variable is not always populated. Consult the live quest
        // table instead; its type distinguishes regular quests from miniquests.
        List<Integer> rows=client.getDBTableRows(DBTableID.Quest.ID);
        if(rows==null||rows.isEmpty()||rows.size()>512)return null;
        int reference=Quest.COOKS_ASSISTANT.getId();
        Integer type=questField(client,reference,DBTableID.Quest.COL_TYPE);
        Integer release=questField(client,reference,DBTableID.Quest.COL_RELEASE_TYPE);
        if(type==null||release==null||!rows.contains(reference))return null;
        Map<Integer,Quest> quests=new HashMap<>();
        for(Quest quest:Quest.values())quests.put(quest.getId(),quest);
        boolean incomplete=false,unknown=false;
        for(int row:rows) {
            Integer rowType=questField(client,row,DBTableID.Quest.COL_TYPE);
            Integer rowRelease=questField(client,row,DBTableID.Quest.COL_RELEASE_TYPE);
            if(rowType==null||rowRelease==null){unknown=true;continue;}
            if(!type.equals(rowType)||!release.equals(rowRelease))continue;
            Quest quest=quests.get(row);
            if(quest==null){unknown=true;continue;} // A new quest requires a matching API update.
            if(quest.getState(client)!=QuestState.FINISHED)incomplete=true;
        }
        if(incomplete)return 0L;
        return unknown?null:Long.valueOf(1);
    }
    private static Integer questField(Client client,int row,int column) {
        Object[] values=client.getDBTableField(row,column,0);
        return values!=null&&values.length==1&&values[0] instanceof Integer?(Integer)values[0]:null;
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
