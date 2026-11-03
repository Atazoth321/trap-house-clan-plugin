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
    private JsonObject browserSnapshot;
    synchronized void browserSnapshot(JsonObject snapshot){browserSnapshot=snapshot;}
    void browserView(TrapHouseConfig config,JsonObject selection,boolean refresh,java.util.function.Consumer<JsonObject> done,java.util.function.Consumer<String> failed) {
        final long epoch;synchronized(this){epoch=generation;}
        try {
            String origin=endpoint(config.rankingUrl().trim());HttpUrl.Builder url=HttpUrl.parse(origin+"/v1/browser").newBuilder();
            for(java.util.Map.Entry<String,com.google.gson.JsonElement> entry:selection.entrySet())if(java.util.Set.of("mode","metric","category","player").contains(entry.getKey()))url.addQueryParameter(entry.getKey(),entry.getValue().getAsString());
            if(refresh)url.addQueryParameter("refresh","1");Request.Builder request=new Request.Builder().url(url.build()).get().header("User-Agent","TrapHouseRankings/1.0");
            try(Response response=http.newCall(request.build()).execute()) {
                if(response.code()==401||response.code()==403)throw new IOException("The server is restricting this public browser. Deploy the updated backend and refresh.");
                if(!response.isSuccessful()||response.body()==null)throw new IOException("Clan browser unavailable (HTTP "+response.code()+"). Refresh to retry.");
                byte[] bytes=response.body().byteStream().readNBytes(3_145_729);if(bytes.length>3_145_728)throw new IOException("Clan browser response is too large.");
                JsonObject data=gson.fromJson(new String(bytes,java.nio.charset.StandardCharsets.UTF_8),JsonObject.class);if(data==null)throw new IOException("Invalid clan browser response.");
                SwingUtilities.invokeLater(()->{synchronized(this){if(epoch==generation)done.accept(data);}});
            }
        }catch(IOException|RuntimeException failure) {
            String message=failure instanceof IOException?failure.getMessage():"Clan browser unavailable. Refresh to retry.";
            SwingUtilities.invokeLater(()->{synchronized(this){if(epoch==generation)failed.accept(message);}});
        }
    }
    private String memberRequest;
    private long memberRevision;
    private String registrationPath;
    private final PointAlerts pointAlerts=new PointAlerts();
    private com.google.gson.JsonArray awardSources=new com.google.gson.JsonArray();
    @Inject private CelebrationOverlay celebrations;
    private String earnedRank;
    private long earnedThreshold;
    private void rankMilestone(JsonObject data) {
        JsonObject profile=data.getAsJsonObject("profile");
        String rank=RankingPanel.string(profile,"automaticRank",RankingPanel.string(profile,"rank","Unranked"));
        Long threshold=null;
        for(com.google.gson.JsonElement element:RankingPanel.array(data,"ranks")) {
            JsonObject tier=element.getAsJsonObject();
            if(rank.equals(RankingPanel.string(tier,"name","")))threshold=tier.get("threshold").getAsLong();
        }
        if(threshold==null) {
            if(rank.equals("Unranked")){earnedRank=rank;earnedThreshold=-1;}
            return;
        }
        if(earnedRank!=null&&!earnedRank.equals(rank)&&threshold>earnedThreshold&&celebrations!=null)celebrations.rank(rank);
        earnedRank=rank;earnedThreshold=threshold;
    }

    synchronized java.util.List<String> pointMessages(){return pointAlerts.drain();}
    synchronized java.util.List<String> pointMessages(TrapHouseConfig config){return pointAlerts.drain(config);}

    com.google.gson.JsonArray probes() { return probes; }
    synchronized void progress(JsonObject values) { progressRequest=values; }
    synchronized void browse(String name) { memberRequest=name;memberRevision++; }
    synchronized void register(String id,boolean join) { java.util.UUID.fromString(id);registrationPath="/v1/clan-events/"+id+(join?"/register":"/cancel"); }

    Runnable rankChange(TrapHouseConfig config,String member,JsonObject body,java.util.function.Consumer<String> done) {
        java.util.UUID.fromString(member);
        final long epoch; synchronized(this){epoch=generation;}
        final String origin=endpoint(config.rankingUrl().trim()),token=config.rankingToken().trim();
        final JsonObject command=body.deepCopy();
        return ()->{
            synchronized(this){if(epoch!=generation)return;}
            String error=null;
            try {
                if(!config.rankingEnabled()||token.isEmpty())throw new IOException("Ranking sync disabled");
                try(Response response=http.newCall(request(origin+"/v1/admin/members/"+member+"/rank",token)
                    .post(RequestBody.create(MediaType.parse("application/json"),gson.toJson(command))).build()).execute()) {
                    if(!response.isSuccessful())error=response.code()==401||response.code()==403?"Owner access rejected. Sign in and refresh.":"Rank change rejected (HTTP "+response.code()+"). Refresh and retry.";
                }
            }catch(IOException|RuntimeException failed){error="Could not confirm the change. Refresh before retrying.";}
            final String result=error;
            SwingUtilities.invokeLater(()->{synchronized(this){if(epoch==generation)done.accept(result);}});
        };
    }

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

    synchronized boolean enqueue(RankingEvent event) {
        if(queue.size()>=200)return false;
        queue.addLast(event);return true;
    }

    synchronized void reset() { generation++; awardSources=new com.google.gson.JsonArray(); earnedRank=null; earnedThreshold=0; if(celebrations!=null)celebrations.reset(); queue.clear(); pointAlerts.reset(); manualRequest = null; manualPlayer = null;progressRequest=null;browserSnapshot=null;memberRequest=null;registrationPath=null;probes=new com.google.gson.JsonArray(); }

    synchronized void requestReward(String player, String key, String reason) {
        if (player == null || manualRequest != null) return;
        manualRequest = new JsonObject();
        manualRequest.addProperty("id", java.util.UUID.randomUUID().toString());
        manualRequest.addProperty("key", key); manualRequest.addProperty("reason", reason);
        manualPlayer = player;
    }

    void publicLeaderboard(TrapHouseConfig config,RankingPanel panel) {
        final long epoch;synchronized(this){epoch=generation;}
        try {
            String origin=endpoint(config.rankingUrl().trim());
            try(Response response=http.newCall(new Request.Builder().url(origin+"/v1/leaderboard").get()
                .header("User-Agent","TrapHouseRankings/1.0").build()).execute()) {
                if(!response.isSuccessful()||response.body()==null)throw new IOException("Leaderboard unavailable");
                byte[] bytes=response.body().byteStream().readNBytes(1_048_577);
                if(bytes.length>1_048_576)throw new IOException("Leaderboard too large");
                JsonObject data=gson.fromJson(new String(bytes,java.nio.charset.StandardCharsets.UTF_8),JsonObject.class);
                if(data==null||!data.has("leaderboard")||!data.get("leaderboard").isJsonArray())throw new IOException("Invalid leaderboard");
                SwingUtilities.invokeLater(()->{synchronized(this){if(epoch==generation)panel.showPublicLeaderboard(data);}});
            }
        }catch(IOException|RuntimeException failure) {
            SwingUtilities.invokeLater(()->{synchronized(this){if(epoch==generation)panel.publicLeaderboardError();}});
        }
    }

    void sync(TrapHouseConfig config, String player, RankingPanel panel) {
        final long epoch;
        synchronized (this) { epoch = generation; }
        if (!config.rankingEnabled() || player == null || config.rankingToken().trim().isEmpty()) {
            SwingUtilities.invokeLater(() -> { synchronized (this) {
                if (epoch == generation) panel.disconnected("Sign in for your profile. The clan leaderboard is available to everyone.");
            }});
            return;
        }
        if (!busy.compareAndSet(false, true)) return;
        try {
            String origin = endpoint(config.rankingUrl().trim());
            String token = config.rankingToken().trim();
            boolean baseline;
            synchronized(this){if(epoch!=generation)return;baseline=pointAlerts.cursor()==null;}
            JsonObject browser; synchronized(this){browser=browserSnapshot;}
            if(browser!=null&&RankingTracker.normalize(player).equals(RankingTracker.normalize(browser.get("player").getAsString()))) {
                if(submit(origin+"/v1/browser/sync",token,browser))synchronized(this){if(epoch==generation&&browserSnapshot==browser)browserSnapshot=null;}
            }
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
                if (!response.isSuccessful()) {
                    String message=dashboardFailure(response.code());
                    SwingUtilities.invokeLater(()->{synchronized(this){if(epoch==generation)panel.actionError(message);}});
                    return;
                }
                if (response.body() == null) throw new IOException("Dashboard unavailable");
                byte[] bytes = response.body().byteStream().readNBytes(1_048_577);
                if (bytes.length > 1_048_576) throw new IOException("Dashboard response too large");
                JsonObject data = gson.fromJson(new String(bytes, java.nio.charset.StandardCharsets.UTF_8), JsonObject.class);
                synchronized(this){if(epoch!=generation)return;probes=RankingPanel.array(data,"goals");awardSources=RankingPanel.array(data,"sources").deepCopy();rankMilestone(data);}
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

    static String dashboardFailure(int code) {
        if(code==401)return "Your login expired or was revoked. Sign in with Discord again.";
        if(code==403)return "Profile access rejected. Log into your linked OSRS character; ask leadership to check your Discord link and active membership.";
        if(code==429)return "Too many requests. Your profile will retry automatically.";
        return "Profile unavailable (HTTP "+code+"). Retrying automatically.";
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
            synchronized(this){if(epoch==generation){
                com.google.gson.JsonArray awards=data.getAsJsonArray("awards");
                if(awards!=null)for(int i=0;i<awards.size();i++)awards.set(i,AwardDetails.enrich(awards.get(i).getAsJsonObject(),awardSources));
                pointAlerts.accept(data);for(JsonObject award:pointAlerts.celebrations())if(celebrations!=null)celebrations.award(award);
            }}
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
