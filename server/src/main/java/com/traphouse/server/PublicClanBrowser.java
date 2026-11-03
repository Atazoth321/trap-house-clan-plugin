package com.traphouse.server;

import com.google.gson.JsonObject;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.*;
import java.util.concurrent.TimeUnit;

/** Public read-only clan standings and collection browser. */
final class PublicClanBrowser {
    private final DiscordWorker.Connections connections;
    private static final class Saved {final JsonObject data;final long expires;Saved(JsonObject data){this.data=data;expires=System.nanoTime()+TimeUnit.SECONDS.toNanos(15);}}
    private final Map<String,Saved> cache=new LinkedHashMap<>(16,.75f,true);
    PublicClanBrowser(DiscordWorker.Connections connections){this.connections=connections;}
    static Map<String,String> parameters(String query) {
        Map<String,String> result=new TreeMap<>();
        if(query!=null) {
            if(query.length()>512)throw new Policy.Rejected(400,"Invalid browser selection");
            for(String part:query.split("&")) {
                String[] pair=part.split("=",2);if(pair.length!=2)throw new Policy.Rejected(400,"Invalid browser selection");
                String key=URLDecoder.decode(pair[0],StandardCharsets.UTF_8),value=URLDecoder.decode(pair[1],StandardCharsets.UTF_8);
                if(!Arrays.asList("mode","metric","category","player","refresh").contains(key)||result.put(key,value)!=null)throw new Policy.Rejected(400,"Invalid browser selection");
            }
        }
        String mode=result.getOrDefault("mode","leaderboard");
        if(!Arrays.asList("catalogue","leaderboard","clog","luck","deserved","dryboard").contains(mode))throw new Policy.Rejected(400,"Unknown browser mode");
        if(!ClanBrowserData.METRICS.contains(result.getOrDefault("metric","points")))throw new Policy.Rejected(400,"Unknown leaderboard statistic");
        if(result.containsKey("category"))ClanBrowserData.key(result.get("category"));
        if(result.containsKey("player")) {
            String player=result.get("player");if(!player.matches("[A-Za-z0-9 _-]{1,12}")||player.trim().isEmpty())throw new Policy.Rejected(400,"Invalid character name");result.put("player",player.trim());
        }
        if(mode.equals("clog")&&!result.containsKey("player"))throw new Policy.Rejected(400,"Choose a clan account");
        if(mode.equals("clog")&&!result.containsKey("category"))throw new Policy.Rejected(400,"Choose a collection category");
        if(Arrays.asList("luck","deserved","dryboard").contains(mode)&&!result.containsKey("category"))throw new Policy.Rejected(400,"Choose a collection category");
        if(result.containsKey("refresh")&&!result.get("refresh").equals("1"))throw new Policy.Rejected(400,"Invalid refresh option");
        return result;
    }
    synchronized JsonObject read(String query,String authorization)throws SQLException {
        Map<String,String> selection=parameters(query);String mode=selection.getOrDefault("mode","leaderboard");
        boolean refresh=selection.remove("refresh")!=null;String key=selection.toString();
        try(Connection c=connections.open()) {
            c.setReadOnly(true);c.setAutoCommit(false);
            try {
                RankingStore.execute(c,"SET LOCAL statement_timeout='5s'");
                Saved saved=cache.get(key);
                if(!refresh&&saved!=null&&System.nanoTime()<saved.expires){c.commit();return saved.data;}
                JsonObject data=ClanBrowserData.view(c,selection);c.commit();
                cache.put(key,new Saved(data));if(cache.size()>64)cache.remove(cache.keySet().iterator().next());return data;
            }catch(SQLException|RuntimeException failure){c.rollback();throw failure;}
        }
    }
}
