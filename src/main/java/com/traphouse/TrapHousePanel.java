package com.traphouse;

import java.awt.*;
import java.util.Locale;
import java.util.List;
import java.util.Collections;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.util.ImageUtil;
import net.runelite.client.util.LinkBrowser;

public class TrapHousePanel extends PluginPanel {
    private static final String DISCORD_URL = "";
    private static final String WEBSITE_URL = "https://traphouseosrs.net/";
    private static final int FIRST_PRIZE = 10_000_000;
    private static final int SECOND_PRIZE = 5_000_000;
    private static final int THIRD_PRIZE = 2_500_000;
    private static final Color WHITE = new Color(239, 241, 246);
    private static final Color MUTED = new Color(143, 151, 167);
    private static final Color RED = new Color(255, 77, 91);
    private static final Color GOLD = new Color(237, 190, 102);
    private final JPanel leaderboardRows = new JPanel();
    private final JLabel leaderboardStatus = wrapped("Connecting to Wise Old Man...", 10, MUTED);
    private final JButton refreshButton = button("Refresh leaderboard", false);
    private JPanel leaderboardCard;
    private final JLabel eventHeading = label("BOSS OF THE WEEK", 10, RED);
    private final JLabel eventStatus = label("", 9, MUTED);
    private final JLabel eventTitle = wrapped("Finding current event...", 17, WHITE);
    private final JLabel eventBoss = wrapped("From Wise Old Man", 14, GOLD);
    private final JLabel eventDates = wrapped("", 9, MUTED);
    private final boolean skillEvent;

    TrapHousePanel(TrapHouseConfig config, Runnable refresh) {
        this(config, refresh, false);
    }

