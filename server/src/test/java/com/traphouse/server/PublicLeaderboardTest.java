package com.traphouse.server;

import com.google.gson.*;
import com.sun.net.httpserver.HttpServer;
import java.lang.reflect.Proxy;
import java.net.*;
import java.net.http.*;
import java.sql.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;
import static org.junit.Assert.*;

public class PublicLeaderboardTest {
    private interface Call {Object invoke(String method,Object[] args)throws Throwable;}
    private static <T>T proxy(Class<T> type,Call call){return type.cast(Proxy.newProxyInstance(type.getClassLoader(),new Class<?>[]{type},(object,method,args)->call.invoke(method.getName(),args)));}
    private static ResultSet rows(Object[][] values) {
        int[] index={-1};
        return proxy(ResultSet.class,(method,args)->{
            switch(method) {
                case "next":return ++index[0]<values.length;
                case "close":return null;
                case "getString":return values[index[0]][(Integer)args[0]-1];
                case "getLong":return ((Number)values[index[0]][(Integer)args[0]-1]).longValue();
                case "getBoolean":return values[index[0]][(Integer)args[0]-1];
                default:throw new AssertionError(method);
            }
        });
    }
    private static Connection publicOnly() {
        return proxy(Connection.class,(method,args)->{
            switch(method) {
                case "setReadOnly":case "setAutoCommit":case "commit":case "rollback":case "close":return null;
                case "prepareStatement":
                    String sql=(String)args[0];Object[][] data;
                    if(sql.startsWith("SET LOCAL "))data=new Object[0][];
                    else if(sql.startsWith("SELECT name,threshold FROM ranks"))data=new Object[][]{{"Recruit",0L},{"Corporal",100L}};
                    else if(sql.startsWith("SELECT m.name,coalesce(sum(l.points),0) AS total")) {
                        assertTrue(sql.contains("WHERE m.active"));assertTrue(sql.contains("ORDER BY total DESC,m.normalized_name,m.id LIMIT 1000"));
                        data=new Object[][]{{"Leader",500L,"Owner",true,true,null},{"Raider",250L,null,false,false,null},{"Veteran",50L,null,false,false,"Corporal"}};
                    } else throw new AssertionError("Public request accessed private data: "+sql);
                    Object[][] result=data;
                    return proxy(PreparedStatement.class,(operation,params)->{
                        switch(operation){case "close":return null;case "executeUpdate":return 0;case "executeQuery":return rows(result);default:throw new AssertionError(operation);}
                    });
                default:throw new AssertionError(method);
            }
        });
    }
    @Test public void anonymousStandingsExposeOnlyNamesRanksAndPointsAndCacheDatabaseReads()throws Exception {
        AtomicInteger connections=new AtomicInteger();
        RankingServer service=new RankingServer(()->{connections.incrementAndGet();return publicOnly();});
        HttpServer server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);server.createContext("/",service::handle);server.start();
        String origin="http://127.0.0.1:"+server.getAddress().getPort();HttpClient client=HttpClient.newHttpClient();
        try {
            for(int i=0;i<2;i++) {
                HttpResponse<String> response=client.send(HttpRequest.newBuilder(URI.create(origin+"/v1/leaderboard")).GET().build(),HttpResponse.BodyHandlers.ofString());
                assertEquals(200,response.statusCode());JsonObject data=JsonParser.parseString(response.body()).getAsJsonObject();assertEquals(1,data.size());
                JsonArray rows=data.getAsJsonArray("leaderboard");assertEquals(3,rows.size());
                for(JsonElement value:rows){JsonObject row=value.getAsJsonObject();assertEquals(3,row.size());assertTrue(row.has("name"));assertTrue(row.has("rank"));assertTrue(row.has("points"));}
                assertEquals("Owner",rows.get(0).getAsJsonObject().get("rank").getAsString());assertEquals("Corporal",rows.get(1).getAsJsonObject().get("rank").getAsString());
                assertEquals("Corporal",rows.get(2).getAsJsonObject().get("rank").getAsString());
            }
            assertEquals(1,connections.get());
            for(String path:new String[]{"/v1/dashboard?player=Raider","/v1/members?player=Raider","/v1/admin/rewards"})
                assertEquals(401,client.send(HttpRequest.newBuilder(URI.create(origin+path)).build(),HttpResponse.BodyHandlers.ofString()).statusCode());
            assertEquals(400,client.send(HttpRequest.newBuilder(URI.create(origin+"/v1/leaderboard?player=Raider")).build(),HttpResponse.BodyHandlers.ofString()).statusCode());
            assertEquals(405,client.send(HttpRequest.newBuilder(URI.create(origin+"/v1/leaderboard")).POST(HttpRequest.BodyPublishers.ofString("{}")).build(),HttpResponse.BodyHandlers.ofString()).statusCode());
        }finally{server.stop(0);}
    }
}
