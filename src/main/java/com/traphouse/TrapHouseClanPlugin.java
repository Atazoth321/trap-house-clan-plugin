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

@PluginDescriptor(name="Trap House Clan Plugin", description="TRAP HOUSE clan rankings, points and Wise Old Man event leaderboards", tags={"clan","events","boss","skills","traphouse","leaderboard","ranks"})
public class TrapHouseClanPlugin extends Plugin {
    @Inject private ClientToolbar toolbar;
    @Inject private TrapHouseConfig config;
    @Inject private WiseOldManClient wiseOldMan;
    @Inject private ScheduledExecutorService executor;
    @Inject private Client client;
    @Inject private OverlayManager overlayManager;
    @Inject private TrapHouseEventOverlay eventOverlay;
    @Inject private RankingClient rankings;
    @Inject private AwardItemImages awardItemImages;
    @Inject private DiscordSignIn discordSignIn;
    private volatile RankingPanel rankingPanel;
    private ScheduledFuture<?> rankingTask;
    private NavigationButton navigationButton;
    private TrapHouseDashboard dashboard;
    private volatile EventLeaderboardController bossBoard;
    private volatile EventLeaderboardController skillBoard;
    private boolean loginSession;
    private boolean bossAnnounced;
    private boolean skillAnnounced;
    private ScheduledFuture<?> refreshTask;
    private boolean active;
    private volatile String playerName;
    private volatile boolean rankingMember;
    private int progressTicks;
    private final PetTracker petTracker=new PetTracker();
    private EventAlerts bossAlerts;
    private EventAlerts skillAlerts;

    @Provides TrapHouseConfig provideConfig(ConfigManager manager) {
        return manager.getConfig(TrapHouseConfig.class);
    }

    @Override protected void startUp() {
        petTracker.reset();
        loginSession = false;
        playerName = null;
        rankingMember = false;
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
        rankingTask = executor.scheduleWithFixedDelay(this::syncRankings, 10, 30, TimeUnit.SECONDS);
    }

    private void createPanel(int selectedTab) {
        if(rankingPanel!=null)rankingPanel.clear("Rebuilding clan sidebar");
        TrapHousePanel bossPanel = new TrapHousePanel(config, bossBoard::refresh, false);
        TrapHousePanel skillPanel = new TrapHousePanel(config, skillBoard::refresh, true);
        bossBoard.setPanel(bossPanel);
        skillBoard.setPanel(skillPanel);
        rankingPanel = new RankingPanel(() -> executor.execute(this::syncRankings), (key, reason) -> {
            if (config.rankingEnabled() && rankingMember) {
                rankings.requestReward(playerName, key, reason);
                executor.execute(this::syncRankings);
            }
        });
        dashboard = new TrapHouseDashboard(bossPanel, skillPanel, selectedTab, rankingPanel);
        rankingPanel.pointsLogLoader((query,done)->executor.execute(()->rankings.pointsLog(config,query,done)));
        rankingPanel.rankEditor((member,body,done)->executor.execute(rankings.rankChange(config,member,body,error->{
            done.accept(error);
            if(error==null)executor.execute(this::syncRankings);
        })));
        rankingPanel.itemArt(awardItemImages);
        rankingPanel.discordLogin(()->discordSignIn.start(config,message->{if(rankingPanel!=null)rankingPanel.loginStatus(message);}));
        rankingPanel.actions(name->{rankings.browse(name);executor.execute(this::syncRankings);},
            (id,join)->{rankings.register(id,join);executor.execute(this::syncRankings);},dashboard::showProfile);
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
        petTracker.reset();
        discordSignIn.cancel();
        overlayManager.remove(eventOverlay);
        loginSession = false;
        if (refreshTask != null) refreshTask.cancel(false);
        if (rankingTask != null) rankingTask.cancel(false);
        rankings.reset();
        refreshTask = null;
        SwingUtilities.invokeLater(() -> {
            active = false;
            bossBoard.stop();
            skillBoard.stop();
            if (navigationButton != null) toolbar.removeNavigation(navigationButton);
            navigationButton = null;
            dashboard = null;
            if(rankingPanel!=null)rankingPanel.clear("Plugin stopped");
            rankingPanel = null;
        });
    }

