package com.traphouse;

import com.google.gson.*;
import java.awt.*;
import java.util.*;
import java.util.function.Consumer;
import javax.swing.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class PointsLogPanelTest {
    @Test public void dropCatalogShowsCurrentRewardsEvenWithoutAwards()throws Exception {
        SwingUtilities.invokeAndWait(()->{
            JsonObject log=data("Member",1,0);JsonArray catalog=new JsonArray();
            JsonObject drop=new JsonObject();drop.addProperty("category","Drops");drop.addProperty("title","Bandos chestplate");drop.addProperty("points",300);drop.addProperty("sourceType","LOOT");drop.addProperty("sourceKey","2215:11832");drop.addProperty("dailyLimit",2);catalog.add(drop);log.add("availableDrops",catalog);
            PointsLogPanel panel=new PointsLogPanel("Member","Drops",(q,done)->done.accept(log));
            JTabbedPane tabs=find(panel,JTabbedPane.class);
            assertEquals("Available drops",tabs.getTitleAt(tabs.getSelectedIndex()));
            assertTrue(text(panel,"Bandos chestplate"));assertTrue(text(panel,"+300 pts per drop"));
            assertTrue(text(panel,"Automatic reward"));assertTrue(text(panel,"Daily cap: 2 per source"));
            assertTrue(text(panel,"No matching awards.")); // History remains available independently.
            find(panel,JComboBox.class).setSelectedItem("Pets");assertEquals(1,tabs.getTabCount());
            panel.close();
        });
    }
    @Test public void actualItemArtReplacesBadgeAndStaleImagesCannotChangeNewCards()throws Exception {
        SwingUtilities.invokeAndWait(()->{
            java.util.List<java.util.function.BiConsumer<String,Icon>> artwork=new ArrayList<>();
            JsonObject log=data("Member",1,1),entry=new JsonObject();entry.addProperty("category","Drops");entry.addProperty("title","415:4151");entry.addProperty("points",50);entry.addProperty("at","2026-10-25T12:00:00Z");log.getAsJsonArray("entries").add(entry);
            PointsLogPanel panel=new PointsLogPanel("Member","Drops",(q,done)->done.accept(log),(award,done)->artwork.add(done));
            Icon sprite=new ImageIcon(new java.awt.image.BufferedImage(36,32,java.awt.image.BufferedImage.TYPE_INT_ARGB));
            artwork.get(0).accept("Abyssal whip",sprite);assertTrue(text(panel,"Abyssal whip"));
            find(panel,JComboBox.class).setSelectedItem("Pets");artwork.get(1).accept("Baby Mole",sprite);assertTrue(text(panel,"Baby Mole"));
            artwork.get(0).accept("Stale item",sprite);assertFalse(text(panel,"Stale item"));assertTrue(text(panel,"Baby Mole"));
            panel.close();artwork.get(1).accept("Closed item",sprite);assertFalse(text(panel,"Closed item"));
        });
    }
    @Test public void filtersSelectedMemberAndIgnoresStaleResponses()throws Exception {
        SwingUtilities.invokeAndWait(()->{
            java.util.List<JsonObject> queries=new ArrayList<>();java.util.List<Consumer<JsonObject>> replies=new ArrayList<>();
            PointsLogPanel panel=new PointsLogPanel("Raider","Pets",(q,done)->{queries.add(q);replies.add(done);});
            assertEquals("Raider",queries.get(0).get("player").getAsString());assertEquals("Pets",queries.get(0).get("category").getAsString());
            JComboBox<?> category=find(panel,JComboBox.class);category.setSelectedItem("Drops");assertEquals("Drops",queries.get(1).get("category").getAsString());
            JsonObject current=data("Raider",2,48);replies.get(1).accept(current);assertTrue(text(panel,"48 entries | +48 pts"));
            replies.get(0).accept(data("Raider",1,1));assertTrue(text(panel,"48 entries | +48 pts"));
            button(panel,"Next").doClick();assertEquals(2,queries.get(2).get("page").getAsInt());
            panel.close();replies.get(2).accept(current);assertTrue(text(panel,"Loading..."));
        });
    }
    @Test public void noAwardsAndFailureAreExplicit()throws Exception {
        SwingUtilities.invokeAndWait(()->{
            PointsLogPanel empty=new PointsLogPanel("Member","Misc",(q,done)->done.accept(data("Member",1,0)));assertTrue(text(empty,"No matching awards."));
            PointsLogPanel failed=new PointsLogPanel("Member","Misc",(q,done)->done.accept(null));assertTrue(text(failed,"Points log unavailable. Change a filter to retry."));
        });
    }
    private static JsonObject data(String member,int pages,int count){JsonObject d=new JsonObject();d.addProperty("player",member);d.addProperty("pages",pages);d.addProperty("page",1);d.addProperty("count",count);d.addProperty("total",count);d.add("entries",new JsonArray());return d;}
    private static boolean text(Container c,String text){for(Component x:c.getComponents()){if(x instanceof JLabel&&text.equals(((JLabel)x).getText()))return true;if(x instanceof Container&&text((Container)x,text))return true;}return false;}
    private static JButton button(Container c,String name){for(Component x:c.getComponents()){if(x instanceof JButton&&name.equals(((JButton)x).getText()))return (JButton)x;if(x instanceof Container){JButton b=button((Container)x,name);if(b!=null)return b;}}return null;}
    private static <T> T find(Container c,Class<T> type){for(Component x:c.getComponents()){if(type.isInstance(x))return type.cast(x);if(x instanceof Container){T value=find((Container)x,type);if(value!=null)return value;}}return null;}
}
