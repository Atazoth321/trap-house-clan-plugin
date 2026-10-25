package com.traphouse;

import java.awt.*;
import javax.swing.*;
import javax.swing.border.EmptyBorder;

/** Compact member layout using the shared clan tab theme. */
final class ProfileStyle {
    static final Color BACKGROUND=ClanStyle.BACKGROUND,CARD=ClanStyle.CARD;
    static final Color TEXT=ClanStyle.WHITE,MUTED=ClanStyle.MUTED,ACCENT=ClanStyle.RED,GREEN=ClanStyle.GREEN;
    static JPanel card(String title) {
        JPanel panel=ClanStyle.card(null);
        panel.setBorder(new EmptyBorder(8,8,8,8));
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);
        if(title!=null){panel.add(label(title,12,TEXT));panel.add(Box.createVerticalStrut(5));}return panel;
    }
    static JLabel label(String text,int size,Color color) {
        return ClanStyle.label(text,size,color);
    }
    static JPanel row() {JPanel panel=new JPanel(new BorderLayout(4,0));panel.setOpaque(false);panel.setAlignmentX(Component.LEFT_ALIGNMENT);return panel;}
    static JPanel tile(String value,String title,Color color) {
        JPanel tile=card(null);JLabel amount=label(value,13,color),caption=label(title,10,MUTED);
        amount.setAlignmentX(Component.CENTER_ALIGNMENT);caption.setAlignmentX(Component.CENTER_ALIGNMENT);
        tile.add(amount);tile.add(caption);return tile;
    }
    static JLabel rank(String text,String rank,Color color) {
        JLabel label=label(text,11,color);ImageIcon icon=RankEmblems.icon(rank);
        if(icon!=null)label.setIcon(new ImageIcon(icon.getImage().getScaledInstance(18,18,Image.SCALE_REPLICATE)));
        label.setIconTextGap(3);return label;
    }
    private ProfileStyle() {}
}
