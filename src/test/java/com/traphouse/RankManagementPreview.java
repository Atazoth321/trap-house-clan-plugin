package com.traphouse;

import com.google.gson.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;
import javax.swing.*;
import net.runelite.client.ui.laf.RuneLiteLAF;

/** Offline preview; contains illustrative member data and never submits edits. */
public class RankManagementPreview {
    public static void main(String[] args)throws Exception {
        SwingUtilities.invokeAndWait(()->{
            RuneLiteLAF.setup();Gson gson=new Gson();
            JsonObject data=gson.fromJson("{members:[{id:'11111111-1111-4111-8111-111111111111',name:'Atazoth',assignedRank:'Owner',locked:true},{id:'22222222-2222-4222-8222-222222222222',name:'Example member',assignedRank:'Deputy Owner',locked:false}]}",JsonObject.class);
            JsonArray management=gson.fromJson("[{name:'Owner'},{name:'Deputy Owner'},{name:'Master'},{name:'Major'},{name:'Proselyte'}]",JsonArray.class);
            RankManagementPanel panel=new RankManagementPanel(data,new JsonArray(),management,(member,body,done)->done.accept("Preview only"));
            panel.setSize(460,450);layout(panel);BufferedImage image=new BufferedImage(460,450,BufferedImage.TYPE_INT_RGB);Graphics2D g=image.createGraphics();panel.printAll(g);g.dispose();
            File file=new File("build/owner-rank-management-preview.png");file.getParentFile().mkdirs();try{ImageIO.write(image,"png",file);System.out.println(file.getAbsolutePath());}catch(Exception e){throw new RuntimeException(e);}panel.close();
        });
    }
    private static void layout(Container parent){parent.doLayout();for(Component child:parent.getComponents())if(child instanceof Container)layout((Container)child);}
}
