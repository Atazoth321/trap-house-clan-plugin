package com.traphouse;

import com.google.gson.*;
import java.util.UUID;
import javax.swing.*;

/** Immediate positive point awards, exposed only on the authenticated owner's profile. */
final class DirectAwardPanel extends JPanel {
    private final MemberPicker members;
    private final JTextField amount=new JTextField(),reason=new JTextField();
    private final JTextArea status=ClanStyle.text("Owner awards apply immediately.");
    private final JButton award;
    private boolean active=true,busy;
    private String pendingSignature,pendingId;
    DirectAwardPanel(JsonObject data,RankManagementPanel.Save submit) {
        setLayout(new BoxLayout(this,BoxLayout.Y_AXIS));setOpaque(false);
        members=new MemberPicker(data);add(members);
        add(ClanStyle.label("Points (1-100000)",12,ClanStyle.GOLD));add(amount);
        add(ClanStyle.label("Award reason",12,ClanStyle.GOLD));add(reason);
        award=ClanStyle.button("Give manual award",()->{
            JsonObject member=members.selected();if(!active||busy||member==null)return;
            int points;try{points=Integer.parseInt(amount.getText().trim());}catch(NumberFormatException invalid){status.setText("Enter a whole number of points.");return;}
            if(points<1||points>100000){status.setText("Enter 1 to 100000 points.");return;}
            String note=reason.getText().trim();if(note.isEmpty()||note.length()>1000){status.setText("Enter a reason (1-1000 characters).");return;}
            String signature=member.get("id").getAsString()+"|"+points+"|"+note;
            if(!signature.equals(pendingSignature)){pendingSignature=signature;pendingId=UUID.randomUUID().toString();}
            JsonObject body=new JsonObject();body.addProperty("id",pendingId);body.addProperty("points",points);body.addProperty("reason",note);
            String name=member.get("name").getAsString();busy=true;editing(false);status.setText("Waiting for server confirmation...");
            submit.accept(member.get("id").getAsString(),body,error->{
                if(!active)return;busy=false;editing(true);
                if(error!=null){status.setText(error);return;}
                status.setText("Confirmed: +"+points+" points to "+name+".");
                amount.setText("");reason.setText("");pendingSignature=null;pendingId=null;
            });
        });
        members.onChange(()->award.setEnabled(active&&!busy&&members.selected()!=null));
        add(award);add(status);editing(true);
    }
    private void editing(boolean enabled){members.editing(enabled);amount.setEnabled(enabled);reason.setEnabled(enabled);award.setEnabled(enabled&&members.selected()!=null);}
    void updateMembers(JsonObject data){if(!busy)members.updateMembers(data);}
    void close(){active=false;editing(false);}
}
