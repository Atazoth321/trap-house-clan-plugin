package com.traphouse.server;

import com.google.gson.*;
import java.net.URI;
import java.net.http.*;
import java.sql.*;
import java.time.Duration;
import java.util.UUID;

/** Transactional outbox, at-least-once delivery. Mentions are always disabled. */
final class DiscordWorker implements Runnable {
    interface Connections { Connection open() throws SQLException; }
    private final Connections connections;
    private final URI webhook;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5))
        .followRedirects(HttpClient.Redirect.NEVER).build();

    DiscordWorker(Connections connections, String url) {
        this.connections = connections;
        webhook = validate(url);
    }

    static URI validate(String url) {
        URI uri = URI.create(url);
        if (!"https".equals(uri.getScheme()) || !"discord.com".equals(uri.getHost())
            || uri.getPort() != -1 || uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null
            || !uri.getPath().matches("/api/webhooks/[0-9]+/[A-Za-z0-9_-]+")) {
            throw new IllegalArgumentException("Use an HTTPS discord.com webhook URL");
        }
        return uri;
    }

    static String payload(String content) {
        JsonObject body = new JsonObject();
        body.addProperty("content", content);
        JsonObject mentions = new JsonObject(); mentions.add("parse", new JsonArray());
        body.add("allowed_mentions", mentions);
        return new Gson().toJson(body);
    }

    @Override public void run() {
        try (Connection c = connections.open()) {
            c.setAutoCommit(false);
            try (PreparedStatement s = RankingStore.query(c, "SELECT id,content,attempts FROM discord_outbox WHERE delivered_at IS NULL AND next_attempt<=now() ORDER BY created_at LIMIT 1 FOR UPDATE SKIP LOCKED"); ResultSet r = s.executeQuery()) {
                if (!r.next()) { c.commit(); return; }
                UUID id = r.getObject(1, UUID.class);
                int attempts = r.getInt(3);
                boolean delivered = false;
                try {
                    HttpRequest request = HttpRequest.newBuilder(webhook).timeout(Duration.ofSeconds(10))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(payload(r.getString(2)))).build();
                    int status = http.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
                    delivered = status >= 200 && status < 300;
                } catch (java.io.IOException failure) {
                    // Persist retry without logging secret webhook URLs or HTTP response bodies.
                } catch (InterruptedException stopped) {
                    Thread.currentThread().interrupt(); return;
                }
                if (delivered) RankingStore.execute(c, "UPDATE discord_outbox SET delivered_at=now(),attempts=attempts+1 WHERE id=?", id);
                else RankingStore.execute(c, "UPDATE discord_outbox SET attempts=attempts+1,next_attempt=now()+(? * interval '1 second') WHERE id=?", Math.min(3600, 60L << Math.min(attempts, 6)), id);
                c.commit();
            }
        } catch (SQLException unavailable) { System.err.println("Discord outbox unavailable; will retry."); }
    }
}
