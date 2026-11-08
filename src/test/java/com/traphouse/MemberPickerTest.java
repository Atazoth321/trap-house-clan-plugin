package com.traphouse;

import com.google.gson.*;
import javax.swing.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class MemberPickerTest {
    @Test public void olderDashboardRosterIsSearchableAndRefreshLoadsNewMembers()throws Exception {
        SwingUtilities.invokeAndWait(()->{
            JsonObject old=new Gson().fromJson("{members:[{id:'a',name:'Alice'},{id:'b',name:'Boob Slip'}]}",JsonObject.class);
            MemberPicker picker=new MemberPicker(old);assertEquals(2,picker.names.getItemCount());
            picker.search.setText("slip");assertEquals("b",picker.selected().get("id").getAsString());
            JsonObject updated=new Gson().fromJson("{membershipMembers:[{id:'a',name:'Alice',trackingDate:'2026-01-01'},{id:'b',name:'Boob Slip',trackingDate:'2026-01-01'},{id:'c',name:'Inactive',trackingDate:'2026-01-01'}]}",JsonObject.class);
            picker.updateMembers(updated);assertEquals("b",picker.selected().get("id").getAsString());
            assertEquals("2026-01-01",picker.selected().get("trackingDate").getAsString());
            picker.search.setText("inactive");assertEquals("c",picker.selected().get("id").getAsString());
        });
    }
    @Test public void searchFiltersNamesAndAlwaysSelectsTheMatchingMemberId()throws Exception {
        SwingUtilities.invokeAndWait(()->{
            MemberPicker picker=new MemberPicker(new Gson().fromJson("[{id:'a',name:'Alice'},{id:'b',name:'Boob Slip'},{id:'c',name:'Bobby'}]",JsonArray.class));
            assertEquals(3,picker.names.getItemCount());
            picker.names.setSelectedIndex(1);picker.search.setText("BOO");
            assertEquals(1,picker.names.getItemCount());assertEquals("b",picker.selected().get("id").getAsString());
            picker.search.setText("missing");assertNull(picker.selected());assertEquals(0,picker.names.getItemCount());
            picker.search.setText("bob");assertEquals("c",picker.selected().get("id").getAsString());
            picker.search.setText("");assertEquals(3,picker.names.getItemCount());
        });
    }
}
