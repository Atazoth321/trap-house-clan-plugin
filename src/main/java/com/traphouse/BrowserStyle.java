package com.traphouse;
import java.awt.*;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import net.runelite.client.ui.FontManager;

/** Shared native clan-browser palette. */
final class BrowserStyle {
    static final Color BG=new Color(79,70,52),CARD=new Color(48,43,33),LINE=new Color(139,122,81),INK=new Color(239,221,173),GOLD=new Color(255,185,55),GREEN=new Color(149,225,101);
    static Font font(int size){return FontManager.getRunescapeBoldFont().deriveFont((float)size);}
    static JPanel panel(LayoutManager layout){JPanel panel=new JPanel(layout);panel.setOpaque(false);return panel;}
    static JLabel label(String text,int size,Color color){JLabel label=new JLabel(text);label.putClientProperty("html.disable",true);label.setFont(font(size));label.setForeground(color);return label;}
    static JButton button(String text,Runnable action){JButton button=new JButton(text);button.putClientProperty("html.disable",true);button.setBackground(CARD);button.setForeground(INK);button.setFont(font(19));button.setFocusPainted(false);button.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(LINE),new EmptyBorder(5,8,5,8)));button.addActionListener(event->action.run());return button;}
    static void select(JButton button,boolean selected){button.setBackground(selected?new Color(111,94,58):CARD);button.setForeground(selected?GOLD:INK);}
    static String pretty(String value){StringBuilder result=new StringBuilder();for(String word:value.replace('_',' ').split(" "))if(!word.isEmpty())result.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1)).append(' ');return result.toString().trim();}
}
