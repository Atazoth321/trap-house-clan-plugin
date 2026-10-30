package com.traphouse;

import com.google.gson.*;
import java.awt.*;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class RankingPanelTest {
    @Test public void rankManagementButtonRequiresAuthenticatedOwnerAndServerEditorData()throws Exception {
        SwingUtilities.invokeAndWait(()->{
            RankingPanel panel=new RankingPanel(()->{});JsonObject data=snapshot();
            data.add("rankManagement",RankManagementPanelTest.data());panel.showData(data);assertNull(button(panel,"Manage player ranks"));
            data.getAsJsonObject("profile").addProperty("permission","OWNER");data.getAsJsonObject("profile").addProperty("rank","Owner");panel.showData(data);
            assertNotNull(button(panel,"Manage player ranks"));assertNotNull(findLabel(panel,"Rank: Owner"));assertNotNull(findLabel(panel,"Rank: Owner").getIcon());
            JsonObject other=snapshot();other.getAsJsonObject("profile").addProperty("name","Raider");panel.showMember(other);assertNull(button(panel,"Manage player ranks"));
            panel.clear("Signed out");assertNull(button(panel,"Manage player ranks"));
        });
    }
    @Test public void profileFitsNarrowSidebarWithoutClippingRightHandTotals()throws Exception {
        SwingUtilities.invokeAndWait(()-> {
            RankingPanel panel=new RankingPanel(()->{});
            JsonObject data=new JsonParser().parse(new java.io.InputStreamReader(RankingPanelTest.class.getResourceAsStream("/clan-preview.json"),java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
            data.getAsJsonObject("profile").addProperty("points",4405);
            panel.showData(data);
            for(int width:new int[]{225,250,300}) {
                panel.setSize(width,600);layoutAll(panel);
                JScrollPane scroll=null;
                for(Component child:panel.getComponents())if(child instanceof JScrollPane)scroll=(JScrollPane)child;
                assertNotNull(scroll);scroll.doLayout();scroll.getViewport().doLayout();layoutAll((Container)scroll.getViewport().getView());
                assertEquals(scroll.getViewport().getExtentSize().width,scroll.getViewport().getView().getWidth());
                JLabel total=findLabel(panel,"4,405");assertNotNull(total);
                assertTrue("Points text must have visible width: "+total.getBounds(),total.getWidth()>=total.getFontMetrics(total.getFont()).stringWidth(total.getText()));
                Rectangle bounds=SwingUtilities.convertRectangle(total.getParent(),total.getBounds(),scroll.getViewport().getView());
                assertTrue("Points must remain inside the sidebar at width "+width,bounds.x>=0&&bounds.x+bounds.width<=scroll.getViewport().getExtentSize().width);
                assertTrue("Long profile must scroll vertically",scroll.getViewport().getView().getHeight()>scroll.getViewport().getExtentSize().height);
            }
        });
    }
    private static void layoutAll(Container container) {
        container.doLayout();for(Component child:container.getComponents())if(child instanceof Container)layoutAll((Container)child);
    }
    @Test public void clickingLeaderboardMemberOpensReadOnlyProfileAndBackReturnsToLeaderboard()throws Exception {
        SwingUtilities.invokeAndWait(()-> {
            RankingPanel panel=new RankingPanel(()->{});
            TrapHouseDashboard dashboard=new TrapHouseDashboard(new TrapHousePanel(new TrapHouseConfig(){},()->{}),new TrapHousePanel(new TrapHouseConfig(){},()->{},true),0,panel);
            JsonObject own=snapshot(),member=snapshot();member.getAsJsonObject("profile").addProperty("name","Raider");member.getAsJsonObject("profile").addProperty("points",250);
            JsonObject row=new JsonObject();row.addProperty("name","Raider");row.addProperty("points",250);own.getAsJsonArray("leaderboard").add(row);
            AtomicInteger requests=new AtomicInteger();
            panel.actions(name->{assertEquals("Raider",name);requests.incrementAndGet();panel.showMember(member);},(id,join)->{},dashboard::showProfile);
            panel.showData(own);button(dashboard,"Leaderboard").doClick();assertEquals(2,dashboard.selectedTab());
            button(dashboard,"#2  Raider   250").doClick();assertEquals(1,requests.get());assertEquals(0,dashboard.selectedTab());
            assertTrue(contains(panel,"Raider"));assertNotNull(findLabel(panel,"250"));
            assertNull(button(panel,"Request manual reward"));
            panel.showData(own);assertTrue(contains(panel,"Raider"));
            button(panel,"< Back").doClick();assertEquals(2,dashboard.selectedTab());
            button(dashboard,"Profile").doClick();assertTrue(contains(panel,"Member"));assertNotNull(button(panel,"Request manual reward"));
        });
    }
    @Test public void suppliedProgressionAndStaffRanksRenderTheirOwnEmblems()throws Exception {
        SwingUtilities.invokeAndWait(()-> {
            RankingPanel panel=new RankingPanel(()->{});
            JsonObject data=new JsonParser().parse(new java.io.InputStreamReader(
                RankingPanelTest.class.getResourceAsStream("/clan-preview.json"),java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
            panel.showData(data);Container rankPage=panel.pages()[1];
            for(String name:new String[]{"Recruit","Pawn","Corporal","Novice","Sergeant","Colonel","Brigadier","Admiral","Marshal","Owner","Deputy Owner","Master","Major","Proselyte"}) {
                JLabel label=findLabel(rankPage,name);assertNotNull(name,label);
                assertNotNull(name,label.getIcon());assertEquals(name+" emblem",((ImageIcon)label.getIcon()).getDescription());
            }
            assertNull(RankEmblems.icon("Unknown custom rank"));
        });
    }
    private static JLabel findLabel(Container container,String text) {
        for(Component child:container.getComponents()) {
            if(child instanceof JLabel&&text.equals(((JLabel)child).getText()))return (JLabel)child;
            if(child instanceof Container){JLabel label=findLabel((Container)child,text);if(label!=null)return label;}
        }return null;
    }
    @Test public void deductedBalanceDoesNotReplaceEarnedRankHistory()throws Exception {
        SwingUtilities.invokeAndWait(()-> {
            RankingPanel panel=new RankingPanel(()->{});JsonObject data=snapshot(),profile=data.getAsJsonObject("profile");
            profile.addProperty("points",0);profile.addProperty("lifetimeEarnedPoints",100);
            profile.addProperty("automaticRank","Corporal");profile.addProperty("automaticRankThreshold",100);
            profile.addProperty("balanceRank","Recruit");
            profile.add("earnedRankHistory",new com.google.gson.JsonParser().parse("[{\"rank\":\"Corporal\",\"threshold\":100,\"at\":\"2026-10-25T12:00:00Z\"}]").getAsJsonArray());
            panel.showData(data);
            assertTrue(contains(panel,"Automatic: Corporal"));
            assertTrue(contains(panel,"Rank from current balance: Recruit"));
            assertTrue(contains(panel,"Lifetime earned: 100 / current balance: 0"));
            assertTrue(contains(panel,"EARNED RANK HISTORY"));
            assertTrue(contains(panel,"2026-10-25T12:00:00Z"));
        });
    }
    @Test public void automaticAndProtectedAssignedRankAreDisplayedSeparately() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            RankingPanel panel=new RankingPanel(() -> {});
            JsonObject data=snapshot(),profile=data.getAsJsonObject("profile");
            profile.addProperty("automaticRank","Corporal");profile.addProperty("assignedRank","Owner");
            profile.addProperty("rankLocked",true);profile.addProperty("promotionEligible",false);
            panel.showData(data);
            assertTrue(contains(panel,"Automatic: Corporal"));
            assertTrue(contains(panel,"Assigned: Owner / Locked"));
            assertTrue(contains(panel,"Promotion eligible: No"));
        });
    }
    private JsonObject snapshot() {
        return new JsonParser().parse("{\"profile\":{\"name\":\"Member\",\"points\":50,\"rank\":\"Recruit\",\"nextThreshold\":\"Corporal at 100\"},"
            + "\"ranks\":[{\"name\":\"Recruit\",\"threshold\":0},{\"name\":\"Corporal\",\"threshold\":100}],"
            + "\"leaderboard\":[{\"name\":\"Member\",\"points\":50}],"
            + "\"sources\":[{\"type\":\"MANUAL\",\"key\":\"clan event\",\"points\":25,\"dailyLimit\":1}],"
            + "\"history\":[{\"type\":\"COMBAT\",\"key\":\"perfect kill\",\"points\":50,\"at\":\"2026-10-25T12:00:00Z\"}],"
            + "\"rewards\":[{\"key\":\"clan event\",\"status\":\"PENDING\"}]}").getAsJsonObject();
    }

    @Test public void addsRankingTabsAndPreservesEventTabs() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            RankingPanel panel = new RankingPanel(() -> {});
            TrapHouseDashboard dashboard = new TrapHouseDashboard(new TrapHousePanel(new TrapHouseConfig() {}, () -> {}),
                new TrapHousePanel(new TrapHouseConfig() {}, () -> {}, true), 0, panel);
            for(String page:new String[]{"Profile","Ranks","Leaderboard","Achievements","Events","Diary","Competitions"})assertNotNull(button(dashboard,page));
            JTabbedPane events=find(dashboard,JTabbedPane.class);
            assertEquals(3,events.getTabCount());
            assertEquals("Boss event",events.getTitleAt(0));assertEquals("Skill event",events.getTitleAt(1));assertEquals("Clan events",events.getTitleAt(2));
            panel.showData(snapshot());
            assertEquals(50, find(panel, JProgressBar.class).getValue());
            assertTrue(contains(panel,"perfect kill"));
            assertTrue(contains(panel,"PENDING"));
            panel.error();
            assertTrue(contains(panel,"Member"));
            panel.clear("Logged out");
            assertFalse(contains(panel,"Member"));
            assertNull(button(panel,"Request manual reward"));
        });
    }

    @Test public void manualRewardRequiresReasonAndUsesSelectedServerRule() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            AtomicInteger requests = new AtomicInteger();
            RankingPanel panel = new RankingPanel(() -> {}, (key, reason) -> {
                assertEquals("clan event", key); assertEquals("Attended", reason); requests.incrementAndGet();
            });
            panel.showData(snapshot());
            JButton request = button(panel, "Request manual reward");
            request.doClick(); assertEquals(0, requests.get());
            find(panel, JTextField.class).setText("Attended");
            request.doClick(); assertEquals(1, requests.get());
        });
    }

    private static JButton button(Container container, String text) {
        for (Component child : container.getComponents()) {
            if (child instanceof JButton && (text.equals(((JButton) child).getText())
                ||text.equals(((JButton)child).getAccessibleContext().getAccessibleName()))) return (JButton) child;
            if (child instanceof Container) { JButton found = button((Container) child, text); if (found != null) return found; }
        }
        return null;
    }
    private static boolean contains(Container container,String text) {
        for(Component child:container.getComponents()) {
            if(child instanceof JTextArea&&((JTextArea)child).getText().contains(text))return true;
            if(child instanceof JLabel&&((JLabel)child).getText().contains(text))return true;
            if(child instanceof Container&&contains((Container)child,text))return true;
        }
        return false;
    }
    private static <T> T find(Container container, Class<T> type) {
        for (Component child : container.getComponents()) {
            if (type.isInstance(child)) return type.cast(child);
            if (child instanceof Container) { T found = find((Container) child, type); if (found != null) return found; }
        }
        return null;
    }
}
