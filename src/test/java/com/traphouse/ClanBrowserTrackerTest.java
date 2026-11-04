package com.traphouse;
import com.google.gson.*;
import java.lang.reflect.Proxy;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import net.runelite.api.*;
import net.runelite.api.events.ScriptPreFired;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.gameval.VarbitID;
import org.junit.Test;
import static org.junit.Assert.*;

public class ClanBrowserTrackerTest {
    private interface Call {Object invoke(String method,Object[] args);}
    private static <T>T proxy(Class<T> type,Call call){return type.cast(Proxy.newProxyInstance(type.getClassLoader(),new Class<?>[]{type},(object,method,args)->call.invoke(method.getName(),args)));}
    private final AtomicInteger tick=new AtomicInteger(1);private final AtomicBoolean host=new AtomicBoolean();
    private Client client(){return client(4);}
    private Client client(int accountType){return proxy(Client.class,(method,args)->{
        switch(method) {
            case "getEnum":int id=(Integer)args[0];return proxy(EnumComposition.class,(operation,values)->{if(operation.equals("getIntValue"))return -1;if(operation.equals("getIntVals"))return id==2102?new int[]{471}:id==500?new int[]{201,202}:new int[]{11286};throw new AssertionError(operation);});
            case "getStructComposition":int struct=(Integer)args[0];return proxy(StructComposition.class,(operation,values)->{if(operation.equals("getIntValue"))return struct==471?500:600+struct;if(operation.equals("getStringValue"))return struct==201?"Vorkath":"Zulrah";throw new AssertionError(operation);});
            case "getVarpValue":int varp=(Integer)args[0];return varp==VarPlayerID.COLLECTION_COUNT_MAX?1600:varp==VarPlayerID.COLLECTION_COUNT?100:varp==1691?500:varp==1518?900:0;
            case "getVarbitValue":int varbit=(Integer)args[0];return varbit==VarbitID.IRONMAN?accountType:varbit==VarbitID.COLLECTION_POH_HOST_BOOK_OPEN?(host.get()?1:0):varbit==VarbitID.CA_POINTS?100:0;
            case "getAccountType":throw new AssertionError("Deprecated account type loses unranked GIM");
            case "getOverallExperience":return 5_000_000_000L;
            case "getTickCount":return tick.get();
            default:throw new AssertionError("Tracker must not perform game actions: "+method);
        }
    });}
    private static ScriptPreFired item(int count){ScriptPreFired event=new ScriptPreFired(4100);event.setScriptEvent(proxy(ScriptEvent.class,(method,args)->{if(method.equals("getArguments"))return new Object[]{4100,11286,count};throw new AssertionError(method);}));return event;}
    @Test public void accountTypesIncludeUnrankedGroupIronmen() {
        String[] expected={"NORMAL","IRONMAN","ULTIMATE_IRONMAN","HARDCORE_IRONMAN","GROUP_IRONMAN","HARDCORE_GROUP_IRONMAN","GROUP_IRONMAN"};
        for(int type=0;type<expected.length;type++) {
            assertEquals(expected[type],new ClanBrowserTracker().collect(client(type),"Member").getAsJsonObject("stats").get("accountType").getAsString());
        }
        assertNull(ClanBrowserTracker.accountType(7));
    }
    @Test public void passiveCollectionCaptureIgnoresHostBooksAndNeverInventsUnseenItems() {
        Client client=client();ClanBrowserTracker tracker=new ClanBrowserTracker();tracker.tick(client);JsonObject initial=tracker.collect(client,"Member");
        assertEquals("GROUP_IRONMAN",initial.getAsJsonObject("stats").get("accountType").getAsString());assertEquals(5_000_000_000L,initial.getAsJsonObject("stats").get("xp").getAsLong());assertEquals(2,initial.getAsJsonArray("pages").size());assertFalse(initial.getAsJsonArray("pages").get(0).getAsJsonObject().getAsJsonArray("items").get(0).getAsJsonObject().has("quantity"));
        host.set(true);tracker.item(client,item(1));assertFalse(tracker.collect(client,"Member").getAsJsonArray("pages").get(0).getAsJsonObject().getAsJsonArray("items").get(0).getAsJsonObject().has("quantity"));
        host.set(false);tracker.item(client,item(1));assertEquals(1,tracker.collect(client,"Member").getAsJsonArray("pages").get(0).getAsJsonObject().getAsJsonArray("items").get(0).getAsJsonObject().get("quantity").getAsInt());
        tracker.reset();tracker.tick(client);assertFalse(tracker.collect(client,"Other").getAsJsonArray("pages").get(0).getAsJsonObject().getAsJsonArray("items").get(0).getAsJsonObject().has("quantity"));
    }
    @Test public void duplicateDropsOnlyUpdateTheMatchingSourceAfterItsCounterSettles() {
        Client client=client();ClanBrowserTracker tracker=new ClanBrowserTracker();tracker.tick(client);tracker.loot(client,"Vorkath",Collections.singletonList(new net.runelite.client.game.ItemStack(11286,1)));
        tick.set(2);tracker.tick(client);assertFalse(tracker.collect(client,"Member").getAsJsonArray("pages").get(0).getAsJsonObject().has("lastDropCounter"));
        tick.set(3);tracker.tick(client);JsonArray pages=tracker.collect(client,"Member").getAsJsonArray("pages");assertEquals(500,pages.get(0).getAsJsonObject().get("lastDropCounter").getAsInt());assertFalse(pages.get(1).getAsJsonObject().has("lastDropCounter"));
    }
}
