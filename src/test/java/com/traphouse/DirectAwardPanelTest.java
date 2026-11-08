package com.traphouse;

import com.google.gson.*;
import java.awt.Component;
import java.util.*;
import javax.swing.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class DirectAwardPanelTest {
    @Test public void awardUsesFilteredRecipientAndReusesRequestIdAfterUnconfirmedResponse()throws Exception {
        SwingUtilities.invokeAndWait(()->{
            JsonObject data=new Gson().fromJson("{membershipMembers:[{id:'owner',name:'Owner'},{id:'inactive',name:'Inactive'}]}",JsonObject.class);
            List<JsonObject> commands=new ArrayList<>();List<String> recipients=new ArrayList<>();
            DirectAwardPanel panel=new DirectAwardPanel(data,(member,body,done)->{recipients.add(member);commands.add(body);done.accept("Unconfirmed; retry");});
            MemberPicker picker=null;JButton button=null;List<JTextField> fields=new ArrayList<>();
            for(Component child:panel.getComponents()){if(child instanceof MemberPicker)picker=(MemberPicker)child;if(child instanceof JButton)button=(JButton)child;if(child instanceof JTextField)fields.add((JTextField)child);}
            assertNotNull(picker);assertNotNull(button);picker.search.setText("inact");
            fields.get(0).setText("50");fields.get(1).setText("Event participation");button.doClick();button.doClick();
            assertEquals(Arrays.asList("inactive","inactive"),recipients);assertEquals(50,commands.get(0).get("points").getAsInt());
            assertEquals(commands.get(0).get("id"),commands.get(1).get("id"));
            picker.search.setText("no such member");assertFalse(button.isEnabled());
            picker.search.setText("Owner");fields.get(0).setText("-25");button.doClick();assertEquals(2,commands.size());
            panel.close();assertFalse(button.isEnabled());
        });
    }
}
