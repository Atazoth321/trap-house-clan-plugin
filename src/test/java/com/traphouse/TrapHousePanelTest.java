package com.traphouse;

import java.awt.Component;
import java.awt.Container;
import java.util.ArrayList;
import java.util.List;
import java.util.Arrays;
import java.util.Collections;
import java.time.Instant;
import javax.swing.JTabbedPane;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;
import net.runelite.client.util.ImageUtil;
import org.junit.Test;
import static org.junit.Assert.*;

public class TrapHousePanelTest {
    @Test
    public void sidebarIconIsPackagedAtPluginResourcePath() {
        assertNotNull(ImageUtil.loadImageResource(TrapHouseClanPlugin.class, "icon.png"));
        assertNotNull(ImageUtil.loadImageResource(TrapHousePanel.class, "logo.png"));
    }

    @Test
    public void leaderboardDisplaysScoresAndCommunityLinkButtons() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            TrapHousePanel panel = new TrapHousePanel(new TrapHouseConfig() {}, () -> {});
            panel.updateLeaderboard(Arrays.asList(new LeaderboardEntry("Clan member", 12),
                new LeaderboardEntry("Another member", 7)), "Fetched 12:00");
            assertTrue(hasLabel(panel, "BOSS KILL LEADERBOARD"));
            assertTrue(hasLabel(panel, "Clan member"));
            assertTrue(hasLabel(panel, "7"));
            assertTrue(hasLabel(panel, "12"));
            int links = 0;
            for (Component component : descendants(panel)) {
                if (component instanceof JButton && ((JButton) component).getText().startsWith("Open ")) {
                    assertFalse(component.isEnabled());
                    links++;
                }
            }
            assertEquals(2, links);
        });
    }

    @Test
    public void emptyLeaderboardDoesNotInventKills() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            TrapHousePanel panel = new TrapHousePanel(new TrapHouseConfig() {}, () -> {});
            panel.updateLeaderboard(Collections.emptyList(), "Fetched 12:00");
            assertTrue(hasLabel(panel, "<html><div style='width:126px'>No scored participants yet</div></html>"));
            assertTrue(hasLabel(panel, "WISE OLD MAN / GROUP 2504"));
            assertFalse(hasLabel(panel, "NEARBY BOSS DEATHS"));
        });
    }

    @Test public void failedRefreshPreservesPreviouslyFetchedScores() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            TrapHousePanel panel = new TrapHousePanel(new TrapHouseConfig() {}, () -> {});
            panel.updateLeaderboard(Collections.singletonList(new LeaderboardEntry("Member", 1836)), "Fetched 12:00");
            panel.setLeaderboardLoading();
            panel.setLeaderboardError("Wise Old Man unavailable.");
            assertTrue(hasLabel(panel, "Member"));
            assertTrue(hasLabel(panel, "1,836"));
        });
    }

    @Test public void noCurrentEventClearsPreviousScores() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            TrapHousePanel panel = new TrapHousePanel(new TrapHouseConfig() {}, () -> {});
            panel.updateLeaderboard(Collections.singletonList(new LeaderboardEntry("Member", 100)), "Fetched 12:00");
            panel.updateEvent(null, "Fetched 12:05");
            assertFalse(hasLabel(panel, "Member"));
            assertTrue(hasLabel(panel, "No active boss event"));
            assertTrue(hasLabel(panel, "KILLS GAINED DURING EVENT"));
        });
    }

    @Test public void skillTabShowsXpGainsAndFixedPrizes() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            TrapHousePanel skill = new TrapHousePanel(new TrapHouseConfig() {}, () -> {}, true);
            ClanEvent event = new ClanEvent(1, "Skill of the Month: Sailing", "sailing", Instant.now().minusSeconds(60),
                Instant.now().plusSeconds(60), Collections.singletonList(new LeaderboardEntry("Member", 1234567)));
            skill.updateEvent(event, "Fetched 12:00");
            assertTrue(hasLabel(skill, "SKILL OF THE MONTH"));
            assertTrue(hasLabel(skill, "SKILL XP LEADERBOARD"));
            assertTrue(hasLabel(skill, "1.2M"));
            assertTrue(hasLabel(skill, "XP"));
            assertTrue(hasLabel(skill, "10,000,000 GP"));
            assertTrue(hasLabel(skill, "5,000,000 GP"));
            assertTrue(hasLabel(skill, "2,500,000 GP"));
            assertFalse(hasLabel(skill, "KILLS"));
            TrapHouseDashboard dashboard = new TrapHouseDashboard(new TrapHousePanel(new TrapHouseConfig() {}, () -> {}), skill, 1);
            assertEquals(1, dashboard.selectedTab());
            for (Component component : descendants(dashboard)) {
                if (component instanceof JTabbedPane) assertEquals(2, ((JTabbedPane) component).getTabCount());
            }
        });
    }

    private static boolean hasLabel(TrapHousePanel panel, String text) {
        for (Component component : descendants(panel)) {
            if (component instanceof JLabel && text.equals(((JLabel) component).getText())) return true;
        }
        return false;
    }

    private static List<Component> descendants(Container container) {
        List<Component> components = new ArrayList<>();
        for (Component component : container.getComponents()) {
            components.add(component);
            if (component instanceof Container) components.addAll(descendants((Container) component));
        }
        return components;
    }
}
