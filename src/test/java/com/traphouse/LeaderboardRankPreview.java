package com.traphouse;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;
import javax.swing.*;
import net.runelite.client.ui.laf.RuneLiteLAF;

/** Proposed layout only; sample standings, not the live leaderboard. */
public final class LeaderboardRankPreview {
    public static void main(String[] args) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            RuneLiteLAF.setup();
            JPanel page=ClanStyle.column();
            page.add(ClanStyle.label("LEADERBOARD / SAMPLE PREVIEW",10,ClanStyle.MUTED));
            page.add(Box.createVerticalStrut(12));
            String[] names={"Boob Slip","Atazoth","Example Member","LongPlayer123"};
            String[] ranks={"Proselyte","Owner","Marshal","Sergeant"};
            String[] points={"11,410","9,850","7,200","6,100"};
            for(int i=0;i<names.length;i++) {
                JButton row=RankingPanel.leaderboardButton(i+1,names[i],ranks[i],points[i],()->{});
                ClanStyle.add(page,row);
            }
            page.add(ClanStyle.label("Hover a member to see their rank title.",10,ClanStyle.MUTED));
            ClanStyle.finish(page);page.setSize(270,230);layout(page);
            BufferedImage image=new BufferedImage(270,230,BufferedImage.TYPE_INT_RGB);
            Graphics2D graphics=image.createGraphics();page.printAll(graphics);graphics.dispose();
            try {ImageIO.write(image,"png",new File("build/leaderboard-rank-preview.png"));}
            catch(Exception failure){throw new RuntimeException(failure);}
        });
    }
    private static void layout(Container parent){parent.doLayout();for(Component child:parent.getComponents())if(child instanceof Container)layout((Container)child);}
}
