package com.traphouse;
import com.google.gson.*;
import java.awt.*;
import java.awt.event.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import javax.swing.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class FullLeaderboardPanelTest {
    private static FullLeaderboardPanel.Loader loader(List<JsonObject> requests,List<Boolean> refreshes){return (query,refresh,done,failed)->{requests.add(query.deepCopy());refreshes.add(refresh);String mode=RankingPanel.string(query,"mode","");if(mode.equals("catalogue"))done.accept(BrowserTestData.catalogue());else if(mode.equals("clog"))done.accept(BrowserTestData.log(query.get("player").getAsString(),RankingPanel.string(query,"category","vorkath")));else {JsonObject data=BrowserTestData.view(RankingPanel.string(query,"metric","points"));data.addProperty("kind",mode);done.accept(data);}};}
    @Test public void allTenStatisticsLoadSeparateGroupedBoardsAndRefreshBypassesCache()throws Exception {
        SwingUtilities.invokeAndWait(()->{
            List<JsonObject> requests=new ArrayList<>();List<Boolean> refreshes=new ArrayList<>();FullLeaderboardPanel panel=new FullLeaderboardPanel(loader(requests,refreshes),"SampleIron2",true,(entry,done)->{});panel.start();
            for(String metric:new String[]{"points","clog","pets","greenlogs","xp","monthly-clog","monthly-points","clues","combat","bosses"}) {
                button(panel,"metric:"+metric).doClick();assertEquals(3,tables(panel).size());assertEquals(metric,requests.get(requests.size()-1).get("metric").getAsString());
            }
            int before=requests.size();button(panel,"metric:xp").doClick();assertEquals(before,requests.size());button(panel,"Refresh").doClick();assertTrue(refreshes.get(refreshes.size()-1));
            JTextField search=find(panel,JTextField.class);search.setText("SampleIron2");assertEquals(0,tables(panel).get(0).getRowCount());assertEquals(2,tables(panel).get(1).getRowCount());assertEquals(2,tables(panel).get(1).getValueAt(0,0));
            button(panel,"Find me").doClick();assertEquals("",search.getText());assertEquals("SampleIron2",tables(panel).get(1).getValueAt(tables(panel).get(1).getSelectedRow(),1));
            tables(panel).get(0).getRowSorter().toggleSortOrder(2);tables(panel).get(0).getRowSorter().toggleSortOrder(2);assertTrue((Long)tables(panel).get(0).getValueAt(0,2)>Integer.MAX_VALUE);
        });
    }
    @Test public void categoryModesUseTheirOwnRequestsAndClogLoadsTheChosenAccount()throws Exception {
        SwingUtilities.invokeAndWait(()->{
            List<JsonObject> requests=new ArrayList<>();FullLeaderboardPanel panel=new FullLeaderboardPanel(loader(requests,new ArrayList<>()),"SampleMain1",true,(entry,done)->{});panel.start();
            for(String mode:new String[]{"luck","deserved","dryboard"}){button(panel,"mode:"+mode).doClick();JsonObject last=requests.get(requests.size()-1);assertEquals(mode,last.get("mode").getAsString());assertTrue(last.has("category"));}
            button(panel,"mode:clog").doClick();JTextField search=find(panel,JTextField.class);search.setText("Other Player");search.postActionEvent();assertEquals("Other Player",requests.get(requests.size()-1).get("player").getAsString());
            button(panel,"Find me").doClick();assertEquals("SampleMain1",search.getText());
        });
    }
    @Test public void guestsCanOpenAccountsAndEveryCollectionMode()throws Exception {
        SwingUtilities.invokeAndWait(()->{
            List<JsonObject> requests=new ArrayList<>();FullLeaderboardPanel panel=new FullLeaderboardPanel(loader(requests,new ArrayList<>()),null,false,(entry,done)->{});panel.start();
            JTable table=tables(panel).get(0);table.setRowSelectionInterval(0,0);table.getActionMap().get("collection").actionPerformed(new ActionEvent(table,0,"collection"));assertEquals("clog",requests.get(requests.size()-1).get("mode").getAsString());
            for(String mode:new String[]{"clog","luck","deserved","dryboard"}){button(panel,"mode:"+mode).doClick();assertEquals(mode,requests.get(requests.size()-1).get("mode").getAsString());}
        });
    }
    @Test public void lateAndClosedWindowResponsesCannotReplaceANewerSelection()throws Exception {
        SwingUtilities.invokeAndWait(()->{
            List<Consumer<JsonObject>> pending=new ArrayList<>();FullLeaderboardPanel panel=new FullLeaderboardPanel((query,refresh,done,failed)->pending.add(done),null,false,(entry,done)->{});panel.start();button(panel,"metric:xp").doClick();pending.get(0).accept(BrowserTestData.view("points"));assertEquals(0,tables(panel).size());pending.get(1).accept(BrowserTestData.view("xp"));assertEquals(3,tables(panel).size());
            button(panel,"metric:pets").doClick();panel.close();pending.get(2).accept(BrowserTestData.view("pets"));assertEquals(0,tables(panel).size());
        });
    }
    private static List<JTable> tables(Container parent){List<JTable> result=new ArrayList<>();for(Component child:parent.getComponents()){if(child instanceof JTable)result.add((JTable)child);else if(child instanceof Container)result.addAll(tables((Container)child));}return result;}
    private static JButton button(Container parent,String text){for(Component child:parent.getComponents()){if(child instanceof JButton&&(text.equals(((JButton)child).getText())||text.equals(((JButton)child).getAccessibleContext().getAccessibleName())))return (JButton)child;if(child instanceof Container){JButton found=button((Container)child,text);if(found!=null)return found;}}return null;}
    private static <T>T find(Container parent,Class<T> type){for(Component child:parent.getComponents()){if(type.isInstance(child))return type.cast(child);if(child instanceof Container){T found=find((Container)child,type);if(found!=null)return found;}}return null;}
}
