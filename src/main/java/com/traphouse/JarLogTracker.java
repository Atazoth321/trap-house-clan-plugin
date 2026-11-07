package com.traphouse;

import java.util.*;
import java.util.function.Predicate;
import net.runelite.api.Client;
import net.runelite.api.events.ScriptPreFired;
import net.runelite.api.gameval.VarbitID;

/** Reads obtained quantities rendered by the player's own collection log. */
final class JarLogTracker {
    private static final Map<Integer,String> JARS;
    static {
        Map<Integer,String> jars=new HashMap<>();
        int[] ids={12007,12885,12936,13245,13277,19701,21745,22106,23064,23525,24495,25521,25524,29786,32921,34030};
        String[] names={"dirt","sand","swamp","souls","miasma","darkness","stone","decay","chemicals","eyes","dreams","spirits","smoke","venom","feathers","light"};
        for(int i=0;i<ids.length;i++)jars.put(ids[i],"jar of "+names[i]);
        JARS=Collections.unmodifiableMap(jars);
    }
    private String player;
    private final Set<String> submitted=new HashSet<>(),pending=new LinkedHashSet<>();
    synchronized void reset(){player=null;submitted.clear();pending.clear();}
    private void character(String name) {
        String current=RankingTracker.normalize(name);
        if(!current.equals(player)){reset();player=current;}
    }
    synchronized void observe(Client client,String name,ScriptPreFired event,Predicate<RankingEvent> enqueue) {
        character(name);
        // Same collection-item renderer observed by ClanBrowserTracker. A POH host's log is not ours.
        if(event.getScriptId()!=4100||client.getVarbitValue(VarbitID.COLLECTION_POH_HOST_BOOK_OPEN)!=0)return;
        if(event.getScriptEvent()==null)return;
        Object[] args=event.getScriptEvent().getArguments();
        if(args==null||args.length<3||!(args[1] instanceof Integer)||!(args[2] instanceof Integer))return;
        int id=(Integer)args[1],quantity=(Integer)args[2];
        if(quantity<=0)return; // Unobtained slots still have an item ID.
        int mapped=client.getEnum(3721).getIntValue(id);
        String key=JARS.get(mapped>0?mapped:id);
        if(key!=null&&!submitted.contains(key))pending.add(key);
        flush(name,enqueue);
    }
    synchronized void flush(String name,Predicate<RankingEvent> enqueue) {
        character(name);
        Iterator<String> iterator=pending.iterator();
        while(iterator.hasNext()) {
            String key=iterator.next();
            if(enqueue.test(new RankingEvent(name,"COLLECTION",key))){submitted.add(key);iterator.remove();}
        }
    }
}
