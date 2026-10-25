package com.traphouse;

import com.google.inject.Provides;
import javax.inject.Inject;
import java.awt.image.BufferedImage;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import javax.swing.SwingUtilities;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.util.ImageUtil;
import net.runelite.api.Client;
import net.runelite.api.ChatMessageType;
import net.runelite.api.GameState;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.Text;

@PluginDescriptor(name="Trap House Clan Plugin", description="TRAP HOUSE boss and Skill of the Month Wise Old Man event leaderboards", tags={"clan","events","boss","skills","traphouse","leaderboard"})
public class TrapHouseClanPlugin extends Plugin {
    @Inject private ClientToolbar toolbar;
    @Inject private TrapHouseConfig config;
    @Inject private WiseOldManClient wiseOldMan;
    @Inject private ScheduledExecutorService executor;
    @Inject private Client client;
    @Inject private OverlayManager overlayManager;
    @Inject private TrapHouseEventOverlay eventOverlay;
    private NavigationButton navigationButton;
    private TrapHouseDashboard dashboard;
    private volatile EventLeaderboardController bossBoard;
    private volatile EventLeaderboardController skillBoard;
    private boolean loginSession;
    private boolean bossAnnounced;
    private boolean skillAnnounced;
    private ScheduledFuture<?> refreshTask;
    private boolean active;
    private String playerName;
    private EventAlerts bossAlerts;
    private EventAlerts skillAlerts;

    @Provides TrapHouseConfig provideConfig(ConfigManager manager) {
        return manager.getConfig(TrapHouseConfig.class);
    }

    @Override protected void startUp() {
        loginSession = false;
        playerName = null;
        bossAlerts = new EventAlerts();
        skillAlerts = new EventAlerts();
        overlayManager.add(eventOverlay);
        SwingUtilities.invokeLater(() -> {
            active = true;
            bossBoard = new EventLeaderboardController(wiseOldMan, executor, config::competitionId, false);
            skillBoard = new EventLeaderboardController(wiseOldMan, executor, config::skillCompetitionId, true);
            createPanel(0);
            refreshBoth();
        });
        refreshTask = executor.scheduleWithFixedDelay(() -> SwingUtilities.invokeLater(this::refreshBoth), 5, 5, TimeUnit.MINUTES);
    }

    private void createPanel(int selectedTab) {
        TrapHousePanel bossPanel = new TrapHousePanel(config, bossBoard::refresh, false);
        TrapHousePanel skillPanel = new TrapHousePanel(config, skillBoard::refresh, true);
        bossBoard.setPanel(bossPanel);
        skillBoard.setPanel(skillPanel);
        dashboard = new TrapHouseDashboard(bossPanel, skillPanel, selectedTab);
        BufferedImage icon = ImageUtil.loadImageResource(getClass(), "icon.png");
        navigationButton = NavigationButton.builder().tooltip("TRAP HOUSE").icon(icon).priority(6).panel(dashboard).build();
        toolbar.addNavigation(navigationButton);
    }

    private void refreshBoth() {
        if (!active) return;
        bossBoard.refresh();
        skillBoard.refresh();
    }

    @Override protected void shutDown() {
        overlayManager.remove(eventOverlay);
        loginSession = false;
        if (refreshTask != null) refreshTask.cancel(false);
        refreshTask = null;
        SwingUtilities.invokeLater(() -> {
            active = false;
            bossBoard.stop();
            skillBoard.stop();
            if (navigationButton != null) toolbar.removeNavigation(navigationButton);
            navigationButton = null;
            dashboard = null;
        });
    }

    @Subscribe public void onConfigChanged(ConfigChanged event) {
        if (!"traphouse".equals(event.getGroup())) return;
        if (!"competitionId".equals(event.getKey()) && !"skillCompetitionId".equals(event.getKey())) return;
        SwingUtilities.invokeLater(() -> {
            if (!active) return;
            if ("competitionId".equals(event.getKey())) bossBoard.reset();
            if ("skillCompetitionId".equals(event.getKey())) skillBoard.reset();
            int tab = dashboard.selectedTab();
            toolbar.removeNavigation(navigationButton);
            createPanel(tab);
            refreshBoth();
        });
    }

    ClanEvent getEvent(boolean skill) {
        EventLeaderboardController board = skill ? skillBoard : bossBoard;
        return board == null ? null : board.getEvent();
    }

    boolean isEventChecked(boolean skill) {
        EventLeaderboardController board = skill ? skillBoard : bossBoard;
        return board != null && board.isChecked();
    }

    @Subscribe public void onGameStateChanged(GameStateChanged event) {
        // Hopping and loading stay in the same session; returning to login resets it.
        if (event.getGameState() == GameState.LOGIN_SCREEN) {
            loginSession = false;
            updatePlayer(null);
        }
    }

    @Subscribe public void onGameTick(GameTick tick) {
        if (client.getGameState() != GameState.LOGGED_IN) return;
        updatePlayer(client.getLocalPlayer() == null ? null : client.getLocalPlayer().getName());
        if (!loginSession) {
            loginSession = true;
            bossAnnounced = !config.announceOnLogin();
            skillAnnounced = !config.announceOnLogin();
            bossAlerts.beginSession();
            skillAlerts.beginSession();
            SwingUtilities.invokeLater(this::refreshBoth);
        }
        if (config.announceOnLogin() && !bossAnnounced && isEventChecked(false)) {
            announceEvent(false);
            bossAnnounced = true;
        }
        if (config.announceOnLogin() && !skillAnnounced && isEventChecked(true)) {
            announceEvent(true);
            skillAnnounced = true;
        }
        pollAlerts(false, bossAlerts);
        pollAlerts(true, skillAlerts);
    }

    private void updatePlayer(String name) {
        if (java.util.Objects.equals(playerName, name)) return;
        playerName = name;
        SwingUtilities.invokeLater(() -> {
            if (!active) return;
            bossBoard.updatePlayer(name);
            skillBoard.updatePlayer(name);
        });
    }

    private void pollAlerts(boolean skill, EventAlerts alerts) {
        if (!isEventChecked(skill)) return;
        boolean automatic = (skill ? config.skillCompetitionId() : config.competitionId()) == 0;
        for (String message : alerts.poll(getEvent(skill), java.time.Instant.now(),
            config.newEventAlerts() && automatic, config.eventEndingReminders())) {
            client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", "[Trap House] " + (skill ? "Skill: " : "Boss: ") + message, null);
        }
    }

    private void announceEvent(boolean skill) {
        ClanEvent event = getEvent(skill);
        String kind = skill ? "Skill event" : "Boss event";
        String message = event == null ? "[Trap House] " + kind + ": no active event."
            : "[Trap House] " + kind + ": " + Text.removeTags(event.title) + " — "
                + EventSummary.target(event) + " (" + EventSummary.timing(event, java.time.Instant.now()) + ").";
        client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", message, null);
    }
}
