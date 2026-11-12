package com.traphouse;

import com.google.gson.*;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import net.runelite.api.*;
import net.runelite.api.gameval.DBTableID;
import net.runelite.api.gameval.VarPlayerID;
import org.junit.Test;
import static org.junit.Assert.*;

public class MilestoneTrackerTest {
    private Client client(int max,int points,int missing,int item) {
        ItemContainer container=(ItemContainer)Proxy.newProxyInstance(ItemContainer.class.getClassLoader(),new Class<?>[]{ItemContainer.class},(p,m,a)->{
            if(m.getName().equals("getItems"))return new Item[]{new Item(item,1)};
            throw new AssertionError(m.getName());
        });
        return (Client)Proxy.newProxyInstance(Client.class.getClassLoader(),new Class<?>[]{Client.class},(p,m,a)->{
            if(m.getName().equals("getVarbitValue"))return (int)a[0]==1782?max:(int)a[0]==missing?0:1;
            if(m.getName().equals("getVarpValue"))return (int)a[0]==VarPlayerID.COLLECTION_COUNT_MAX?max:points;
            if(m.getName().equals("getItemContainer"))return a[0]==InventoryID.BANK?container:null;
            if(m.getName().equals("getDBTableRows"))return null;
            throw new AssertionError("Unexpected game call: "+m.getName());
        });
    }
    @Test public void questCompletionUsesLiveMaximumAndUnknownStaysUnknown() {
        assertNull(MilestoneTracker.read(client(0,0,-1,0),"ALL_QUESTS"));
        assertEquals(Long.valueOf(0),MilestoneTracker.read(client(350,349,-1,0),"ALL_QUESTS"));
        assertEquals(Long.valueOf(1),MilestoneTracker.read(client(350,350,-1,0),"ALL_QUESTS"));
        assertEquals(Long.valueOf(0),MilestoneTracker.read(client(351,350,-1,0),"ALL_QUESTS"));
    }
    @Test public void everyDiaryFlagIsRequired() {
        assertEquals(48,MilestoneTracker.DIARIES.length);
        assertEquals(Long.valueOf(1),MilestoneTracker.read(client(350,350,-1,0),"ALL_DIARIES"));
        for(int flag:MilestoneTracker.DIARIES)assertEquals(Long.valueOf(0),MilestoneTracker.read(client(350,350,flag,0),"ALL_DIARIES"));
    }
    @Test public void missingQuestPointMaximumFallsBackToReleasedRegularQuests() {
        assertEquals(Long.valueOf(1),MilestoneTracker.read(questClient(-1,false,false),"ALL_QUESTS"));
        assertEquals(Long.valueOf(0),MilestoneTracker.read(questClient(Quest.DEMON_SLAYER.getId(),false,false),"ALL_QUESTS"));
        // An unfinished miniquest does not block the normal quest cape milestone.
        assertEquals(Long.valueOf(1),MilestoneTracker.read(questClient(Quest.ALFRED_GRIMHANDS_BARCRAWL.getId(),false,false),"ALL_QUESTS"));
    }
    @Test public void questFallbackDoesNotAwardWhenCacheOrNewQuestIsUnknown() {
        assertNull(MilestoneTracker.read(questClient(-1,true,false),"ALL_QUESTS"));
        assertNull(MilestoneTracker.read(questClient(-1,false,true),"ALL_QUESTS"));
    }
    private Client questClient(int incomplete,boolean unknownQuest,boolean missingField) {
        int[] lastQuest={-1};
        return (Client)Proxy.newProxyInstance(Client.class.getClassLoader(),new Class<?>[]{Client.class},(p,m,a)->{
            switch(m.getName()) {
                case "getVarbitValue":return 0;
                case "getDBTableRows":return unknownQuest
                    ?Arrays.asList(Quest.COOKS_ASSISTANT.getId(),Quest.DEMON_SLAYER.getId(),999999)
                    :Arrays.asList(Quest.COOKS_ASSISTANT.getId(),Quest.DEMON_SLAYER.getId(),Quest.ALFRED_GRIMHANDS_BARCRAWL.getId());
                case "getDBTableField":
                    if(missingField)return null;
                    return new Object[]{(int)a[1]==DBTableID.Quest.COL_TYPE&&(int)a[0]==Quest.ALFRED_GRIMHANDS_BARCRAWL.getId()?2:1};
                case "runScript":lastQuest[0]=(int)((Object[])a[0])[1];return null;
                case "getIntStack":return new int[]{lastQuest[0]==incomplete?1:2};
                default:throw new AssertionError("Unexpected game call: "+m.getName());
            }
        });
    }
    @Test public void itemMilestonesAcceptAppliedRewardsButRejectComponentsAndOrdinaryGear() {
        String[] keys={"QUIVER","MASORI_KIT","MENAPHITE_KIT","PURIFYING_SIGIL","ANCIENT_BLOOD_KIT"};
        int[][] accepted={{28947,28955,33530},{27372,27374,27365},{27255,27253},{30793},{28336,28254,28258}};
        for(int i=0;i<keys.length;i++) {
            for(int item:accepted[i])assertEquals(keys[i]+" / "+item,Long.valueOf(1),MilestoneTracker.read(client(0,0,-1,item),keys[i]));
            for(int item:new int[]{30783,30785,27251,26382,22109})assertEquals(Long.valueOf(0),MilestoneTracker.read(client(0,0,-1,item),keys[i]));
        }
    }
    @Test public void collectionTotalsAreUnknownUntilLoadedAndGoalsBeyondTwoHundredAreIncluded() {
        assertNull(MilestoneTracker.read(client(0,0,-1,0),"COLLECTION_COUNT"));
        assertEquals(Long.valueOf(1200),MilestoneTracker.read(client(1600,1200,-1,0),"COLLECTION_COUNT"));
        JsonArray definitions=new JsonArray();for(int i=0;i<214;i++){
            JsonObject goal=new JsonObject();goal.addProperty("id",new java.util.UUID(0,i).toString());goal.addProperty("source","MILESTONE");goal.addProperty("sourceKey","COLLECTION_COUNT");definitions.add(goal);
        }
        assertEquals(214,ProgressTracker.collect(client(1600,1200,-1,0),"Member",definitions).getAsJsonObject("values").size());
    }
}
