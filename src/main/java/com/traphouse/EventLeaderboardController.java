package com.traphouse;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.IntSupplier;
import javax.swing.SwingUtilities;

/** All state changes occur on Swing's event thread; HTTP work runs on the executor. */
final class EventLeaderboardController {
    private final WiseOldManClient client;
    private final ScheduledExecutorService executor;
    private final IntSupplier competitionId;
    private final boolean skillEvent;
    private TrapHousePanel panel;
    private Future<?> request;
    private volatile ClanEvent event;
    private volatile boolean checked;
    private boolean active = true;
    private long generation;
    private long lastRefresh;
    private String fetchedAt;

    EventLeaderboardController(WiseOldManClient client, ScheduledExecutorService executor, IntSupplier competitionId, boolean skillEvent) {
        this.client = client;
        this.executor = executor;
        this.competitionId = competitionId;
        this.skillEvent = skillEvent;
    }

    void setPanel(TrapHousePanel panel) {
        this.panel = panel;
        if (checked) panel.updateEvent(event, fetchedAt);
        if (request != null) panel.setLeaderboardLoading();
    }

    ClanEvent getEvent() { return event; }

    boolean isChecked() { return checked; }

    void refresh() {
        if (!active || request != null || panel == null) return;
        if (lastRefresh != 0 && System.nanoTime() - lastRefresh < TimeUnit.SECONDS.toNanos(30)) return;
        lastRefresh = System.nanoTime();
        long version = ++generation;
        panel.setLeaderboardLoading();
        int id = competitionId.getAsInt();
        request = executor.submit(() -> {
            try {
                ClanEvent fetched = client.fetchEvent(id, skillEvent);
                SwingUtilities.invokeLater(() -> received(version, fetched, null));
            } catch (Exception exception) {
                String error = exception instanceof java.io.IOException || exception instanceof IllegalArgumentException
                    ? exception.getMessage() : "Could not read Wise Old Man event scores.";
                SwingUtilities.invokeLater(() -> received(version, null, error));
            }
        });
    }

    private void received(long version, ClanEvent fetched, String error) {
        if (!active || version != generation) return;
        request = null;
        if (error != null) panel.setLeaderboardError(error);
        else {
            event = fetched;
            checked = true;
            fetchedAt = "Fetched " + LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"));
            panel.updateEvent(event, fetchedAt);
        }
    }

    void reset() {
        ++generation;
        if (request != null) request.cancel(true);
        request = null;
        event = null;
        checked = false;
        lastRefresh = 0;
    }

    void stop() { active = false; reset(); panel = null; }
}
