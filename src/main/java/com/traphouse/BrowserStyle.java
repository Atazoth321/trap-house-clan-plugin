package com.traphouse;
import java.awt.*;
import javax.swing.*;
import javax.swing.border.EmptyBorder;

/** Full browser uses the same visual language as the clan sidebar. */
final class BrowserStyle {
    static final Color BG=ClanStyle.BACKGROUND,CARD=ClanStyle.CARD,LINE=new Color(43,47,57),INK=ClanStyle.WHITE,GOLD=ClanStyle.GOLD,GREEN=ClanStyle.GREEN;
    static final Color ACTIVE=new Color(63,25,36),STRIPE=new Color(29,33,41);
    static Font font(int size){return new Font("SansSerif",Font.BOLD,Math.max(11,size-6));}
    static JPanel panel(LayoutManager layout){JPanel panel=new JPanel(layout);panel.setOpaque(false);return panel;}
    static JLabel label(String text,int size,Color color){JLabel label=new JLabel(text);label.putClientProperty("html.disable",true);label.setFont(font(size));label.setForeground(color);return label;}
    static JButton button(String text,Runnable action){JButton button=ClanStyle.button(text,action);button.setFont(font(19));button.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(LINE),new EmptyBorder(8,10,8,10)));button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));return button;}
    static void select(JButton button,boolean selected){button.setBackground(selected?ACTIVE:new Color(33,37,46));button.setForeground(selected?ClanStyle.RED:INK);button.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(selected?ClanStyle.RED:LINE),new EmptyBorder(8,10,8,10)));}
    static String pretty(String value){StringBuilder result=new StringBuilder();for(String word:value.replace('_',' ').split(" "))if(!word.isEmpty())result.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1)).append(' ');return result.toString().trim();}
}
