package com.traphouse;

import java.awt.BorderLayout;
import java.awt.Font;
import javax.swing.JTabbedPane;
import net.runelite.client.ui.PluginPanel;

final class TrapHouseDashboard extends PluginPanel {
    private final JTabbedPane tabs = new JTabbedPane();

    TrapHouseDashboard(TrapHousePanel bossPanel, TrapHousePanel skillPanel, int selectedTab) {
        setLayout(new BorderLayout());
        tabs.setFont(new Font("SansSerif", Font.BOLD, 11));
        tabs.addTab("Boss event", bossPanel);
        tabs.addTab("Skill event", skillPanel);
        tabs.setToolTipTextAt(0, "Boss of the Week: competition kill gains");
        tabs.setToolTipTextAt(1, "Skill of the Month: competition XP gains");
        tabs.setSelectedIndex(selectedTab);
        add(tabs, BorderLayout.CENTER);
    }

    int selectedTab() { return tabs.getSelectedIndex(); }
}
