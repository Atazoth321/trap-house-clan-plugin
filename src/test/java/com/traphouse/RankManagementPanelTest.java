package com.traphouse;

import com.google.gson.*;
import java.awt.*;
import java.util.*;
import java.util.function.Consumer;
import javax.swing.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class RankManagementPanelTest {
    @Test public void savesSelectedMemberAndRankOnlyWithReasonAndWaitsForConfirmation()throws Exception {
        SwingUtilities.invokeAndWait(()->{
            java.util.List<JsonObject> commands=new ArrayList<>();java.util.List<Consumer<String>> replies=new ArrayList<>();
            RankManagementPanel panel=new RankManagementPanel(data(),new JsonArray(),new Gson().fromJson("[{name:'Owner'},{name:'Deputy Owner'},{name:'Master'},{name:'Major'},{name:'Proselyte'}]",JsonArray.class),(id,body,done)->{assertEquals("11111111-1111-4111-8111-111111111111",id);commands.add(body);replies.add(done);});
            java.util.List<JComboBox> boxes=new ArrayList<>();collect(panel,JComboBox.class,boxes);boxes.get(1).setSelectedItem("Deputy Owner");
            JButton save=save(panel);save.doClick();assertTrue(commands.isEmpty());
            JTextField reason=find(panel,JTextField.class);reason.setText("Appointed clan deputy");save.doClick();
            assertEquals("ASSIGN",commands.get(0).get("action").getAsString());assertEquals("Deputy Owner",commands.get(0).get("rank").getAsString());assertFalse(save.isEnabled());
            replies.get(0).accept(null);assertTrue(save.isEnabled());assertEquals("",reason.getText());
            boxes.get(1).setSelectedItem("Automatic (points)");reason.setText("Return to points progression");save.doClick();assertEquals("REMOVE",commands.get(1).get("action").getAsString());assertFalse(commands.get(1).has("rank"));
            panel.close();replies.get(1).accept(null);assertFalse(save.isEnabled());
        });
    }
    @Test public void serverRejectionDoesNotReportSavedAndCanBeRetried()throws Exception {
        SwingUtilities.invokeAndWait(()->{
            RankManagementPanel panel=new RankManagementPanel(data(),new JsonArray(),new JsonArray(),(id,body,done)->done.accept("Owner access rejected"));
            find(panel,JTextField.class).setText("Test request");save(panel).doClick();
            java.util.List<JLabel> labels=new ArrayList<>();collect(panel,JLabel.class,labels);assertTrue(labels.stream().anyMatch(label->"Owner access rejected".equals(label.getText())));assertTrue(save(panel).isEnabled());panel.close();
        });
    }
    static JsonObject data(){return new Gson().fromJson("{members:[{id:'11111111-1111-4111-8111-111111111111',name:'Raider',assignedRank:null,locked:false}]}",JsonObject.class);}
    private static JButton save(Container panel){java.util.List<JButton> buttons=new ArrayList<>();collect(panel,JButton.class,buttons);return buttons.stream().filter(button->"Save rank".equals(button.getText())).findFirst().get();}
    private static <T> T find(Container parent,Class<T> type){java.util.List<T> values=new ArrayList<>();collect(parent,type,values);return values.get(0);}
    private static <T> void collect(Container parent,Class<T> type,java.util.List<T> values){for(Component c:parent.getComponents()){if(type.isInstance(c))values.add(type.cast(c));if(c instanceof Container)collect((Container)c,type,values);}}
}
