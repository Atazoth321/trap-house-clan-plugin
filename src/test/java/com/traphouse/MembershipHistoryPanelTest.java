package com.traphouse;

import org.junit.Test;
import static org.junit.Assert.*;

public class MembershipHistoryPanelTest {
    @Test public void previewCountsFullWeeksAcrossLeapDay() {
        assertEquals(25,MembershipHistoryPanel.points("2024-02-25","2024-03-03"));
        assertEquals(25,MembershipHistoryPanel.points("2024-02-25","2024-03-09"));
        assertEquals(50,MembershipHistoryPanel.points("2024-02-25","2024-03-10"));
    }
}
