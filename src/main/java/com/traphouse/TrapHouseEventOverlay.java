package com.traphouse;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.time.Instant;
import java.util.Locale;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.ComponentConstants;
import net.runelite.client.ui.overlay.components.PanelComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

final class TrapHouseEventOverlay extends OverlayPanel {
    private final Client client;
    private final TrapHouseConfig config;
    private final TrapHouseClanPlugin plugin;
    private final PanelComponent bossSection = createSection();
    private final PanelComponent skillSection = createSection();

    @Inject
    TrapHouseEventOverlay(Client client, TrapHouseConfig config, TrapHouseClanPlugin plugin) {
        super(plugin);
        this.client = client;
        this.config = config;
        this.plugin = plugin;
        setPosition(OverlayPosition.TOP_LEFT);
        panelComponent.setPreferredSize(new Dimension(230, 0));
        panelComponent.setBackgroundColor(ComponentConstants.STANDARD_BACKGROUND_COLOR);
        panelComponent.setGap(new Point(0, 12));
    }

    @Override public Dimension render(Graphics2D graphics) {
        if (!config.showEventOverlay() || (!config.showBossOverlay() && !config.showSkillOverlay())
            || client.getGameState() != GameState.LOGGED_IN) return null;
        panelComponent.getChildren().clear();
        panelComponent.getChildren().add(TitleComponent.builder().text("TRAP HOUSE Events").color(new Color(237, 190, 102)).build());
        if (config.showBossOverlay()) addEvent(false);
        if (config.showSkillOverlay()) addEvent(true);
        return super.render(graphics);
    }

    private static PanelComponent createSection() {
        PanelComponent section = new PanelComponent();
        section.setBackgroundColor(null);
        section.setBorder(new Rectangle());
        section.setGap(new Point(0, 3));
        return section;
    }

    private void addEvent(boolean skill) {
        PanelComponent section = skill ? skillSection : bossSection;
        section.getChildren().clear();
        ClanEvent event = plugin.getEvent(skill);
        String target = event == null ? (plugin.isEventChecked(skill) ? "No active event" : "Loading...") : EventSummary.target(event);
        section.getChildren().add(LineComponent.builder().left(skill ? "SKILL EVENT" : "BOSS EVENT")
            .leftColor(new Color(237, 190, 102)).right(target).build());
        if (event != null) {
            section.getChildren().add(LineComponent.builder().left(EventSummary.timing(event, Instant.now())).leftColor(Color.LIGHT_GRAY).build());
            if (config.showOverlayLeaders()) {
                for (int index = 0; index < Math.min(3, event.scores.size()); index++) {
                    LeaderboardEntry entry = event.scores.get(index);
                    section.getChildren().add(LineComponent.builder().left((index + 1) + ". " + net.runelite.client.util.Text.removeTags(entry.name))
                        .right(String.format(Locale.US, "%,d %s", entry.score, skill ? "XP" : "kills")).build());
                }
            }
        }
        panelComponent.getChildren().add(section);
    }
}
