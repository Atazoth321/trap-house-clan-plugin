package com.traphouse;

import java.awt.*;
import javax.swing.Icon;

/** Category artwork for rewards without an item sprite. */
final class RewardIcon implements Icon {
    private final String category;
    private final boolean received;
    RewardIcon(String category,boolean received){this.category=category;this.received=received;}
    public int getIconWidth(){return 48;}
    public int getIconHeight(){return 48;}
    public void paintIcon(Component c,Graphics graphics,int x,int y) {
        Graphics2D g=(Graphics2D)graphics.create();g.translate(x,y);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(new Color(37,43,55));g.fillRoundRect(2,2,44,44,12,12);
        g.setColor(received?new Color(125,210,150):ProfileStyle.ACCENT);g.setStroke(new BasicStroke(2.5f,BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND));
        switch(category) {
            case "Pets":
                g.fillOval(15,22,18,15);g.fillOval(9,15,7,9);g.fillOval(19,10,7,9);g.fillOval(29,13,7,9);break;
            case "Combat":case "Challenges":
                g.drawLine(13,12,35,34);g.drawLine(35,12,13,34);g.drawLine(10,29,19,38);g.drawLine(29,38,38,29);break;
            case "Milestones":
                g.drawArc(8,11,12,16,90,180);g.drawArc(28,11,12,16,270,180);
                g.drawPolyline(new int[]{16,16,21,27,32,32,16},new int[]{11,23,29,29,23,11,11},7);
                g.drawLine(24,29,24,37);g.drawLine(17,38,31,38);break;
            case "Diaries":case "Collection":case "Clues":
                g.drawRoundRect(12,10,25,29,3,3);g.drawLine(18,10,18,39);g.drawLine(23,19,32,19);g.drawLine(23,25,32,25);g.drawLine(23,31,30,31);break;
            case "Events":
                g.drawRoundRect(11,13,27,26,3,3);g.drawLine(11,21,38,21);g.drawLine(18,9,18,17);g.drawLine(31,9,31,17);g.fillOval(20,26,8,8);break;
            case "Efficiency":
                g.drawPolyline(new int[]{11,19,26,37},new int[]{36,27,30,13},4);g.drawLine(28,13,37,13);g.drawLine(37,13,37,23);break;
            default:
                g.drawRoundRect(11,19,27,19,3,3);g.drawRect(9,14,31,7);g.drawLine(24,14,24,38);g.drawOval(14,7,10,8);g.drawOval(24,7,10,8);
        }
        if(received){g.setColor(new Color(39,62,46));g.fillOval(33,32,15,15);g.setColor(new Color(125,210,150));g.drawPolyline(new int[]{36,39,45},new int[]{39,42,36},3);}
        g.dispose();
    }
}
