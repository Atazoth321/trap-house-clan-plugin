package com.traphouse;

import java.lang.reflect.Proxy;
import java.util.*;
import net.runelite.api.*;
import net.runelite.api.events.ScriptPreFired;
import org.junit.Test;
import static org.junit.Assert.*;

public class JarLogTrackerTest {
    private interface Call {Object invoke(String method,Object[] args);}
    private static <T>T proxy(Class<T> type,Call call) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(),new Class<?>[]{type},(object,method,args)->call.invoke(method.getName(),args)));
    }
    private Client client(boolean host) {
        return proxy(Client.class,(method,args)->{
            if(method.equals("getVarbitValue"))return host?1:0;
            if(method.equals("getEnum"))return proxy(EnumComposition.class,(operation,values)->{
                if(operation.equals("getIntValue"))return (Integer)values[0]==9999?12007:-1;
                throw new AssertionError(operation);
            });
            throw new AssertionError("Unexpected game action: "+method);
        });
    }
    private ScriptPreFired item(int script,Object... args) {
        ScriptPreFired event=new ScriptPreFired(script);
        event.setScriptEvent(proxy(ScriptEvent.class,(method,unused)->{
            if(method.equals("getArguments"))return args;
            throw new AssertionError(method);
        }));return event;
    }
    @Test public void openingOwnedJarSlotsRecognizesAllSixteenWithoutNewUnlockMessages() {
        int[] ids={12007,12885,12936,13245,13277,19701,21745,22106,23064,23525,24495,25521,25524,29786,32921,34030};
        JarLogTracker tracker=new JarLogTracker();List<RankingEvent> events=new ArrayList<>();
        for(int id:ids)for(int repeat=0;repeat<2;repeat++)tracker.observe(client(false),"Member",item(4100,4100,id,1),event->{events.add(event);return true;});
        assertEquals(16,events.size());Set<String> keys=new HashSet<>();
        for(RankingEvent event:events){assertEquals("COLLECTION",event.type);assertEquals("Member",event.player);assertTrue(keys.add(event.key));}
        assertTrue(keys.contains("jar of eyes"));assertTrue(keys.contains("jar of light"));
    }
    @Test public void unownedSlotsOtherPlayersLogsAndUnrelatedScriptsDoNotQualify() {
        JarLogTracker tracker=new JarLogTracker();List<RankingEvent> events=new ArrayList<>();
        for(ScriptPreFired event:Arrays.asList(item(4100,4100,12007,0),item(4100,4100,12007,-1),
            item(4100,4100,2132,1),item(4099,4099,12007,1),item(4100,4100,"12007",1),item(4100,4100,12007)))
            tracker.observe(client(false),"Member",event,e->{events.add(e);return true;});
        tracker.observe(client(true),"Member",item(4100,4100,12007,1),e->{events.add(e);return true;});
        assertTrue(events.isEmpty());
    }
    @Test public void canonicalIdsQueueRetryResetAndCharacterChangesWork() {
        JarLogTracker tracker=new JarLogTracker();List<RankingEvent> events=new ArrayList<>();
        tracker.observe(client(false),"Member",item(4100,4100,9999,3),e->false);
        tracker.flush("Member",e->{events.add(e);return true;});
        tracker.flush("Member",e->{events.add(e);return true;});
        assertEquals(1,events.size());assertEquals("jar of dirt",events.get(0).key);
        tracker.reset();tracker.observe(client(false),"Member",item(4100,4100,12007,1),e->{events.add(e);return true;});
        assertEquals(2,events.size());
        tracker.observe(client(false),"Member",item(4100,4100,23525,1),e->false);
        tracker.flush("Other",e->{events.add(e);return true;});assertEquals(2,events.size());
        tracker.observe(client(false),"Other",item(4100,4100,12007,1),e->{events.add(e);return true;});
        assertEquals("Other",events.get(2).player);
    }
}
