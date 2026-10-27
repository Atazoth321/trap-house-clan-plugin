package com.traphouse;

import com.google.gson.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;
import javax.swing.*;
import net.runelite.client.ui.laf.RuneLiteLAF;

/** Offline preview of the actual reward browser, with explicitly illustrative awards. */
public class RewardCatalogPreview {
    public static void main(String[] args)throws Exception {
        SwingUtilities.invokeAndWait(()->{
            RuneLiteLAF.setup();
            JsonObject data=new JsonObject();data.addProperty("player","Preview");data.addProperty("page",1);data.addProperty("pages",1);data.addProperty("count",4);data.addProperty("total",1300);data.add("entries",new JsonArray());
            JsonArray rewards=new JsonArray();
            String[] titles={"First Fire cape","Baby mole","Perfect kill","Helpful clan member","Attack level 99","Pet kraken","Elite combat tier","Clan event attendance","Elite diary","Collection milestone","Boss challenge","Efficiency milestone"};
            String[] categories={"Milestones","Pets","Combat","Misc","Milestones","Pets","Combat","Events","Diaries","Collection","Challenges","Efficiency"};
            for(int i=0;i<titles.length;i++) {
                JsonObject card=new JsonObject();card.addProperty("title",titles[i]);card.addProperty("category",categories[i]);card.addProperty("sourceType",i==3||i==7?"MANUAL":"GOAL");card.addProperty("sourceKey","preview-"+i);
                card.addProperty("points",i%2==0?150:500);card.addProperty("dailyLimit",1);card.addProperty("onceOnly",i!=3&&i!=7);card.addProperty("received",i<4);card.addProperty("receivedCount",i<4?1:0);card.addProperty("earnedPoints",i<4?(i%2==0?150:500):0);rewards.add(card);
                if(i<4){JsonObject entry=card.deepCopy();entry.addProperty("at","2026-10-26T12:00:00Z");entry.addProperty("occurrences",1);data.getAsJsonArray("entries").add(entry);}
            }
            data.add("availableRewards",rewards);
            PointsLogPanel panel=new PointsLogPanel("Preview","All",(query,done)->done.accept(data));panel.setSize(960,850);layout(panel);
            BufferedImage image=new BufferedImage(960,850,BufferedImage.TYPE_INT_RGB);Graphics2D g=image.createGraphics();panel.printAll(g);g.dispose();
            File file=new File("build/reward-catalog-preview.png");file.getParentFile().mkdirs();
            try{ImageIO.write(image,"png",file);System.out.println("Illustrative reward preview: "+file.getAbsolutePath());}catch(Exception e){throw new RuntimeException(e);}panel.close();
        });
    }
    private static void layout(Container container){container.doLayout();for(Component child:container.getComponents())if(child instanceof Container)layout((Container)child);}
}
