package com.traphouse;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayDeque;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.inject.Inject;
import javax.swing.SwingUtilities;
import okhttp3.*;

/** A bounded in-memory queue. Retries retain the same event ID. No game-thread networking. */
final class RankingClient {
    private final OkHttpClient http;
    private final Gson gson;
    private final ArrayDeque<RankingEvent> queue = new ArrayDeque<>();
    private final AtomicBoolean busy = new AtomicBoolean();
    private long generation;
    private JsonObject manualRequest;
    private String manualPlayer;
    private volatile com.google.gson.JsonArray probes = new com.google.gson.JsonArray();
    private JsonObject progressRequest;
    private String memberRequest;
    private long memberRevision;
    private String registrationPath;
    private final PointAlerts pointAlerts=new PointAlerts();

    synchronized java.util.List<String> pointMessages(){return pointAlerts.drain();}

    com.google.gson.JsonArray probes() { return probes; }
    synchronized void progress(JsonObject values) { progressRequest=values; }
    synchronized void browse(String name) { memberRequest=name;memberRevision++; }
    synchronized void register(String id,boolean join) { java.util.UUID.fromString(id);registrationPath="/v1/clan-events/"+id+(join?"/register":"/cancel"); }

    @Inject RankingClient(OkHttpClient http, Gson gson) {
        this.http = http.newBuilder().followRedirects(false).followSslRedirects(false)
            .callTimeout(15, TimeUnit.SECONDS).build();
        this.gson = gson;
    }

