package com.traphouse;
import com.google.gson.*;
import net.runelite.api.*;
import java.lang.reflect.Proxy;
import org.junit.Test;
import static org.junit.Assert.*;

public class ProgressTrackerTest {
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
