package com.traphouse;

import com.google.inject.Provides;
import javax.inject.Inject;
import java.awt.image.BufferedImage;
import net.runelite.api.events.NpcDespawned;
import net.runelite.api.NPC;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.util.ImageUtil;

@PluginDescriptor(name="Trap House Clan", description="Public TRAP HOUSE clan dashboard and local boss-death tracker", tags={"clan","events","boss","traphouse"})
public class TrapHousePlugin extends Plugin {
    @Inject private ClientToolbar toolbar;
    @Inject private TrapHouseConfig config;
    @Inject private ConfigManager configManager;
    private NavigationButton navigationButton;
    private TrapHousePanel panel;
    private int personalKills;

    @Provides TrapHouseConfig provideConfig(ConfigManager manager) {
        return manager.getConfig(TrapHouseConfig.class);
    }

    @Override protected void startUp() {
        personalKills = Math.max(0, configManager.getRSProfileConfiguration("traphouse", "personalKills", 0));
        panel = new TrapHousePanel(config, personalKills, () -> {
            personalKills = 0;
            configManager.setRSProfileConfiguration("traphouse", "personalKills", 0);
            panel.updateKills(0);
        });
        BufferedImage icon = ImageUtil.loadImageResource(getClass(), "icon.png");
        navigationButton = NavigationButton.builder().tooltip("TRAP HOUSE").icon(icon).priority(6).panel(panel).build();
        toolbar.addNavigation(navigationButton);
    }

    @Override protected void shutDown() {
        toolbar.removeNavigation(navigationButton);
    }

    @Subscribe public void onNpcDespawned(NpcDespawned event) {
        NPC npc = event.getNpc();
        // A death animation is a useful signal, but not proof of kill credit.
        // This counter tracks nearby boss deaths, not verified personal kills.
        if (npc.getName() != null && npc.getName().equalsIgnoreCase(config.trackedBoss().trim()) && npc.isDead()) {
            personalKills++;
            configManager.setRSProfileConfiguration("traphouse", "personalKills", personalKills);
            panel.updateKills(personalKills);
        }
    }
}