    static String endpoint(String value) {
        URI uri = URI.create(value);
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null
            || uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null
            || !(uri.getPath().isEmpty() || "/".equals(uri.getPath()))) {
            throw new IllegalArgumentException("Ranking URL must be an HTTPS origin");
        }
        return value.replaceAll("/+$", "");
    }

    synchronized void enqueue(RankingEvent event) {
        if (queue.size() < 200) queue.addLast(event);
    }

    synchronized void reset() { generation++; queue.clear(); pointAlerts.reset(); manualRequest = null; manualPlayer = null;progressRequest=null;memberRequest=null;registrationPath=null;probes=new com.google.gson.JsonArray(); }

    synchronized void requestReward(String player, String key, String reason) {
        if (player == null || manualRequest != null) return;
        manualRequest = new JsonObject();
        manualRequest.addProperty("id", java.util.UUID.randomUUID().toString());
        manualRequest.addProperty("key", key); manualRequest.addProperty("reason", reason);
        manualPlayer = player;
    }

    void sync(TrapHouseConfig config, String player, RankingPanel panel) {
        final long epoch;
        synchronized (this) { epoch = generation; }
        if (!config.rankingEnabled() || player == null || config.rankingToken().trim().isEmpty()) {
            SwingUtilities.invokeLater(() -> { synchronized (this) {
                if (epoch == generation) panel.clear("Ranking sync disabled or no logged-in member credential.");
            }});
            return;
        }
        if (!busy.compareAndSet(false, true)) return;
        try {
            String origin = endpoint(config.rankingUrl().trim());
            String token = config.rankingToken().trim();
            boolean baseline;
            synchronized(this){if(epoch!=generation)return;baseline=pointAlerts.cursor()==null;}
            if(baseline)pollPointAlerts(origin,token,player,epoch);
            String actionError=null;
            JsonObject progressBody;String registerPath;
            synchronized(this){if(epoch!=generation)return;progressBody=progressRequest;registerPath=registrationPath;}
            if(progressBody!=null) {
                if(!submit(origin+"/v1/progress",token,progressBody))actionError="Progress definitions changed; refreshing from server";
                synchronized(this){if(epoch==generation&&progressBody==progressRequest)progressRequest=null;}
            }
            if(registerPath!=null) {
                if(!submit(origin+registerPath,token,new JsonObject()))actionError="Registration declined; check event availability";
                synchronized(this){if(epoch==generation&&java.util.Objects.equals(registerPath,registrationPath))registrationPath=null;}
            }
            JsonObject reward;
            synchronized (this) {
                if (epoch != generation) return;
                reward = java.util.Objects.equals(manualPlayer, player) ? manualRequest : null;
            }
            if (reward != null) {
                try (Response response = http.newCall(request(origin + "/v1/manual-rewards", token)
                    .post(RequestBody.create(MediaType.parse("application/json"), gson.toJson(reward))).build()).execute()) {
                    if (response.code() >= 500 || response.code() == 429 || response.code() == 401 || response.code() == 403) throw new IOException("Reward request will retry");
                    synchronized (this) { if (epoch == generation && manualRequest == reward) manualRequest = null; }
                    if (!response.isSuccessful()) throw new IOException("Reward request rejected");
                }
            }
            // Bound each batch so event submission cannot starve profile refresh.
            for (int sent = 0; sent < 20; sent++) {
                RankingEvent event;
                synchronized (this) {
                    if (epoch != generation) return;
                    event = queue.peekFirst();
                }
                if (event == null) break;
                if (!RankingTracker.normalize(player).equals(RankingTracker.normalize(event.player))) {
                    synchronized (this) { queue.remove(event); }
                    continue;
                }
                try (Response response = http.newCall(request(origin + "/v1/events", token)
                    .post(RequestBody.create(MediaType.parse("application/json"), gson.toJson(event))).build()).execute()) {
                    int code = response.code();
                    if (code >= 500 || code == 429) throw new IOException("Retry later");
                    if (code == 401 || code == 403) throw new IOException("Credential or membership rejected");
                    if (!response.isSuccessful() && code != 400 && code != 409 && code != 422) throw new IOException("Request rejected");
                    synchronized (this) { queue.remove(event); }
                }
            }
            HttpUrl url = HttpUrl.parse(origin + "/v1/dashboard").newBuilder().addQueryParameter("player", player).build();
            try (Response response = http.newCall(request(url.toString(), token).get().build()).execute()) {
                if (!response.isSuccessful() || response.body() == null) throw new IOException("Dashboard unavailable");
                byte[] bytes = response.body().byteStream().readNBytes(1_048_577);
                if (bytes.length > 1_048_576) throw new IOException("Dashboard response too large");
                JsonObject data = gson.fromJson(new String(bytes, java.nio.charset.StandardCharsets.UTF_8), JsonObject.class);
                synchronized(this){if(epoch!=generation)return;probes=RankingPanel.array(data,"goals");}
                final String actionMessage=actionError;
                SwingUtilities.invokeLater(() -> { synchronized (this) {
                    if (epoch == generation) {
                        try { panel.showData(data);if(actionMessage!=null)panel.actionError(actionMessage); } catch (RuntimeException invalid) { panel.error(); }
                    }
                }});
            }
            String member;final long revision;
            pollPointAlerts(origin,token,player,epoch);
            synchronized(this){if(epoch!=generation)return;member=memberRequest;revision=memberRevision;}
            if(member!=null) {
                HttpUrl memberUrl=HttpUrl.parse(origin+"/v1/members").newBuilder().addQueryParameter("player",member).build();
                try(Response response=http.newCall(request(memberUrl.toString(),token).get().build()).execute()) {
                    if(!response.isSuccessful()||response.body()==null)throw new IOException("Member profile unavailable");
                    byte[] bytes=response.body().byteStream().readNBytes(1_048_577);if(bytes.length>1_048_576)throw new IOException("Profile too large");
                    JsonObject data=gson.fromJson(new String(bytes,java.nio.charset.StandardCharsets.UTF_8),JsonObject.class);
                    if(!RankingTracker.normalize(member).equals(RankingTracker.normalize(data.getAsJsonObject("profile").get("name").getAsString())))throw new IOException("Member profile mismatch");
                    SwingUtilities.invokeLater(()->{synchronized(this){if(epoch==generation&&revision==memberRevision)panel.showMember(data);}});
                    synchronized(this){if(epoch==generation&&revision==memberRevision)memberRequest=null;}
                }
            }
        } catch (IOException | RuntimeException failure) {
            SwingUtilities.invokeLater(() -> { synchronized (this) { if (epoch == generation) panel.error(); }});
        } finally { busy.set(false); }
    }

    private Request.Builder request(String url, String token) {
        return new Request.Builder().url(url).header("Authorization", "Bearer " + token)
            .header("User-Agent", "TrapHouseRankings/1.0");
    }

    private void pollPointAlerts(String origin,String token,String player,long epoch) {
        final Long cursor;
        synchronized(this){if(epoch!=generation)return;cursor=pointAlerts.cursor();}
        String url=origin+"/v1/point-notifications"+(cursor==null?"":"?after="+cursor);
        try(Response response=http.newCall(request(url,token).get().build()).execute()) {
            if(!response.isSuccessful()||response.body()==null)return;
            byte[] bytes=response.body().byteStream().readNBytes(262145);
            if(bytes.length>262144)return;
            JsonObject data=gson.fromJson(new String(bytes,java.nio.charset.StandardCharsets.UTF_8),JsonObject.class);
            if(!RankingTracker.normalize(player).equals(RankingTracker.normalize(data.get("player").getAsString())))return;
            synchronized(this){if(epoch==generation)pointAlerts.accept(data);}
        }catch(IOException|RuntimeException unavailable){ /* Retry the same cursor later; older servers can still sync rankings. */ }
    }

    void pointsLog(TrapHouseConfig config,JsonObject query,java.util.function.Consumer<JsonObject> done) {
        final long epoch;synchronized(this){epoch=generation;}
        JsonObject result=null;
        try {
            if(!config.rankingEnabled()||config.rankingToken().trim().isEmpty())throw new IOException("No credential");
            HttpUrl.Builder url=HttpUrl.parse(endpoint(config.rankingUrl().trim())+"/v1/points-log").newBuilder();
            for(java.util.Map.Entry<String,com.google.gson.JsonElement> entry:query.entrySet())url.addQueryParameter(entry.getKey(),entry.getValue().getAsString());
            try(Response response=http.newCall(request(url.build().toString(),config.rankingToken().trim()).get().build()).execute()) {
                if(!response.isSuccessful()||response.body()==null)throw new IOException("Log unavailable");
                byte[] bytes=response.body().byteStream().readNBytes(1_048_577);if(bytes.length>1_048_576)throw new IOException("Log too large");
                result=gson.fromJson(new String(bytes,java.nio.charset.StandardCharsets.UTF_8),JsonObject.class);
            }
        }catch(IOException|RuntimeException unavailable){ /* Read-only failure shown by popup. */ }
        final JsonObject data=result;SwingUtilities.invokeLater(()->{synchronized(this){if(epoch==generation)done.accept(data);}});
    }

    private boolean submit(String url,String token,JsonObject body)throws IOException {
        try(Response response=http.newCall(request(url,token).post(RequestBody.create(MediaType.parse("application/json"),gson.toJson(body))).build()).execute()) {
            if(response.code()==400||response.code()==404||response.code()==409||response.code()==422)return false;
            if(!response.isSuccessful())throw new IOException("Clan action rejected ("+response.code()+")");
            return true;
        }
    }
}
