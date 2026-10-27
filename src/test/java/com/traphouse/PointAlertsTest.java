package com.traphouse;

import com.google.gson.*;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class PointAlertsTest {
    @Test public void categoryColorsUseConfirmedCategoryAndSanitizeTheRewardTitle() {
        PointAlerts alerts=new PointAlerts();TrapHouseConfig config=new TrapHouseConfig(){};alerts.accept(page(0,0,0,0,""));
        String[] categories={"Drops","Pets","Milestones","Combat","Misc","Unknown"},colors={"ffc048","c080ff","64d282","78c8ff","78c8ff","78c8ff"};
        for(int i=0;i<categories.length;i++) {
            JsonObject data=page(i+1,i+1,1,1,"<col=ff0000>Reward</col>");data.getAsJsonArray("awards").get(0).getAsJsonObject().addProperty("category",categories[i]);alerts.accept(data);
            assertEquals("<col="+colors[i]+">[Trap House] You earned 1 clan point for Reward.</col>",alerts.drain(config).get(0));
        }
        alerts.accept(page(7,7,1,1,"Legacy reward"));assertTrue(alerts.drain(config).get(0).startsWith("<col=78c8ff>"));
    }
    @Test public void queuedAwardsUseLatestColorPreferencesAndCanBeDisplayedWithoutColors() {
        PointAlerts alerts=new PointAlerts();alerts.accept(page(0,0,0,0,""));JsonObject drop=page(1,1,1,1,"Cowhide");drop.getAsJsonArray("awards").get(0).getAsJsonObject().addProperty("category","Drops");alerts.accept(drop);
        TrapHouseConfig custom=new TrapHouseConfig(){public java.awt.Color rankingDropMessageColor(){return new java.awt.Color(0,16,255,50);}};
        assertEquals("<col=0010ff>[Trap House] You earned 1 clan point for Cowhide.</col>",alerts.drain(custom).get(0));
        drop.getAsJsonArray("awards").get(0).getAsJsonObject().addProperty("id","2");drop.addProperty("cursor","2");alerts.accept(drop);
        assertEquals("[Trap House] You earned 1 clan point for Cowhide.",alerts.drain(new TrapHouseConfig(){public boolean rankingPointMessageColors(){return false;}}).get(0));
    }
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
