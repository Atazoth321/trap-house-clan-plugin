package com.traphouse;

import java.awt.Container;
import java.awt.Component;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.nio.charset.StandardCharsets;
import com.google.gson.Gson;
import okhttp3.OkHttpClient;
import javax.imageio.ImageIO;
import javax.swing.SwingUtilities;
import net.runelite.client.ui.laf.RuneLiteLAF;

/** Renders the actual sidebar without starting a game client. */
public class TrapHousePanelPreview {
    public static void main(String[] args) throws Exception {
        boolean hasData = args.length > 0;
        boolean skillEvent = hasData && "--skill-live".equals(args[0]);
        ClanEvent event = null;
        if (hasData) {
            if ("--live".equals(args[0]) || skillEvent) {
                int competitionId = args.length > 1 ? Integer.parseInt(args[1]) : 0;
                OkHttpClient http = new OkHttpClient();
                try {
                    event = new WiseOldManClient(http, new Gson()).fetchEvent(competitionId, skillEvent);
                    System.out.println(event == null ? "No active " + (skillEvent ? "skill" : "boss") + " event in group 2504"
                        : "Event " + event.id + ": " + event.scores.size() + " participants loaded");
                } finally {
                    http.dispatcher().executorService().shutdown();
                    http.connectionPool().evictAll();
                }
            } else {
                String json = new String(Files.readAllBytes(new File(args[0]).toPath()), StandardCharsets.UTF_8);
                event = WiseOldManClient.parseEvent(json, new Gson());
            }
        }
        ClanEvent previewEvent = event;
        SwingUtilities.invokeAndWait(() -> {
            RuneLiteLAF.setup();
            TrapHousePanel bossPanel = new TrapHousePanel(new TrapHouseConfig() {}, () -> {}, false);
            TrapHousePanel skillPanel = new TrapHousePanel(new TrapHouseConfig() {}, () -> {}, true);
            if (hasData) (skillEvent ? skillPanel : bossPanel).updateEvent(previewEvent, "Fetched preview data");
            TrapHouseDashboard panel = new TrapHouseDashboard(bossPanel, skillPanel, skillEvent ? 1 : 0);
            panel.setSize(225, panel.getPreferredSize().height);
            layout(panel);
            BufferedImage image = new BufferedImage(450, panel.getHeight() * 2, BufferedImage.TYPE_INT_RGB);
            Graphics2D graphics = image.createGraphics();
            graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            graphics.scale(2, 2);
            panel.printAll(graphics);
            graphics.dispose();
            try {
                File output = new File("build/trap-house-preview.png");
                output.getParentFile().mkdirs();
                ImageIO.write(image, "png", output);
                System.out.println("Preview saved to " + output.getAbsolutePath());
            } catch (Exception exception) { throw new RuntimeException(exception); }
        });
    }

    private static void layout(Container container) {
        container.doLayout();
        for (Component component : container.getComponents()) {
            if (component instanceof Container) layout((Container) component);
        }
    }
}
