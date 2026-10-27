package com.traphouse;

import com.google.gson.*;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class PointAlertsTest {
    private JsonObject page(long cursor,long first,int count,int points,String title) {
        JsonObject data=new JsonObject();data.addProperty("cursor",Long.toString(cursor));JsonArray awards=new JsonArray();
        for(int i=0;i<count;i++) {
            JsonObject award=new JsonObject();award.addProperty("id",Long.toString(first+i));award.addProperty("points",points);award.addProperty("title",title);awards.add(award);
        }
        data.add("awards",awards);return data;
    }
    @Test public void oldAwardsAreSilentAndRetriesCannotRepeatMessages() {
        PointAlerts alerts=new PointAlerts();alerts.accept(page(40,0,0,0,""));assertTrue(alerts.drain().isEmpty());
        JsonObject reward=page(41,41,1,1,"Cowhide (easy test drop)");alerts.accept(reward);alerts.accept(reward);
        assertEquals(Collections.singletonList("[Trap House] You earned 1 clan point for Cowhide (easy test drop)."),alerts.drain());
        assertTrue(alerts.drain().isEmpty());
    }
    @Test public void milestoneBurstsArePagedAndDrainedWithoutLosingAwards() {
        PointAlerts alerts=new PointAlerts();alerts.accept(page(0,0,0,0,""));
        alerts.accept(page(100,1,100,50,"Skill milestone"));alerts.accept(page(105,101,5,150,"Quest milestone"));
        List<String> messages=new ArrayList<>();List<String> batch;
        while(!(batch=alerts.drain()).isEmpty()){assertTrue(batch.size()<=5);messages.addAll(batch);}
        assertEquals(105,messages.size());assertTrue(messages.get(104).contains("150 clan points for Quest milestone"));
        assertEquals(Long.valueOf(105),alerts.cursor());
    }
    @Test public void invalidPagesDoNotAdvanceCursorAndResetClearsQueuedAwards() {
        PointAlerts alerts=new PointAlerts();alerts.accept(page(0,0,0,0,""));
        try {alerts.accept(page(2,1,1,1,"Cowhide"));fail("Missing entry accepted");}catch(IllegalArgumentException expected){}
        assertEquals(Long.valueOf(0),alerts.cursor());assertTrue(alerts.drain().isEmpty());
        alerts.accept(page(1,1,1,1,"<col=ff0000>Cowhide</col>\nTest"));
        String message=alerts.drain().get(0);assertFalse(message.contains("<"));assertFalse(message.contains("\n"));
        alerts.accept(page(2,2,1,1,"Cowhide"));alerts.reset();assertNull(alerts.cursor());assertTrue(alerts.drain().isEmpty());
        alerts.accept(page(20,0,0,0,""));assertTrue(alerts.drain().isEmpty());
    }
    @Test public void queuedChatIsBoundedAndAnUnreadPageCanRetryLater() {
        PointAlerts alerts=new PointAlerts();alerts.accept(page(0,0,0,0,""));
        alerts.accept(page(100,1,100,1,"Cowhide"));alerts.accept(page(200,101,100,1,"Cowhide"));
        alerts.accept(page(300,201,100,1,"Cowhide"));assertEquals(Long.valueOf(200),alerts.cursor());
        for(int i=0;i<20;i++)alerts.drain();
        alerts.accept(page(300,201,100,1,"Cowhide"));assertEquals(Long.valueOf(300),alerts.cursor());
    }
}
