package com.traphouse;

import java.awt.*;
import java.net.URI;
import javax.swing.*;
import net.runelite.client.ui.PluginPanel;

public class TrapHousePanel extends PluginPanel {
    private final JLabel killsLabel = new JLabel();
    private final TrapHouseConfig config;

    TrapHousePanel(TrapHouseConfig config, int kills, Runnable reset) {
        this.config = config;
        setBackground(new Color(20, 18, 20));
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        add(label("TRAP HOUSE", 23, new Color(225, 55, 55)));
        add(label("We PvM, We Skill, We TRAP.", 12, Color.LIGHT_GRAY));
        add(Box.createVerticalStrut(20));
        add(label("ACTIVE EVENT", 16, Color.WHITE));
        add(label(config.eventName(), 14, new Color(235, 180, 80)));
        add(Box.createVerticalStrut(12));
        add(label("TRACKED BOSS", 16, Color.WHITE));
        add(label(config.trackedBoss(), 14, Color.LIGHT_GRAY));
        add(killsLabel);
        updateKills(kills);
        JButton resetButton = new JButton("Reset counter");
        resetButton.addActionListener(e -> {
            if (JOptionPane.showConfirmDialog(this, "Reset your local boss counter?", "TRAP HOUSE", JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION) reset.run();
        });
        add(resetButton);
        add(Box.createVerticalStrut(16));
        add(label("EVENT PRIZES", 16, Color.WHITE));
        add(label("1st: " + gp(config.firstPrize()), 13, Color.LIGHT_GRAY));
        add(label("2nd: " + gp(config.secondPrize()), 13, Color.LIGHT_GRAY));
        add(label("3rd: " + gp(config.thirdPrize()), 13, Color.LIGHT_GRAY));
        add(Box.createVerticalStrut(16));
        add(linkButton("Open Discord", config.discordUrl()));
        add(linkButton("Open Clan Website", config.websiteUrl()));
        add(label("Local counter; no clan-wide sync", 11, Color.GRAY));
    }

    void updateKills(int kills) { killsLabel.setText("Nearby boss deaths: " + kills); }
    private static String gp(int amount) { return String.format("%,d GP", amount); }
    private JLabel label(String text, int size, Color color) {
        JLabel l = new JLabel(text);
        l.setFont(new Font(Font.SANS_SERIF, Font.BOLD, size));
        l.setForeground(color);
        l.setAlignmentX(Component.LEFT_ALIGNMENT);
        return l;
    }
    private JButton linkButton(String text, String url) {
        JButton button = new JButton(text);
        button.setAlignmentX(Component.LEFT_ALIGNMENT);
        button.setEnabled(url != null && url.matches("https://.+"));
        button.addActionListener(e -> {
            try { Desktop.getDesktop().browse(new URI(url)); }
            catch (Exception ex) { JOptionPane.showMessageDialog(this, "Could not open URL: " + ex.getMessage()); }
        });
        return button;
    }
}
