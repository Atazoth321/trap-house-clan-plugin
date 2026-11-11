package com.traphouse;

import java.awt.Dimension;
import java.awt.event.MouseWheelEvent;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import org.junit.Test;
import static org.junit.Assert.*;

public class PageScrollPaneTest {
    @Test public void wheelOverShortNestedPageScrollsOuterPage() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            PageScrollPane inner = pane(50);
            inner.setPreferredSize(new Dimension(200, 600));
            JScrollPane outer = new JScrollPane(inner);
            outer.setSize(240, 200);
            outer.doLayout();
            inner.setSize(outer.getViewport().getExtentSize().width, 600);
            inner.doLayout();
            wheel(inner, 1);
            assertTrue("The outer page must receive the wheel", outer.getVerticalScrollBar().getValue() > 0);
        });
    }

    @Test public void scrollsInnerContentThenPassesWheelAtBothEdges() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            PageScrollPane inner = pane(1200);
            inner.setPreferredSize(new Dimension(200, 400));
            JScrollPane outer = new JScrollPane(inner);
            outer.setSize(240, 200);
            outer.doLayout();
            inner.setSize(200, 400);
            inner.doLayout();
            wheel(inner, 1);
            assertTrue(inner.getVerticalScrollBar().getValue() > 0);
            assertEquals(0, outer.getVerticalScrollBar().getValue());

            inner.getVerticalScrollBar().setValue(inner.getVerticalScrollBar().getMaximum());
            wheel(inner, 1);
            assertTrue(outer.getVerticalScrollBar().getValue() > 0);

            inner.getVerticalScrollBar().setValue(0);
            int before = outer.getVerticalScrollBar().getValue();
            wheel(inner, -1);
            assertTrue(outer.getVerticalScrollBar().getValue() < before);
        });
    }

    private static PageScrollPane pane(int contentHeight) {
        JPanel content = new JPanel();
        content.setPreferredSize(new Dimension(160, contentHeight));
        return new PageScrollPane(content);
    }

    private static void wheel(JScrollPane pane, int direction) {
        pane.dispatchEvent(new MouseWheelEvent(pane, MouseWheelEvent.MOUSE_WHEEL,
            System.currentTimeMillis(), 0, 40, 40, 0, false,
            MouseWheelEvent.WHEEL_UNIT_SCROLL, 3, direction));
    }
}
