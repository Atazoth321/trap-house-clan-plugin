package com.traphouse;

import com.google.gson.*;
import java.awt.*;
import java.util.*;
import java.util.function.*;
import javax.swing.*;

/** Authenticated owner's rank editor. The server enforces every change. */
final class RankManagementPanel extends JPanel {
    interface Save {void accept(String member,JsonObject body,Consumer<String> done);}
    private final JComboBox<String> members=new JComboBox<>(),ranks=new JComboBox<>();
    private final JTextField reason=new JTextField();
    private final JLabel current=ClanStyle.label("",11,ClanStyle.MUTED),status=ClanStyle.label("Select a rank and enter a reason to save.",11,ClanStyle.MUTED);
    private final JButton save;
    private final java.util.List<JsonObject> roster=new ArrayList<>();
    private boolean active=true,busy;
    RankManagementPanel(JsonObject data,JsonArray earned,JsonArray protectedRanks,Save submit) {
        super(new BorderLayout(8,8));setBackground(ClanStyle.BACKGROUND);setBorder(BorderFactory.createEmptyBorder(12,12,12,12));setPreferredSize(new Dimension(460,450));
        JPanel form=ClanStyle.column();
        JTextArea introduction=ClanStyle.text("Assign plugin ranks and management emblems. OSRS clan ranks must be changed manually. Display ranks do not grant administrator permissions.");
        introduction.setPreferredSize(null);introduction.setSize(410,Short.MAX_VALUE);introduction.setPreferredSize(new Dimension(410,introduction.getPreferredSize().height));
        form.add(introduction);form.add(Box.createVerticalStrut(12));
        form.add(ClanStyle.label("Player",12,ClanStyle.GOLD));form.add(members);form.add(current);
        form.add(ClanStyle.label("Rank",12,ClanStyle.GOLD));ranks.addItem("Automatic (points)");
        LinkedHashSet<String> options=new LinkedHashSet<>();
        for(JsonElement entry:protectedRanks)options.add(entry.getAsJsonObject().get("name").getAsString());
        for(JsonElement entry:earned)options.add(entry.getAsJsonObject().get("name").getAsString());
        for(String rank:options)ranks.addItem(rank);
        ranks.setRenderer(new DefaultListCellRenderer(){public Component getListCellRendererComponent(JList<?> list,Object value,int index,boolean selected,boolean focus){JLabel label=(JLabel)super.getListCellRendererComponent(list,value,index,selected,focus);label.setIcon(RankEmblems.icon(String.valueOf(value)));return label;}});
        form.add(ranks);form.add(ClanStyle.label("Reason",12,ClanStyle.GOLD));form.add(reason);
        for(JsonElement entry:RankingPanel.array(data,"members")){JsonObject member=entry.getAsJsonObject();roster.add(member);members.addItem(member.get("name").getAsString());}
        save=ClanStyle.button("Save rank",()->{
            int selected=members.getSelectedIndex();String why=reason.getText().trim();
            if(!active||busy||selected<0)return;
            if(why.isEmpty()||why.length()>1000){status.setText("Enter a reason (1-1000 characters).");return;}
            JsonObject body=new JsonObject();boolean automatic=ranks.getSelectedIndex()==0;
            body.addProperty("action",automatic?"REMOVE":"ASSIGN");body.addProperty("reason",why);
            if(!automatic)body.addProperty("rank",(String)ranks.getSelectedItem());
            busy=true;setEditing(false);status.setText("Saving; waiting for server confirmation...");
            submit.accept(roster.get(selected).get("id").getAsString(),body,error->{
                if(!active)return;busy=false;setEditing(true);
                if(error!=null){status.setText(error);status.setToolTipText(error);return;}
                roster.get(selected).addProperty("assignedRank",automatic?null:(String)ranks.getSelectedItem());
                showCurrent();reason.setText("");status.setText("Rank saved. Clan profiles will refresh.");
            });
        });
        members.addActionListener(e->{showCurrent();selectCurrent();});
        showCurrent();selectCurrent();form.add(save);form.add(status);ClanStyle.finish(form);
        JScrollPane scroll=ClanStyle.scroll(form);scroll.getViewport().setBackground(ClanStyle.BACKGROUND);add(scroll,BorderLayout.CENTER);
        save.setEnabled(!roster.isEmpty());
    }
    private void showCurrent(){int i=members.getSelectedIndex();if(i<0)return;JsonObject member=roster.get(i);String rank=member.has("assignedRank")&&!member.get("assignedRank").isJsonNull()?member.get("assignedRank").getAsString():"Automatic (points)";current.setText("Assigned: "+rank);current.setIcon(RankEmblems.icon(rank));}
    private void selectCurrent(){int i=members.getSelectedIndex();if(i<0)return;JsonObject member=roster.get(i);ranks.setSelectedItem(member.has("assignedRank")&&!member.get("assignedRank").isJsonNull()?member.get("assignedRank").getAsString():"Automatic (points)");}
    private void setEditing(boolean enabled){members.setEnabled(enabled);ranks.setEnabled(enabled);reason.setEnabled(enabled);save.setEnabled(enabled&&!roster.isEmpty());}
    void close(){active=false;setEditing(false);}
}
