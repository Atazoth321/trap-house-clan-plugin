package com.traphouse;

import com.google.gson.*;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.SwingUtilities;
import okhttp3.*;
import okio.Buffer;
import org.junit.Test;
import static org.junit.Assert.*;

public class RankingClientTest {
    @Test public void pointsLogUsesAuthenticatedReadOnlyRequestAndDropsResetResponse()throws Exception {
        JsonObject query=JSON.fromJson("{\"player\":\"Raider\",\"category\":\"Pets\",\"page\":1}",JsonObject.class);
        RankingClient[] client=new RankingClient[1];AtomicInteger delivered=new AtomicInteger();AtomicBoolean reset=new AtomicBoolean();
        client[0]=new RankingClient(new OkHttpClient.Builder().addInterceptor(chain->{
            assertEquals("GET",chain.request().method());assertEquals("Bearer member-only-token",chain.request().header("Authorization"));assertEquals("/v1/points-log",chain.request().url().encodedPath());
            assertEquals("Raider",chain.request().url().queryParameter("player"));assertEquals("Pets",chain.request().url().queryParameter("category"));
            if(reset.get())client[0].reset();return response(chain,200,"{\"player\":\"Raider\",\"entries\":[]}");
        }).build(),JSON);
        client[0].pointsLog(config,query,data->{assertEquals("Raider",data.get("player").getAsString());delivered.incrementAndGet();});SwingUtilities.invokeAndWait(()->{});assertEquals(1,delivered.get());
        reset.set(true);client[0].pointsLog(config,query,data->delivered.incrementAndGet());SwingUtilities.invokeAndWait(()->{});assertEquals(1,delivered.get());
    }
    @Test public void memberClickFetchesSelectedPublicProfileWithTheViewersCredential()throws Exception {
        List<String> requested=new ArrayList<>();
        RankingPanel panel=panel();AtomicInteger opened=new AtomicInteger();
        SwingUtilities.invokeAndWait(()->panel.actions(name->{},(id,join)->{},opened::incrementAndGet));
        RankingClient client=new RankingClient(new OkHttpClient.Builder().addInterceptor(chain->{
            assertEquals("Bearer member-only-token",chain.request().header("Authorization"));
            assertEquals("GET",chain.request().method());
            if(chain.request().url().encodedPath().equals("/v1/members")) {
                requested.add(chain.request().url().queryParameter("player"));
                return response(chain,200,"{\"profile\":{\"name\":\"TRAP Raider\",\"points\":250}}");
            }
            return response(chain,200,"{\"profile\":{\"name\":\"Member\",\"points\":50}}");
        }).build(),JSON);
        client.browse("TRAP Raider");client.sync(config,"Member",panel);SwingUtilities.invokeAndWait(()->{});
        assertEquals(Collections.singletonList("TRAP Raider"),requested);assertEquals(1,opened.get());
    }
    @Test public void olderMemberResponseCannotReplaceANewerMemberSelection()throws Exception {
        RankingPanel panel=panel();AtomicInteger opened=new AtomicInteger();
        SwingUtilities.invokeAndWait(()->panel.actions(name->{},(id,join)->{},opened::incrementAndGet));
        RankingClient[] client=new RankingClient[1];
        client[0]=new RankingClient(new OkHttpClient.Builder().addInterceptor(chain->{
            if(chain.request().url().encodedPath().equals("/v1/members")) {
                String selected=chain.request().url().queryParameter("player");
                if(selected.equals("First"))client[0].browse("Second");
                return response(chain,200,"{\"profile\":{\"name\":\""+selected+"\",\"points\":100}}");
            }
            return response(chain,200,"{\"profile\":{\"name\":\"Member\"}}");
        }).build(),JSON);
        client[0].browse("First");client[0].sync(config,"Member",panel);SwingUtilities.invokeAndWait(()->{});assertEquals(0,opened.get());
        client[0].sync(config,"Member",panel);SwingUtilities.invokeAndWait(()->{});assertEquals(1,opened.get());
    }
    private static final Gson JSON = new Gson();
    private final TrapHouseConfig config = new TrapHouseConfig() {
        public boolean rankingEnabled() { return true; }
        public String rankingUrl() { return "https://clan.example"; }
        public String rankingToken() { return "member-only-token"; }
    };

