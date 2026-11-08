package com.traphouse;

import com.google.gson.*;
import java.util.*;
import javax.swing.*;
import javax.swing.event.*;

/** Search narrows a fixed roster; typed names never become arbitrary recipients. */
final class MemberPicker extends JPanel {
    final JTextField search=new JTextField();
    final JComboBox<String> names=new JComboBox<>();
    private final java.util.List<JsonObject> roster=new ArrayList<>(),filtered=new ArrayList<>();
    private Runnable changed=()->{};
    private boolean filtering;
    MemberPicker(JsonObject data){this(roster(data));}
    private static JsonArray roster(JsonObject data) {
        return data.has("membershipMembers")&&data.get("membershipMembers").isJsonArray()?RankingPanel.array(data,"membershipMembers"):RankingPanel.array(data,"members");
    }
    MemberPicker(JsonArray members) {
        setLayout(new BoxLayout(this,BoxLayout.Y_AXIS));setOpaque(false);
        add(ClanStyle.label("Search member names",12,ClanStyle.GOLD));add(search);add(names);
        search.setToolTipText("Type any part of a member's name");
        search.putClientProperty("JTextField.placeholderText","Type a name to search...");
        search.setColumns(12);
        search.getAccessibleContext().setAccessibleName("Search member names");names.getAccessibleContext().setAccessibleName("Member names");
        for(JsonElement value:members)roster.add(value.getAsJsonObject());
        names.addActionListener(e->{if(!filtering)changed.run();});
        search.getDocument().addDocumentListener(new DocumentListener(){public void insertUpdate(DocumentEvent e){filter();}public void removeUpdate(DocumentEvent e){filter();}public void changedUpdate(DocumentEvent e){filter();}});
        filter();
    }
    void onChange(Runnable changed){this.changed=changed;}
    void updateMembers(JsonObject data) {
        JsonArray incoming=roster(data);
        java.util.List<JsonObject> next=new ArrayList<>();for(JsonElement value:incoming)next.add(value.getAsJsonObject());
        if(roster.equals(next))return;
        roster.clear();roster.addAll(next);filter();
    }
    JsonObject selected(){int index=names.getSelectedIndex();return index<0?null:filtered.get(index);}
    private void filter() {
        JsonObject previous=selected();String text=search.getText().trim().toLowerCase(Locale.ROOT);
        filtering=true;filtered.clear();names.removeAllItems();
        for(JsonObject member:roster)if(member.get("name").getAsString().toLowerCase(Locale.ROOT).contains(text)){filtered.add(member);names.addItem(member.get("name").getAsString());}
        if(previous!=null)for(int i=0;i<filtered.size();i++)if(previous.get("id").equals(filtered.get(i).get("id")))names.setSelectedIndex(i);
        filtering=false;changed.run();
    }
    void editing(boolean enabled){search.setEnabled(enabled);names.setEnabled(enabled);}
}
