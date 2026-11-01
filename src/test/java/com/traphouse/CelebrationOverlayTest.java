package com.traphouse;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.lang.reflect.Proxy;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import org.junit.Test;
import static org.junit.Assert.*;

public class CelebrationOverlayTest {
    @Test public void legacyAwardCanCelebrateAfterServerCatalogEnrichment() {
        Client client=(Client)Proxy.newProxyInstance(Client.class.getClassLoader(),new Class<?>[]{Client.class},
            (proxy,method,args)->method.getName().equals("getGameState")?GameState.LOGGED_IN:null);
        CelebrationOverlay overlay=new CelebrationOverlay(client,new TrapHouseConfig(){},(entry,done)->{});
        com.google.gson.JsonObject award=new com.google.gson.JsonObject();
        award.addProperty("title","Cowhide (easy test drop)");award.addProperty("points",1);
        overlay.award(award);
        Graphics2D graphics=new BufferedImage(400,120,BufferedImage.TYPE_INT_ARGB).createGraphics();
        try {
            assertNull(overlay.render(graphics));
            com.google.gson.JsonArray sources=new com.google.gson.JsonArray();
            com.google.gson.JsonObject rule=new com.google.gson.JsonObject();
            rule.addProperty("name","Cowhide (easy test drop)");rule.addProperty("type","LOOT");rule.addProperty("key","2790:1739");sources.add(rule);
            com.google.gson.JsonObject confirmed=AwardDetails.enrich(award,sources);
            assertEquals("Drops",confirmed.get("category").getAsString());
            assertEquals(1739,AwardItemImages.itemId(confirmed));
            overlay.award(confirmed);assertNotNull(overlay.render(graphics));
            assertFalse(award.has("category"));
            overlay.reset();assertNull(overlay.render(graphics));
        }finally{graphics.dispose();}
    }
    @Test public void rankBannersRespectLoginToggleAndSessionReset() {
        GameState[] state={GameState.LOGIN_SCREEN};boolean[] enabled={true};
        Client client=(Client)Proxy.newProxyInstance(Client.class.getClassLoader(),new Class<?>[]{Client.class},
            (proxy,method,args)->method.getName().equals("getGameState")?state[0]:null);
        TrapHouseConfig config=new TrapHouseConfig(){public boolean rankCelebrations(){return enabled[0];}};
        CelebrationOverlay overlay=new CelebrationOverlay(client,config,null);
        Graphics2D graphics=new BufferedImage(400,120,BufferedImage.TYPE_INT_ARGB).createGraphics();
        try {
            overlay.rank("Recruit");assertNull(overlay.render(graphics));
            state[0]=GameState.LOGGED_IN;assertNotNull(overlay.render(graphics));
            enabled[0]=false;assertNull(overlay.render(graphics));
            enabled[0]=true;overlay.rank("Corporal");overlay.reset();assertNull(overlay.render(graphics));
        } finally {graphics.dispose();}
    }
}
