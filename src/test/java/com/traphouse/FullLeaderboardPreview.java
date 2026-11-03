package com.traphouse;

import com.google.gson.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;
import javax.swing.*;
import net.runelite.client.ui.laf.RuneLiteLAF;

/** Offline rendering of the actual full leaderboard component, using sample data. */
public final class FullLeaderboardPreview {
    public static void main(String[] args)throws Exception {
        SwingUtilities.invokeAndWait(()->{
            RuneLiteLAF.setup();FullLeaderboardPanel panel=new FullLeaderboardPanel((query,refresh,done,failed)->{
                String mode=RankingPanel.string(query,"mode","leaderboard");
                if(mode.equals("catalogue"))done.accept(BrowserTestData.catalogue());else if(mode.equals("clog"))done.accept(BrowserTestData.log("SampleMain1","vorkath"));else{JsonObject data=BrowserTestData.view(RankingPanel.string(query,"metric","points"));data.addProperty("kind",mode);done.accept(data);}
            },"SampleMain1",true,(entry,done)->{});
            panel.start();panel.setSize(1120,790);layout(panel);
            BufferedImage image=new BufferedImage(1120,820,BufferedImage.TYPE_INT_RGB);Graphics2D g=image.createGraphics();g.setColor(new Color(47,43,34));g.fillRect(0,0,1120,820);g.setColor(ClanStyle.GOLD);g.drawString("DEVELOPMENT PREVIEW / SAMPLE DATA / NOT LIVE STANDINGS",14,20);g.translate(0,30);panel.printAll(g);g.dispose();
            try{File file=new File("build/full-leaderboard-preview.png");file.getParentFile().mkdirs();ImageIO.write(image,"png",file);}catch(Exception failure){throw new RuntimeException(failure);}panel.close();
        });
    }
    private static void layout(Container parent){parent.doLayout();for(Component child:parent.getComponents())if(child instanceof Container)layout((Container)child);}
}