    @Subscribe public void onConfigChanged(ConfigChanged event) {
        if (!"traphouse".equals(event.getGroup())) return;
        if (event.getKey().startsWith("ranking")) {
            if(!java.util.Set.of("rankingEnabled","rankingUrl","rankingToken").contains(event.getKey()))return;
            if(event.getKey().equals("rankingUrl"))discordSignIn.cancel();
            rankings.reset();
            petTracker.reset();
            SwingUtilities.invokeLater(() -> {
                if (rankingPanel != null) rankingPanel.clear("Ranking settings changed; reconnecting");
            });
            executor.execute(this::syncRankings);
            return;
        }
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
            petTracker.reset();
            rankingMember = false;
            loginSession = false;
            updatePlayer(null);
        }
    }

    @Subscribe public void onGameTick(GameTick tick) {
        if (client.getGameState() != GameState.LOGGED_IN) return;
        net.runelite.api.clan.ClanSettings settings = client.getClanSettings();
        String localName = client.getLocalPlayer() == null ? null : client.getLocalPlayer().getName();
        rankingMember = settings != null && localName != null
            && "trap house".equals(RankingTracker.normalize(settings.getName()))
            && settings.findMember(localName) != null;
        if (config.rankingEnabled() && rankingMember && ++progressTicks >= 50) {
            progressTicks=0;
            rankings.progress(ProgressTracker.collect(client,localName,rankings.probes()));
            petTracker.scan(client,localName,rankings::enqueue);
        }
        updatePlayer(client.getLocalPlayer() == null ? null : client.getLocalPlayer().getName());
        if(rankingMember)for(String message:rankings.pointMessages(config)) {
            if(config.rankingEnabled()&&config.rankingPointMessages())
                client.addChatMessage(ChatMessageType.GAMEMESSAGE,"",message,null);
        }
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
        rankings.reset();
        SwingUtilities.invokeLater(() -> {
            if (!active) return;
            if (rankingPanel != null) rankingPanel.clear(name == null ? "Log in to see clan rankings" : "Loading rankings for " + name);
            bossBoard.updatePlayer(name);
            skillBoard.updatePlayer(name);
        });
        executor.execute(this::syncRankings);
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

    private void syncRankings() {
        RankingPanel panel = rankingPanel;
        if (panel != null) rankings.sync(config, rankingMember ? playerName : null, panel);
    }

    private String trackingPlayer() {
        if (!config.rankingEnabled() || !rankingMember || client.getGameState() != GameState.LOGGED_IN
            || client.getLocalPlayer() == null) return null;
        return client.getLocalPlayer().getName();
    }

    @Subscribe public void onItemContainerChanged(net.runelite.api.events.ItemContainerChanged event) {
        if(event.getContainerId()!=net.runelite.api.InventoryID.INVENTORY.getId()
            &&event.getContainerId()!=net.runelite.api.InventoryID.BANK.getId())return;
        String player=trackingPlayer();
        if(player!=null)petTracker.scan(client,player,rankings::enqueue);
    }

    @Subscribe public void onNpcLootReceived(net.runelite.client.events.NpcLootReceived event) {
        if(RankingTracker.serverLootNpc(event.getNpc().getId())||RankingTracker.expandedLootNpc(event.getNpc().getId()))return;
        String player = trackingPlayer();
        if (player == null) return;
        java.util.Set<Integer> items = new java.util.HashSet<>();
        for (net.runelite.client.game.ItemStack item : event.getItems()) {
            if (items.add(item.getId())) rankings.enqueue(new RankingEvent(player, "LOOT", event.getNpc().getId() + ":" + item.getId()));
        }
    }

    @Subscribe public void onLootReceived(net.runelite.client.plugins.loottracker.LootReceived event) {
        String player=trackingPlayer();
        if(player==null)return;
        for(RankingEvent observation:RankingTracker.containerLoot(player,event))rankings.enqueue(observation);
    }

    @Subscribe public void onServerNpcLoot(net.runelite.client.events.ServerNpcLoot event) {
        String player=trackingPlayer();int npc=event.getComposition().getId();
        if(player==null||!RankingTracker.serverLootNpc(npc))return;
        java.util.Set<Integer> seen=new java.util.HashSet<>();
        for(net.runelite.client.game.ItemStack item:event.getItems())
            if(seen.add(item.getId()))rankings.enqueue(new RankingEvent(player,"LOOT",npc+":"+item.getId()));
    }

    @Subscribe public void onChatMessage(net.runelite.api.events.ChatMessage event) {
        if (event.getType() != ChatMessageType.GAMEMESSAGE && event.getType() != ChatMessageType.SPAM) return;
        String colored = DropChatColors.recolor(event.getMessage(), config);
        if (colored != null) {
            event.getMessageNode().setValue(colored);
            event.getMessageNode().setRuneLiteFormatMessage(colored);
            client.refreshChat();
        }
        String player = trackingPlayer();
        if (player == null || (event.getType() != ChatMessageType.GAMEMESSAGE
            && event.getType() != ChatMessageType.SPAM)) return;
        RankingEvent observation = RankingTracker.chat(player, event.getMessage());
        if (observation != null) rankings.enqueue(observation);
        for(RankingEvent milestone:RankingTracker.clueMilestones(player,event.getMessage()))rankings.enqueue(milestone);
    }
}
