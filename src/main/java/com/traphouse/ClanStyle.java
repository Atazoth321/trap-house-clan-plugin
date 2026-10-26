package com.traphouse;

import java.awt.*;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import net.runelite.client.util.ImageUtil;

/** Shared TRAP HOUSE palette and card layout for the clan screens. */
final class ClanStyle {
    static final Color BACKGROUND = new Color(15, 17, 22), CARD = new Color(24, 27, 34);
    static final Color WHITE = new Color(239, 241, 246), MUTED = new Color(143, 151, 167);
    static final Color RED = new Color(255, 77, 91), GOLD = new Color(237, 190, 102), GREEN = new Color(121, 202, 152);

    static JPanel column() {
        JPanel panel = new SidebarColumn(); panel.setBackground(BACKGROUND);
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(new EmptyBorder(8, 8, 12, 8)); return panel;
    }
    /** A hidden horizontal scrollbar must not leave the viewport wider than the sidebar. */
    private static final class SidebarColumn extends JPanel implements Scrollable {
        @Override public Dimension getPreferredScrollableViewportSize(){return getPreferredSize();}
        @Override public int getScrollableUnitIncrement(Rectangle visible,int orientation,int direction){return 16;}
        @Override public int getScrollableBlockIncrement(Rectangle visible,int orientation,int direction){return Math.max(16,visible.height-16);}
        @Override public boolean getScrollableTracksViewportWidth(){return true;}
        @Override public boolean getScrollableTracksViewportHeight(){return false;}
    }
    static JPanel card(String title) {
        return card(title,false);
    }
    private static JPanel card(String title,boolean brand) {
        JPanel panel = new TrapHousePanel.Card(brand); panel.setBackground(CARD);
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(new EmptyBorder(11,10,11,10));
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);
        if (title != null) { panel.add(label(title, 11, GOLD)); panel.add(Box.createVerticalStrut(8)); }
        return panel;
    }
    static JLabel label(String text, int size, Color color) {
        JLabel label = new JLabel(text); label.putClientProperty("html.disable", true);
        label.setFont(new Font("SansSerif", Font.BOLD, size)); label.setForeground(color);
        label.setAlignmentX(Component.LEFT_ALIGNMENT); return label;
    }
    static JTextArea text(String text) {
        JTextArea area = new JTextArea(text); area.setEditable(false); area.setFocusable(false);
        area.setLineWrap(true); area.setWrapStyleWord(true); area.setOpaque(false);
        area.setForeground(MUTED); area.setFont(new Font("SansSerif", Font.PLAIN, 11));
        area.setSize(164,Short.MAX_VALUE);
        area.setPreferredSize(new Dimension(164,area.getPreferredSize().height));
        area.setAlignmentX(Component.LEFT_ALIGNMENT); return area;
    }
    static JButton button(String text, Runnable action) {
        JButton button = new JButton(text); button.putClientProperty("html.disable", true);
        button.setBackground(new Color(33,37,46)); button.setForeground(WHITE);
        button.setFont(new Font("SansSerif", Font.BOLD, 11)); button.setFocusPainted(false);
        button.putClientProperty("JButton.buttonType","roundRect");
        button.setAlignmentX(Component.LEFT_ALIGNMENT); button.addActionListener(e -> action.run()); return button;
    }
    static void add(JPanel target, JComponent item) {
        item.setAlignmentX(Component.LEFT_ALIGNMENT);
        target.add(item); target.add(Box.createVerticalStrut(8));
    }
    static void finish(JPanel panel) {
        for (Component child : panel.getComponents()) {
            if (child instanceof JPanel) finish((JPanel) child);
            if (child instanceof JComponent) {
                ((JComponent) child).setMaximumSize(new Dimension(Integer.MAX_VALUE, child.getPreferredSize().height));
                ((JComponent) child).setMinimumSize(new Dimension(0, child.getMinimumSize().height));
            }
        }
        panel.revalidate(); panel.repaint();
    }
    static JScrollPane scroll(JPanel panel) {
        JScrollPane scroll = new JScrollPane(panel); scroll.setBorder(null);
        scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.getVerticalScrollBar().setUnitIncrement(16); return scroll;
    }
    static JPanel brand() {
        JPanel header = card(null,true);
        header.setBackground(new Color(63,25,36));
        header.add(label("THE CLAN / THE GRIND", 9, new Color(255,160,165)));
        JPanel row = new JPanel(new BorderLayout(8,0)); row.setOpaque(false);row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.add(new JLabel(new ImageIcon(ImageUtil.loadImageResource(ClanStyle.class,"logo.png").getScaledInstance(42,42,Image.SCALE_SMOOTH))),BorderLayout.WEST);
        JPanel name = new JPanel(new GridLayout(2,1)); name.setOpaque(false);
        name.add(label("TRAP HOUSE.",16,WHITE)); name.add(label("PvM. Skill. TRAP.",10,GOLD));
        row.add(name,BorderLayout.CENTER); header.add(row); return header;
    }
}
