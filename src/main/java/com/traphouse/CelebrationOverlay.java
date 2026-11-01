package com.traphouse;

import com.google.gson.JsonObject;
import java.awt.*;
import java.util.ArrayDeque;
import javax.inject.Inject;
import javax.swing.Icon;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.client.ui.overlay.*;

/** Bounded, session-local celebrations for confirmed server changes. */
@javax.inject.Singleton
final class CelebrationOverlay extends Overlay {
    private final Client client;
    private final TrapHouseConfig config;
    private final PointsLogPanel.ItemArt art;
    private final ArrayDeque<Banner> queue = new ArrayDeque<>();
    private Banner current;
    private long started;
    private static final class Banner {
        final String heading, title, detail;
        final Color color;
        volatile Icon icon;
        Banner(String heading, String title, String detail, Color color) {
            this.heading=heading; this.title=title; this.detail=detail; this.color=color;
        }
    }
    @Inject CelebrationOverlay(Client client, TrapHouseConfig config, AwardItemImages art) {
        this(client,config,(PointsLogPanel.ItemArt)art);
    }
    CelebrationOverlay(Client client, TrapHouseConfig config, PointsLogPanel.ItemArt art) {
        this.client=client; this.config=config; this.art=art;
        setPosition(OverlayPosition.TOP_CENTER);
        setLayer(OverlayLayer.ABOVE_WIDGETS);
    }
    synchronized void award(JsonObject award) {
        String category=RankingPanel.string(award,"category","");
        if(!config.dropCelebrations() || !(category.equals("Drops") || category.equals("Pets")))return;
        Banner banner=new Banner(category.equals("Pets")?"PET UNLOCKED":"DROP RECEIVED",
            clean(RankingPanel.string(award,"title","Clan reward")),
            "+"+award.get("points").getAsInt()+" clan points"+
                (award.has("rarity")&&!award.get("rarity").isJsonNull()?" | "+clean(award.get("rarity").getAsString()):""),
            category.equals("Pets")?new Color(192,128,255):new Color(255,192,72));
        if(queue.size()>=20)return;
        queue.add(banner);
        art.load(award,(name,icon)->banner.icon=icon);
    }
    synchronized void rank(String rank) {
        if(!config.rankCelebrations() || queue.size()>=20)return;
        Banner banner=new Banner("RANK MILESTONE UNLOCKED",clean(rank),"In-game promotion requires leadership",new Color(255,192,72));
        banner.icon=RankEmblems.icon(rank);queue.add(banner);
    }
    synchronized void reset(){queue.clear();current=null;}
    static String clean(String value){return net.runelite.client.util.Text.removeTags(value).replaceAll("[\\p{Cntrl}<>]"," ");}
    @Override public synchronized Dimension render(Graphics2D graphics) {
        if(client.getGameState()!=GameState.LOGGED_IN)return null;
        long now=System.nanoTime();
        if(current!=null&&(current.heading.startsWith("RANK")?!config.rankCelebrations():!config.dropCelebrations()))current=null;
        if(current!=null && now-started>=5_000_000_000L)current=null;
        while(current==null&&!queue.isEmpty()) {
            Banner next=queue.removeFirst();
            if(next.heading.startsWith("RANK")?!config.rankCelebrations():!config.dropCelebrations())continue;
            current=next;started=now;
            if(next.heading.startsWith("RANK")&&config.rankCelebrationSound())java.awt.Toolkit.getDefaultToolkit().beep();
        }
        if(current==null)return null;
        float seconds=(now-started)/1_000_000_000f;
        float alpha=Math.min(1f,Math.min(seconds/.35f,(5f-seconds)/.6f));
        Graphics2D g=(Graphics2D)graphics.create();
        try {
            g.setComposite(AlphaComposite.SrcOver.derive(Math.max(0,alpha)));
            int y=(int)((1-alpha)*-12);
            for(int i=8;i>0;i--){g.setColor(new Color(current.color.getRed(),current.color.getGreen(),current.color.getBlue(),10));g.fillRoundRect(8-i,y+8-i,312+2*i,78+2*i,16,16);}
            g.setColor(new Color(20,23,30,240));g.fillRoundRect(8,y+8,312,78,12,12);
            g.setColor(current.color);g.drawRoundRect(8,y+8,312,78,12,12);
            if(current.icon!=null)current.icon.paintIcon(null,g,18,y+30);
            g.setFont(new Font("SansSerif",Font.BOLD,11));g.drawString(current.heading,60,y+28);
            g.setColor(Color.WHITE);g.setFont(new Font("SansSerif",Font.BOLD,13));
            String title=current.title;while(title.length()>1&&g.getFontMetrics().stringWidth(title)>246)title=title.substring(0,title.length()-1);
            if(!title.equals(current.title))title=title.substring(0,Math.max(0,title.length()-2))+"…";
            g.drawString(title,60,y+48);g.setFont(new Font("SansSerif",Font.PLAIN,10));g.setColor(Color.LIGHT_GRAY);
            g.drawString(current.detail,60,y+67);
        } finally {g.dispose();}
        return new Dimension(328,94);
    }
}
