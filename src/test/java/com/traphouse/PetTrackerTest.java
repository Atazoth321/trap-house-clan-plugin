package com.traphouse;

import java.lang.reflect.Proxy;
import java.util.*;
import net.runelite.api.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class PetTrackerTest {
    @Test public void existingInventoryAndBankPetsUseCanonicalCollectionRewardKeys() {
        PetTracker tracker=new PetTracker();List<RankingEvent> events=new ArrayList<>();
        Client client=client(new Item[]{new Item(12655,1),new Item(27383,1)},new Item[]{new Item(12655,1),new Item(13320,1)});
        tracker.scan(client,"Member",event->{events.add(event);return true;});
        Set<String> keys=new HashSet<>();for(RankingEvent event:events){assertEquals("COLLECTION",event.type);assertEquals("Member",event.player);keys.add(event.key);}
        assertEquals(new HashSet<>(Arrays.asList("pet kraken","tumeken's guardian","heron")),keys);
        tracker.scan(client,"Member",event->{events.add(event);return true;});assertEquals(3,events.size());
    }
    @Test public void transformedFormsShareOneRewardAndPlaceholdersAndOrdinaryItemsDoNotQualify() {
        List<RankingEvent> events=new ArrayList<>();
        new PetTracker().scan(client(new Item[]{new Item(13320,1),new Item(25600,1),new Item(3695,1),new Item(5608,1),new Item(12655,0),new Item(-1,0)},null),"Member",event->{events.add(event);return true;});
        assertEquals(1,events.size());assertEquals("heron",events.get(0).key);
        assertEquals("beaver",PetTracker.key(28670));assertNull(PetTracker.key(5608));
        assertEquals("jal-nib-rek",PetTracker.key(22319));assertEquals("pet dark core",PetTracker.key(22318));
        assertEquals("lil' zik",PetTracker.key(25752));assertEquals("rocky",PetTracker.key(24849));
    }
    @Test public void missingBankIsUnknownAndLaterOpeningItFindsThePet() {
        PetTracker tracker=new PetTracker();List<RankingEvent> events=new ArrayList<>();
        tracker.scan(client(null,null),"Member",event->{events.add(event);return true;});assertTrue(events.isEmpty());
        tracker.scan(client(null,new Item[]{new Item(12655,1)}),"Member",event->{events.add(event);return true;});
        assertEquals(1,events.size());assertEquals("pet kraken",events.get(0).key);
    }
    @Test public void aFullSubmissionQueueDoesNotLoseThePetAndResetOrCharacterChangeRescans() {
        PetTracker tracker=new PetTracker();List<RankingEvent> events=new ArrayList<>();Client client=client(new Item[]{new Item(12655,1)},null);
        tracker.scan(client,"Member",event->false);
        tracker.scan(client,"Member",event->{events.add(event);return true;});assertEquals(1,events.size());
        tracker.reset();tracker.scan(client,"Member",event->{events.add(event);return true;});assertEquals(2,events.size());
        tracker.scan(client,"Another",event->{events.add(event);return true;});assertEquals(3,events.size());assertEquals("Another",events.get(2).player);
    }
    @Test public void allFiftyFiveSupportedPetFamiliesHaveCanonicalGameSprites() {
        int[] base={13262,13323,12646,13322,19730,13178,13071,20659,13247,21509,13320,22746,21291,12647,21748,20851,11995,12644,12645,12643,12816,12650,12652,12655,12649,12703,12648,12921,12651,20693,12653,20665,13321,20663,13181,21273,23760,23495,20661,13225,13177,13179,21992,23757,22473,24491,25348,25602,26901,27352,27590,28248,28250,28252,28246};
        Set<String> keys=new HashSet<>();for(int item:base){String key=PetTracker.key(item);assertNotNull(key);assertTrue(keys.add(key));assertEquals(item,PetTracker.itemId(key));}
        assertEquals(55,keys.size());
    }
    private Client client(Item[] inventory,Item[] bank) {
        return (Client)Proxy.newProxyInstance(Client.class.getClassLoader(),new Class<?>[]{Client.class},(proxy,method,args)->{
            if(!method.getName().equals("getItemContainer"))throw new AssertionError("Unexpected game API call: "+method.getName());
            Item[] items=args[0]==InventoryID.INVENTORY?inventory:args[0]==InventoryID.BANK?bank:null;
            if(items==null)return null;
            return Proxy.newProxyInstance(ItemContainer.class.getClassLoader(),new Class<?>[]{ItemContainer.class},(p,m,a)->{
                if(m.getName().equals("getItems"))return items;
                throw new AssertionError("Unexpected container API call: "+m.getName());
            });
        });
    }
}
