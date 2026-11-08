package com.traphouse;

import com.google.gson.*;
import java.awt.BorderLayout;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import javax.swing.*;
import javax.swing.event.*;

/** Owner-only form for verified historical membership, including the owner's account. */
final class MembershipHistoryPanel extends JPanel {
    private final MemberPicker members;
    private final JTextField start=new JTextField(),end=new JTextField(),reason=new JTextField();
    private final JLabel preview=ClanStyle.label("Enter dates to preview points.",12,ClanStyle.GOLD);
    private final JTextArea status=ClanStyle.text("Server confirmation appears here.");
    private final JLabel baseline=ClanStyle.label("",11,ClanStyle.MUTED);
    private final JButton save;
    private boolean active=true,busy;
    private String pendingSignature,pendingId;
    MembershipHistoryPanel(JsonObject data,RankManagementPanel.Save submit) {
        super(new BorderLayout());setBackground(ClanStyle.BACKGROUND);
        JPanel form=ClanStyle.column();
        members=new MemberPicker(data);
        form.add(ClanStyle.text("Award verified past clan time to any member, including yourself or inactive members. 25 points per full seven days. The end date is exclusive; leftover days do not earn points. Previously awarded ranges cannot overlap."));
        form.add(ClanStyle.label("Member",12,ClanStyle.GOLD));form.add(members);form.add(baseline);
        form.add(ClanStyle.label("Start date (YYYY-MM-DD)",12,ClanStyle.GOLD));form.add(start);
        form.add(ClanStyle.label("End date (YYYY-MM-DD)",12,ClanStyle.GOLD));form.add(end);
        form.add(preview);form.add(ClanStyle.label("Verification note / source",12,ClanStyle.GOLD));form.add(reason);
        save=ClanStyle.button("Award membership points",()->{
            if(!active||busy||members.selected()==null)return;
            int points;
            try{points=validatedPoints();}catch(RuntimeException invalid){status.setText("Enter valid dates and at least seven historical days.");return;}
            String note=reason.getText().trim();if(note.isEmpty()||note.length()>1000){status.setText("Enter a verification note (1-1000 characters).");return;}
            JsonObject member=members.selected();
            String signature=member.get("id").getAsString()+"|"+start.getText().trim()+"|"+end.getText().trim()+"|"+note;
            if(!signature.equals(pendingSignature)){pendingSignature=signature;pendingId=UUID.randomUUID().toString();}
            JsonObject body=new JsonObject();body.addProperty("id",pendingId);body.addProperty("startDate",start.getText().trim());body.addProperty("endDate",end.getText().trim());body.addProperty("reason",note);
            busy=true;editing(false);status.setText("Waiting for server confirmation...");
            submit.accept(member.get("id").getAsString(),body,error->{
                if(!active)return;busy=false;editing(true);
                status.setText(error==null?"Confirmed: +"+points+" membership points.":error);status.setToolTipText(status.getText());
            });
        });
        DocumentListener listener=new DocumentListener(){public void insertUpdate(DocumentEvent e){update();}public void removeUpdate(DocumentEvent e){update();}public void changedUpdate(DocumentEvent e){update();}};
        start.getDocument().addDocumentListener(listener);end.getDocument().addDocumentListener(listener);
        members.onChange(this::update);form.add(save);form.add(status);add(form);update();
    }
    static int points(String start,String end) {
        long weeks=ChronoUnit.DAYS.between(LocalDate.parse(start),LocalDate.parse(end))/7;
        if(weeks<1||weeks>4000)throw new IllegalArgumentException("Enter at least 7 days, up to 4000 full weeks.");
        return (int)weeks*25;
    }
    private int validatedPoints() {
        if(!hasTrackingDate())throw new IllegalArgumentException("Backend update required for verified dates.");
        int points=points(start.getText().trim(),end.getText().trim());
        LocalDate until=LocalDate.parse(end.getText().trim());
        if(until.isAfter(LocalDate.parse(members.selected().get("trackingDate").getAsString())))throw new IllegalArgumentException("End date must be on or before automatic tracking began.");
        return points;
    }
    private void update() {
        save.setEnabled(active&&!busy&&hasTrackingDate());
        if(members.selected()==null){baseline.setText("No matching member");preview.setText("Search for a clan member.");return;}
        if(!hasTrackingDate()){baseline.setText("Tracking date unavailable");preview.setText("Backend update required.");status.setText("Member search is available. Deploy the backend update and run init to enable verified date awards.");return;}
        baseline.setText("Auto starts: "+members.selected().get("trackingDate").getAsString());
        try{int points=validatedPoints();preview.setText((points/25)+" full weeks = "+points+" points");}
        catch(RuntimeException invalid){preview.setText("Enter a valid historical range (YYYY-MM-DD).");}
    }
    private boolean hasTrackingDate(){JsonObject selected=members.selected();return selected!=null&&selected.has("trackingDate")&&!selected.get("trackingDate").isJsonNull();}
    void updateMembers(JsonObject data){if(!busy)members.updateMembers(data);}
    private void editing(boolean enabled){members.editing(enabled);start.setEnabled(enabled);end.setEnabled(enabled);reason.setEnabled(enabled);save.setEnabled(enabled&&hasTrackingDate());}
    void close(){active=false;editing(false);}
}
