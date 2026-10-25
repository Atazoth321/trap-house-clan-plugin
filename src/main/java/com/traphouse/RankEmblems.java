package com.traphouse;

import java.awt.Color;
import java.awt.Component;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.imageio.ImageIO;
import java.io.IOException;
import java.net.URL;

/** Pixel emblems extracted from the clan owner's supplied OSRS rank reference. */
final class RankEmblems {
    private static final Map<String,ImageIcon> ICONS;
    static {
        Map<String,ImageIcon> icons=new HashMap<>();
        for(String name:new String[]{"Recruit","Pawn","Corporal","Novice","Sergeant","Colonel","Brigadier","Admiral","Marshal","Owner","Deputy Owner","Master","Major","Proselyte"}) {
            URL resource=RankEmblems.class.getResource("ranks/"+name.toLowerCase(Locale.ROOT).replace(' ','-')+".png");
            if(resource==null)throw new IllegalStateException("Missing rank emblem: "+name);
            try { icons.put(name,new ImageIcon(ImageIO.read(resource),name+" emblem")); }
            catch(IOException failure){throw new ExceptionInInitializerError(failure);}
        }
        ICONS=Collections.unmodifiableMap(icons);
    }
    static ImageIcon icon(String rank) { return ICONS.get(rank); }
    static JLabel label(String text,String rank,int size,Color color) {
        JLabel label=ClanStyle.label(text,size,color);label.setIcon(icon(rank));label.setIconTextGap(5);
        label.setAlignmentX(Component.LEFT_ALIGNMENT);return label;
    }
    private RankEmblems() {}
}
