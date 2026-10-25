package com.traphouse.server;

import com.google.gson.*;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.time.Duration;
import java.util.UUID;

/** Optional server-side import of public EHP/EHB. Never submits player updates or awards points. */
final class WiseOldManStats implements Runnable {
    private final DiscordWorker.Connections connections;
    private final HttpClient http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).followRedirects(HttpClient.Redirect.NEVER).build();
    WiseOldManStats(DiscordWorker.Connections connections){this.connections=connections;}
    static JsonObject parse(String response) {
        JsonObject data=JsonParser.parseString(response).getAsJsonObject(),result=new JsonObject();
        for(String metric:new String[]{"ehp","ehb"}) {
            if(!data.has(metric)||!data.get(metric).isJsonPrimitive()||!data.getAsJsonPrimitive(metric).isNumber())throw new IllegalArgumentException("Missing efficiency value");
            java.math.BigDecimal value=data.get(metric).getAsBigDecimal();
            if(value.signum()<0||value.compareTo(new java.math.BigDecimal("1000000000"))>0)throw new IllegalArgumentException("Efficiency value outside range");
            result.addProperty(metric,value);
        }
        return result;
    }
    @Override public void run() {
        UUID id;String name;
        try(Connection c=connections.open();PreparedStatement s=RankingStore.query(c,"SELECT m.id,m.name FROM members m LEFT JOIN member_metrics v ON v.member_id=m.id LEFT JOIN metric_attempts a ON a.member_id=m.id WHERE m.active AND (v.fetched_at IS NULL OR v.fetched_at<now()-interval '1 hour') AND (a.last_attempt IS NULL OR a.last_attempt<now()-interval '1 hour') ORDER BY a.last_attempt NULLS FIRST,m.id LIMIT 1");ResultSet r=s.executeQuery()) {
            if(!r.next())return;id=r.getObject(1,UUID.class);name=r.getString(2);
            RankingStore.execute(c,"INSERT INTO metric_attempts(member_id) VALUES (?) ON CONFLICT(member_id) DO UPDATE SET last_attempt=now()",id);
        }catch(SQLException unavailable){return;}
        try {
            URI url=URI.create("https://api.wiseoldman.net/v2/players/"+URLEncoder.encode(name,StandardCharsets.UTF_8).replace("+","%20"));
            HttpRequest request=HttpRequest.newBuilder(url).timeout(Duration.ofSeconds(10)).header("User-Agent","TrapHouseRankings/1.0").GET().build();
            HttpResponse<java.io.InputStream> response=http.send(request,HttpResponse.BodyHandlers.ofInputStream());
            JsonObject values;
            try(java.io.InputStream input=response.body()) {
                if(response.statusCode()!=200)return;
                byte[] bytes=input.readNBytes(1_048_577);if(bytes.length>1_048_576)return;
                values=parse(new String(bytes,StandardCharsets.UTF_8));
            }
            try(Connection c=connections.open()) {
                RankingStore.execute(c,"INSERT INTO member_metrics(member_id,ehp,ehb) VALUES (?,?,?) ON CONFLICT(member_id) DO UPDATE SET ehp=excluded.ehp,ehb=excluded.ehb,fetched_at=now()",id,values.get("ehp").getAsBigDecimal(),values.get("ehb").getAsBigDecimal());
            }
        }catch(InterruptedException stop){Thread.currentThread().interrupt();}
        catch(Exception failure){System.err.println("Public efficiency import unavailable; will retry.");}
    }
}
