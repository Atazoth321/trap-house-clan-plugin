package com.traphouse;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.time.Instant;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

final class TrapHouseEventOverlay extends OverlayPanel {
    private final Client client;
    private final TrapHouseConfig config;
    private final TrapHouseClanPlugin plugin;

    @Inject
    TrapHouseEventOverlay(Client client, TrapHouseConfig config, TrapHouseClanPlugin plugin) {
        super(plugin);
        this.client = client;
        this.config = config;
        this.plugin = plugin;
        setPosition(OverlayPosition.TOP_LEFT);
        panelComponent.setPreferredSize(new Dimension(230, 0));
    }

    @Override public Dimension render(Graphics2D graphics) {
        if (!config.showEventOverlay() || client.getGameState() != GameState.LOGGED_IN) return null;
        panelComponent.getChildren().clear();
        panelComponent.getChildren().add(TitleComponent.builder().text("TRAP HOUSE Events").color(new Color(237, 190, 102)).build());
        addEvent(false);
        addEvent(true);
        return super.render(graphics);
    }

    private void addEvent(boolean skill) {
        ClanEvent event = plugin.getEvent(skill);
        String target = event == null ? (plugin.isEventChecked(skill) ? "No active event" : "Loading...") : EventSummary.target(event);
        panelComponent.getChildren().add(LineComponent.builder().left(skill ? "Skill" : "Boss").right(target).build());
        if (event != null) {
            panelComponent.getChildren().add(LineComponent.builder().left(EventSummary.timing(event, Instant.now())).leftColor(Color.LIGHT_GRAY).build());
        }
    }
}
