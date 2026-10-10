package com.traphouse;

import com.google.gson.Gson;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;
import javax.swing.SwingUtilities;
import net.runelite.client.ui.laf.RuneLiteLAF;
import okhttp3.OkHttpClient;

/** Side-by-side rendering of both tabs using the real components and current WOM events. */
public class TrapHouseBothTabsPreview {
    public static void main(String[] args) throws Exception {
        OkHttpClient http = new OkHttpClient();
        ClanEvent boss;
        ClanEvent skill;
        try {
            WiseOldManClient client = new WiseOldManClient(http, new Gson());
            boss = client.fetchEvent(0, false);
            skill = client.fetchEvent(0, true);
        } finally {
            http.dispatcher().executorService().shutdown();
            http.connectionPool().evictAll();
        }
        SwingUtilities.invokeAndWait(() -> {
            RuneLiteLAF.setup();
            TrapHouseDashboard left = dashboard(boss, skill, 0);
            TrapHouseDashboard right = dashboard(boss, skill, 1);
            int height = Math.max(left.getPreferredSize().height, right.getPreferredSize().height);
            left.setSize(225, height);
            right.setSize(225, height);
            layout(left);
            layout(right);
            BufferedImage image = new BufferedImage(948, (height + 24) * 2, BufferedImage.TYPE_INT_RGB);
            Graphics2D graphics = image.createGraphics();
            graphics.setColor(new Color(15, 17, 22));
            graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
            graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            graphics.scale(2, 2);
            graphics.translate(6, 12);
            left.printAll(graphics);
            graphics.translate(237, 0);
            right.printAll(graphics);
            graphics.dispose();
            try {
                File output = new File("build/trap-house-both-tabs.png");
                output.getParentFile().mkdirs();
                ImageIO.write(image, "png", output);
                System.out.println("Both tabs saved to " + output.getAbsolutePath());
            } catch (Exception exception) { throw new RuntimeException(exception); }
        });
    }

    private static TrapHouseDashboard dashboard(ClanEvent boss, ClanEvent skill, int selectedTab) {
        TrapHousePanel bossPanel = new TrapHousePanel(new TrapHouseConfig() {}, () -> {}, false);
        TrapHousePanel skillPanel = new TrapHousePanel(new TrapHouseConfig() {}, () -> {}, true);
        bossPanel.updateEvent(boss, "Fetched live event data");
        skillPanel.updateEvent(skill, "Fetched live event data");
        return new TrapHouseDashboard(bossPanel, skillPanel, selectedTab);
    }

    private static void layout(Container container) {
        container.doLayout();
        for (Component component : container.getComponents()) {
            if (component instanceof Container) layout((Container) component);
        }
    }
}
