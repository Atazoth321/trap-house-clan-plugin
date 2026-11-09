package com.traphouse;

import java.awt.*;
import javax.swing.*;
import net.runelite.client.util.LinkBrowser;

/** Compact community links styled for the clan's branded header. */
final class CommunityLinkButton extends JButton {
    static final String WEBSITE_URL="https://traphouseosrs.net/",DISCORD_URL="https://discord.gg/thosrs";
    private final boolean website;
    CommunityLinkButton(boolean website) {
        super(website?"Website":"Discord");this.website=website;
        setPreferredSize(new Dimension(108,30));setMinimumSize(new Dimension(0,30));
        setBorderPainted(false);setContentAreaFilled(false);setFocusPainted(false);setRolloverEnabled(true);
        setFont(new Font("SansSerif",Font.BOLD,10));setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        String url=website?WEBSITE_URL:DISCORD_URL;
        setToolTipText(url);getAccessibleContext().setAccessibleName(website?"Open clan website":"Open clan Discord");
        addActionListener(event->LinkBrowser.browse(url));
    }
    @Override protected void paintComponent(Graphics graphics) {
        Graphics2D g=(Graphics2D)graphics.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
            Color fill=website?new Color(63,35,39):new Color(54,44,72);
            if(getModel().isRollover()||getModel().isPressed())fill=fill.brighter();
            g.setColor(fill);g.fillRoundRect(0,0,getWidth()-1,getHeight()-1,8,8);
            g.setColor(website?new Color(128,85,57):new Color(110,91,157));g.drawRoundRect(0,0,getWidth()-1,getHeight()-1,8,8);
            int y=getHeight()/2;g.setColor(website?ClanStyle.GOLD:new Color(205,194,249));
            if(website){g.drawOval(9,y-6,12,12);g.drawOval(13,y-6,4,12);g.drawLine(9,y,21,y);}
            else{g.drawRoundRect(9,y-5,13,9,4,4);g.drawLine(12,y+4,11,y+7);g.fillOval(12,y-2,2,2);g.fillOval(17,y-2,2,2);}
            g.setFont(getFont());g.drawString(getText(),27,y+4);
            int x=getWidth()-12;g.drawLine(x,y+3,x+5,y-2);g.drawLine(x+1,y-2,x+5,y-2);g.drawLine(x+5,y-2,x+5,y+2);
            if(hasFocus()){g.setColor(ClanStyle.WHITE);g.drawRoundRect(2,2,getWidth()-5,getHeight()-5,6,6);}
        } finally {g.dispose();}
    }
}