    private Response response(Interceptor.Chain chain, int code, String body) {
        return new Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(code)
            .message("test").body(ResponseBody.create(MediaType.parse("application/json"), body)).build();
    }

    @Test public void retriesRetainEventIdsAndUseBearerHeader() throws Exception {
        List<String> ids = new ArrayList<>(); AtomicInteger attempts = new AtomicInteger();
        OkHttpClient http = new OkHttpClient.Builder().addInterceptor(chain -> {
            assertEquals("Bearer member-only-token", chain.request().header("Authorization"));
            if (chain.request().url().encodedPath().equals("/v1/events")) {
                Buffer buffer = new Buffer(); chain.request().body().writeTo(buffer);
                ids.add(JSON.fromJson(buffer.readUtf8(), JsonObject.class).get("id").getAsString());
                return response(chain, attempts.incrementAndGet() == 1 ? 503 : 200, "{}");
            }
            return response(chain, 503, "{}");
        }).build();
        RankingClient client = new RankingClient(http, JSON);
        RankingEvent event = new RankingEvent("Member", "LOOT", "1:2"); client.enqueue(event);
        RankingPanel panel = panel();
        client.sync(config, "Member", panel); client.sync(config, "Member", panel); client.sync(config, "Member", panel);
        assertEquals(Arrays.asList(event.id, event.id), ids);
        SwingUtilities.invokeAndWait(() -> {});
    }

    @Test public void resetAndDifferentAccountsNeverDrainOldQueuedEvents() throws Exception {
        AtomicInteger posts = new AtomicInteger();
        RankingClient client = new RankingClient(new OkHttpClient.Builder().addInterceptor(chain -> {
            if (chain.request().method().equals("POST")) posts.incrementAndGet();
            return response(chain, 503, "{}");
        }).build(), JSON);
        RankingPanel panel = panel();
        client.enqueue(new RankingEvent("Other", "LOOT", "1:2"));
        client.sync(config, "Member", panel);
        client.enqueue(new RankingEvent("Member", "LOOT", "1:2")); client.reset();
        client.sync(config, "Member", panel);
        assertEquals(0, posts.get());
        SwingUtilities.invokeAndWait(() -> {});
    }

    @Test public void manualRequestRetryIsIdempotentAndResetClearsIt() throws Exception {
        List<String> ids = new ArrayList<>(); AtomicBoolean fail = new AtomicBoolean(true);
        RankingClient client = new RankingClient(new OkHttpClient.Builder().addInterceptor(chain -> {
            if (chain.request().url().encodedPath().equals("/v1/manual-rewards")) {
                Buffer buffer = new Buffer(); chain.request().body().writeTo(buffer);
                JsonObject body = JSON.fromJson(buffer.readUtf8(), JsonObject.class);
                assertFalse(body.has("points")); ids.add(body.get("id").getAsString());
                if (fail.getAndSet(false)) throw new IOException("network interrupted");
                return response(chain, 200, "{}");
            }
            return response(chain, 503, "{}");
        }).build(), JSON);
        RankingPanel panel = panel();
        client.requestReward("Member", "clan event", "Attended");
        client.sync(config, "Member", panel); client.sync(config, "Member", panel); client.sync(config, "Member", panel);
        assertEquals(2, ids.size()); assertEquals(ids.get(0), ids.get(1));
        client.requestReward("Member", "clan event", "Attended"); client.reset(); client.sync(config, "Member", panel);
        assertEquals(2, ids.size());
        SwingUtilities.invokeAndWait(() -> {});
    }

    private RankingPanel panel() throws Exception {
        RankingPanel[] result = new RankingPanel[1]; SwingUtilities.invokeAndWait(() -> result[0] = new RankingPanel(() -> {})); return result[0];
    }
}