    TrapHousePanel(TrapHouseConfig config, Runnable refresh, boolean skillEvent) {
        this.skillEvent = skillEvent;
        setBackground(new Color(15, 17, 22));
        setBorder(new EmptyBorder(12, 10, 16, 10));
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));

        JPanel header = card(true);
        header.add(label("THE CLAN / THE GRIND", 9, new Color(255, 160, 165)));
        gap(header, 9);
        JPanel brand = new JPanel(new BorderLayout(8, 0));
        brand.setOpaque(false);
        brand.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel logo = new JLabel(new ImageIcon(ImageUtil.loadImageResource(TrapHousePanel.class, "logo.png")
            .getScaledInstance(56, 56, Image.SCALE_SMOOTH)));
        logo.setToolTipText("TRAP HOUSE clan logo");
        brand.add(logo, BorderLayout.WEST);
        brand.add(label("<html>TRAP<br>HOUSE<span style='color:#ff4d5b'>.</span></html>", 25, WHITE), BorderLayout.CENTER);
        brand.setMaximumSize(new Dimension(Integer.MAX_VALUE, brand.getPreferredSize().height));
        header.add(brand);
        gap(header, 10);
        header.add(label("PvM. Skill. TRAP.", 12, new Color(222, 184, 191)));
        section(header);

        JPanel event = card(false);
        event.add(eventHeading);
        gap(event, 8);
        event.add(eventTitle);
        gap(event, 4);
        event.add(eventStatus);
        gap(event, 14);
        event.add(label(skillEvent ? "FEATURED SKILL" : "YOUR TARGET", 9, MUTED));
        gap(event, 4);
        event.add(eventBoss);
        gap(event, 8);
        event.add(eventDates);
        section(event);

        leaderboardCard = card(false);
        leaderboardCard.add(label(skillEvent ? "SKILL XP LEADERBOARD" : "BOSS KILL LEADERBOARD", 10, GOLD));
        gap(leaderboardCard, 5);
        leaderboardCard.add(label("WISE OLD MAN / GROUP 2504", 9, MUTED));
        gap(leaderboardCard, 4);
        leaderboardCard.add(label(skillEvent ? "XP GAINED DURING EVENT" : "KILLS GAINED DURING EVENT", 9, MUTED));
        gap(leaderboardCard, 14);
        leaderboardRows.setOpaque(false);
        leaderboardRows.setAlignmentX(Component.LEFT_ALIGNMENT);
        leaderboardRows.setLayout(new BoxLayout(leaderboardRows, BoxLayout.Y_AXIS));
        leaderboardCard.add(leaderboardRows);
        gap(leaderboardCard, 10);
        leaderboardCard.add(leaderboardStatus);
        gap(leaderboardCard, 10);
        refreshButton.setToolTipText("Refresh Wise Old Man data (at most once every 30 seconds)");
        refreshButton.addActionListener(e -> refresh.run());
        leaderboardCard.add(refreshButton);
        renderScores(Collections.emptyList(), "Loading scores");
        section(leaderboardCard);
        JPanel prizes = card(false);
        prizes.add(label("THE PRIZE POOL", 10, GOLD));
        gap(prizes, 12);
        prizes.add(prize("01", "FIRST PLACE", FIRST_PRIZE, GOLD));
        gap(prizes, 10);
        prizes.add(prize("02", "SECOND PLACE", SECOND_PRIZE, new Color(185, 197, 215)));
        gap(prizes, 10);
        prizes.add(prize("03", "THIRD PLACE", THIRD_PRIZE, new Color(207, 153, 120)));
        section(prizes);

        add(linkButton("Open Discord", DISCORD_URL, true));
        gap(this, 7);
        add(linkButton("Open Clan Website", WEBSITE_URL, false));
        gap(this, 14);
        add(label("YOUR GRIND. YOUR COMMUNITY.", 9, MUTED));
        gap(this, 4);
        add(label("Auto-refresh / every 5 minutes", 9, MUTED));
        if (skillEvent) eventHeading.setText("SKILL OF THE MONTH");
    }

    void setLeaderboardLoading() {
        refreshButton.setEnabled(false);
        setStatus("Refreshing Wise Old Man...");
    }

    void setLeaderboardError(String error) {
        refreshButton.setEnabled(true);
        setStatus(error + " Existing scores may be stale.");
    }

    void updateLeaderboard(List<LeaderboardEntry> scores, String fetchedAt) {
        renderScores(scores, "No scored participants yet");
        refreshButton.setEnabled(true);
        setStatus(fetchedAt + " / WOM snapshots");
    }

    void updateEvent(ClanEvent event, String fetchedAt) {
        if (event == null) {
            eventStatus.setText("");
            eventTitle.setFont(new Font("SansSerif", Font.BOLD, 16));
            eventTitle.setText(skillEvent ? "No active skill event" : "No active boss event");
            eventBoss.setText("Awaiting competition");
            eventDates.setText("");
            renderScores(Collections.emptyList(), skillEvent ? "No active skill event on Wise Old Man" : "No active boss event on Wise Old Man");
            refreshButton.setEnabled(true);
            setStatus("Group 2504 / checked for events");
        } else {
            Instant now = Instant.now();
            eventTitle.setFont(new Font("SansSerif", Font.BOLD, 17));
            eventStatus.setText(now.isBefore(event.startsAt) ? "UPCOMING EVENT" : !now.isBefore(event.endsAt) ? "ENDED EVENT" : "ACTIVE EVENT");
            eventTitle.setText(wrapped(event.title, 17, WHITE).getText());
            String boss = event.metric.replace('_', ' ');
            boss = Character.toUpperCase(boss.charAt(0)) + boss.substring(1);
            eventBoss.setText(wrapped(boss, 14, GOLD).getText());
            DateTimeFormatter dates = DateTimeFormatter.ofPattern("MMM d, HH:mm 'UTC'", Locale.US).withZone(ZoneOffset.UTC);
            eventDates.setText(wrapped(dates.format(event.startsAt) + " - " + dates.format(event.endsAt), 9, MUTED).getText());
            updateLeaderboard(event.scores, fetchedAt);
        }
        // Dynamic titles can wrap; release the event card's previous height.
        Container eventCard = eventTitle.getParent();
        eventCard.invalidate();
        ((JPanel) eventCard).setMaximumSize(new Dimension(Integer.MAX_VALUE, eventCard.getPreferredSize().height));
        invalidate();
        revalidate();
        repaint();
    }

    private void setStatus(String text) {
        leaderboardStatus.setText("<html><div style='width:126px'>" + text + "</div></html>");
        resizeLeaderboard();
    }

    private void renderScores(List<LeaderboardEntry> scores, String emptyText) {
        leaderboardRows.removeAll();
        if (scores.isEmpty()) {
            leaderboardRows.add(wrapped(emptyText, 12, MUTED));
        } else {
            for (int index = 0; index < Math.min(10, scores.size()); index++) {
                LeaderboardEntry entry = scores.get(index);
                String score = skillEvent && entry.score >= 1000000 ? String.format(Locale.US, "%.1fM", entry.score / 1000000.0)
                    : skillEvent && entry.score >= 10000 ? String.format(Locale.US, "%.1fK", entry.score / 1000.0)
                    : String.format(Locale.US, "%,d", entry.score);
                JPanel row = leaderboardRow(index + 1, entry.name, score);
                row.setToolTipText(String.format(Locale.US, "%s: %,d %s gained", entry.name, entry.score, skillEvent ? "XP" : "kills"));
                leaderboardRows.add(row);
                gap(leaderboardRows, 10);
            }
        }
        resizeLeaderboard();
    }

    private void resizeLeaderboard() {
        leaderboardRows.invalidate();
        leaderboardRows.setMaximumSize(new Dimension(Integer.MAX_VALUE, leaderboardRows.getPreferredSize().height));
        leaderboardCard.invalidate();
        leaderboardCard.setMaximumSize(new Dimension(Integer.MAX_VALUE, leaderboardCard.getPreferredSize().height));
        invalidate();
        revalidate();
        repaint();
    }

    private JPanel leaderboardRow(int rank, String name, String kills) {
        Color color = rank == 1 ? GOLD : rank == 2 ? new Color(185, 197, 215) : rank == 3 ? new Color(207, 153, 120) : MUTED;
        JPanel row = new JPanel(new BorderLayout(8, 0));
        row.setOpaque(false);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.add(label(String.format(Locale.US, "%02d", rank), 18, color), BorderLayout.WEST);
        JLabel player = label(name, 12, WHITE);
        player.putClientProperty("html.disable", Boolean.TRUE);
        player.setToolTipText(name);
        row.add(player, BorderLayout.CENTER);
        JPanel score = new JPanel();
        score.setOpaque(false);
        score.setLayout(new BoxLayout(score, BoxLayout.Y_AXIS));
        score.add(label(kills, 15, color));
        score.add(label(skillEvent ? "XP" : "KILLS", 8, MUTED));
        row.add(score, BorderLayout.EAST);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, row.getPreferredSize().height));
        return row;
    }

    private void section(JPanel panel) {
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, panel.getPreferredSize().height));
        add(panel);
        gap(this, 10);
    }

    private static void gap(JPanel panel, int height) { panel.add(Box.createVerticalStrut(height)); }

    private static JPanel card(boolean header) {
        JPanel panel = new Card(header);
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(header ? new EmptyBorder(16, 14, 16, 14) : new EmptyBorder(13, 12, 13, 12));
        return panel;
    }

    private static JPanel prize(String rank, String name, int amount, Color color) {
        JPanel row = new JPanel(new BorderLayout(9, 0));
        row.setOpaque(false);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.add(label(rank, 20, color), BorderLayout.WEST);
        JPanel detail = new JPanel();
        detail.setOpaque(false);
        detail.setLayout(new BoxLayout(detail, BoxLayout.Y_AXIS));
        detail.add(label(name, 9, MUTED));
        JLabel value = label(String.format(Locale.US, "%,d GP", amount), 13, WHITE);
        detail.add(value);
        row.setToolTipText(value.getText());
        row.add(detail, BorderLayout.CENTER);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, row.getPreferredSize().height));
        return row;
    }

    private static JLabel wrapped(String text, int size, Color color) {
        String escaped = text == null ? "" : text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
        return label("<html><div style='width:126px'>" + escaped + "</div></html>", size, color);
    }

    private static JLabel label(String text, int size, Color color) {
        JLabel label = new JLabel(text);
        label.setFont(new Font("SansSerif", Font.BOLD, size));
        label.setForeground(color);
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        return label;
    }

    private static JButton button(String text, boolean primary) {
        JButton button = new JButton(text);
        button.setFont(new Font("SansSerif", Font.BOLD, 12));
        button.setForeground(primary ? WHITE : new Color(199, 206, 218));
        button.setBackground(primary ? new Color(167, 44, 59) : new Color(33, 37, 46));
        button.setFocusPainted(false);
        button.putClientProperty("JButton.buttonType", "roundRect");
        button.setAlignmentX(Component.LEFT_ALIGNMENT);
        button.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));
        button.setPreferredSize(new Dimension(180, 34));
        return button;
    }

    private JButton linkButton(String text, String url, boolean primary) {
        JButton button = button(text, primary);
        button.setEnabled(url != null && url.matches("https://.+"));
        if (!button.isEnabled()) button.setToolTipText("Community link coming soon");
        button.addActionListener(e -> LinkBrowser.browse(url));
        return button;
    }
    private static class Card extends JPanel {
        private final boolean header;
        Card(boolean header) { this.header = header; setOpaque(false); }
        @Override protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            if (header) g.setPaint(new GradientPaint(0, 0, new Color(85, 27, 40), getWidth(), getHeight(), new Color(29, 24, 34)));
            else g.setColor(new Color(24, 27, 34));
            g.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 16, 16);
            g.setColor(header ? new Color(120, 48, 61) : new Color(43, 47, 57));
            g.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 16, 16);
            if (header) { g.setColor(RED); g.fillRoundRect(getWidth() - 35, 18, 20, 3, 3, 3); }
            g.dispose();
            super.paintComponent(graphics);
        }
    }
}
