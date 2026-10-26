package com.traphouse;
import com.google.gson.*;
import net.runelite.api.*;
import java.lang.reflect.Proxy;
import org.junit.Test;
import static org.junit.Assert.*;

public class ProgressTrackerTest {
    @Test public void recognizesDragonDefenderVariantsInEachSupportedContainer() {
        for(InventoryID location:new InventoryID[]{InventoryID.INVENTORY,InventoryID.EQUIPMENT,InventoryID.BANK}) {
            for(int defender:new int[]{12954,19722,20463,23597,24143,27008}) {
                Client client=itemClient(location,new Item(defender,1));
                assertEquals("Defender "+defender+" in "+location,Long.valueOf(1),ProgressTracker.read(client,"ITEM","12954"));
                assertEquals(Long.valueOf(0),ProgressTracker.read(client,"ITEM","6570"));
            }
        }
        assertEquals(Long.valueOf(0),ProgressTracker.read(itemClient(InventoryID.INVENTORY,new Item(6570,1)),"ITEM","12954"));
    }
    @Test public void recognizesFireCapeVariantsInEachSupportedContainer() {
        for(InventoryID location:new InventoryID[]{InventoryID.INVENTORY,InventoryID.EQUIPMENT,InventoryID.BANK}) {
            for(int cape:new int[]{6570,10566,20445,24223}) {
                Client client=itemClient(location,new Item(cape,1));
                assertEquals("Cape "+cape+" in "+location,Long.valueOf(1),ProgressTracker.read(client,"ITEM","6570"));
            }
        }
        assertEquals(Long.valueOf(0),ProgressTracker.read(itemClient(InventoryID.INVENTORY,new Item(1,1)),"ITEM","6570"));
    }
    private Client itemClient(InventoryID location,Item item) {
        ItemContainer container=(ItemContainer)Proxy.newProxyInstance(ItemContainer.class.getClassLoader(),new Class<?>[]{ItemContainer.class},(proxy,method,args)-> {
            if(method.getName().equals("getItems"))return new Item[]{item};
            throw new UnsupportedOperationException(method.getName());
        });
        return (Client)Proxy.newProxyInstance(Client.class.getClassLoader(),new Class<?>[]{Client.class},(proxy,method,args)-> {
            if(method.getName().equals("getItemContainer"))return args[0]==location?container:null;
            throw new UnsupportedOperationException(method.getName());
        });
    }
    private Client client() {
        return (Client)Proxy.newProxyInstance(Client.class.getClassLoader(),new Class<?>[]{Client.class},(proxy,method,args)-> {
            if(method.getName().equals("getTotalLevel"))return 2000;
            if(method.getName().equals("getVarbitValue"))return ((Integer)args[0])==14815?100:50;
            if(method.getName().equals("getRealSkillLevel"))return 99;
            throw new UnsupportedOperationException(method.getName());
        });
    }
    @Test public void readsConfiguredValuesWithoutCallingGameActions() {
        assertEquals(Long.valueOf(2000),ProgressTracker.read(client(),"TOTAL_LEVEL","total"));
        assertEquals(Long.valueOf(99),ProgressTracker.read(client(),"SKILL","ATTACK"));
        assertEquals(Long.valueOf(1),ProgressTracker.read(client(),"CA_TIER","4132"));
        assertNull(ProgressTracker.read(client(),"MANUAL","staff review"));
    }
    @Test public void skipsUnknownSourcesAndInvalidIdsRatherThanInventingProgress() {
        JsonArray definitions=new JsonArray();
        for(String source:new String[]{"VARBIT","UNKNOWN","MANUAL"}) {
            JsonObject goal=new JsonObject();goal.addProperty("source",source);goal.addProperty("sourceKey","-1");goal.addProperty("id",java.util.UUID.randomUUID().toString());definitions.add(goal);
        }
        assertEquals(0,ProgressTracker.collect(client(),"Member",definitions).getAsJsonObject("values").size());
    }
}
