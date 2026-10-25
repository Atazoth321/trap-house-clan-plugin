package com.traphouse.server;

import com.sun.net.httpserver.HttpServer;
import java.net.*;
import java.net.http.*;
import java.sql.SQLException;
import org.junit.*;
import static org.junit.Assert.*;

public class HttpTest {
    private HttpServer server;
    private String origin;
    private final HttpClient http = HttpClient.newHttpClient();

    @Before public void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        RankingServer service = new RankingServer(() -> { throw new SQLException("unavailable"); });
        server.createContext("/", service::handle);
        server.start();
        origin = "http://127.0.0.1:" + server.getAddress().getPort();
    }
    @After public void stop() { server.stop(0); }

    private HttpResponse<String> post(String type, String body) throws Exception {
        return http.send(HttpRequest.newBuilder(URI.create(origin + "/v1/events"))
            .header("Content-Type", type).POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
    }

    @Test public void healthAndErrorsAreJsonAndNotCached() throws Exception {
        HttpResponse<String> health = http.send(HttpRequest.newBuilder(URI.create(origin + "/health")).build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(200, health.statusCode());
        assertEquals("no-store", health.headers().firstValue("Cache-Control").get());
        HttpResponse<String> failure = post("application/json", "{}");
        assertEquals(503, failure.statusCode());
        assertFalse(failure.body().contains("SQLException"));
    }

    @Test public void bodySizeTypeAndShapeAreEnforcedBeforeDatabaseAccess() throws Exception {
        assertEquals(415, post("text/plain", "{}").statusCode());
        assertEquals(400, post("application/json", "[]").statusCode());
        assertEquals(400, post("application/json", "{").statusCode());
        assertEquals(413, post("application/json", "x".repeat(16385)).statusCode());
    }

    @Test public void readinessFailsWhenDatabaseIsUnavailable() throws Exception {
        HttpResponse<String> response = http.send(HttpRequest.newBuilder(URI.create(origin + "/ready")).build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(503, response.statusCode());
        assertFalse(response.body().contains("SQLException"));
    }
}
