package com.traphouse;

import java.awt.Component;
import java.awt.event.MouseWheelEvent;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;

/** Let an enclosing page scroll when a nested pane has no room in that direction. */
final class PageScrollPane extends JScrollPane {
    PageScrollPane(Component view) {
        super(view);
        getVerticalScrollBar().setUnitIncrement(16);
    }

    @Override protected void processMouseWheelEvent(MouseWheelEvent event) {
        JScrollBar bar = event.isShiftDown() ? getHorizontalScrollBar() : getVerticalScrollBar();
        double rotation = event.getPreciseWheelRotation();
        boolean canScroll = bar.isVisible() && (rotation < 0
            ? bar.getValue() > bar.getMinimum()
            : bar.getValue() < bar.getMaximum() - bar.getVisibleAmount());
        if (!event.isConsumed() && rotation != 0 && !canScroll) {
            JScrollPane parent = (JScrollPane) SwingUtilities.getAncestorOfClass(JScrollPane.class, this);
            if (parent != null) {
                parent.dispatchEvent(SwingUtilities.convertMouseEvent(this, event, parent));
                event.consume();
                return;
            }
        }
        super.processMouseWheelEvent(event);
    }
}
