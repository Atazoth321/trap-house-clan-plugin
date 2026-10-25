package com.traphouse.server;

import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpServer;
import java.lang.reflect.Proxy;
import java.net.*;
import java.net.http.*;
import java.sql.*;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Real HTTP routing and token hashing with a credential-only JDBC fixture.
 * Any attempt to read or mutate a target member fails the test. */
public class DeductionHttpTest {
    private interface Call { Object invoke(String method,Object[] args) throws Throwable; }
    private static <T> T proxy(Class<T> type,Call call) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(),new Class<?>[]{type},
            (object,method,args)->call.invoke(method.getName(),args)));
    }
    private Connection credentials(Map<String,RankingStore.Actor> identities) {
        return proxy(Connection.class,(method,args)-> {
            switch(method) {
                case "setAutoCommit":case "commit":case "rollback":case "close":return null;
                case "prepareStatement":
                    String sql=(String)args[0];Object[] parameter={null};
                    if(!sql.startsWith("SET LOCAL ")&&!sql.contains("FROM credentials k"))throw new AssertionError("Unauthorized target database access: "+sql);
                    return proxy(PreparedStatement.class,(operation,values)-> {
                        switch(operation) {
                            case "setObject":parameter[0]=values[1];return null;
                            case "close":return null;
                            case "executeUpdate":return 0;
                            case "executeQuery":
                                RankingStore.Actor actor=identities.get(parameter[0]);boolean[] read={false};
                                return proxy(ResultSet.class,(readMethod,columns)-> {
                                    switch(readMethod) {
                                        case "next":boolean found=!read[0]&&actor!=null;read[0]=true;return found;
                                        case "getObject":return actor.id;
                                        case "getString":return ((Integer)columns[0])==2?actor.name:actor.role.name();
                                        case "close":return null;
                                        default:throw new AssertionError(readMethod);
                                    }
                                });
                            default:throw new AssertionError(operation);
                        }
                    });
                default:throw new AssertionError(method);
            }
        });
    }
    @Test public void authenticatedNonOwnersCannotDeductThroughHttpOrSpoofOwnerFields() throws Exception {
        Map<String,RankingStore.Actor> identities=new HashMap<>();
        for(RankSecurity.Role role:RankSecurity.Role.values())identities.put(Policy.hash("token-"+role),new RankingStore.Actor(UUID.randomUUID(),role.name(),role));
        HttpServer server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/",new RankingServer(()->credentials(identities))::handle);server.start();
        try {
            URI uri=URI.create("http://127.0.0.1:"+server.getAddress().getPort()+"/v1/admin/members/"+UUID.randomUUID()+"/points");
            JsonObject body=new JsonObject();body.addProperty("id",UUID.randomUUID().toString());body.addProperty("points",-50);body.addProperty("reason","Spoofed request");
            body.addProperty("permission","OWNER");body.addProperty("actor",identities.get(Policy.hash("token-OWNER")).id.toString());
            HttpClient http=HttpClient.newHttpClient();
            for(RankSecurity.Role role:RankSecurity.Role.values()) {
                if(role==RankSecurity.Role.OWNER)continue;
                HttpResponse<String> response=http.send(HttpRequest.newBuilder(uri).header("Authorization","Bearer token-"+role)
                    .header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(body.toString())).build(),HttpResponse.BodyHandlers.ofString());
                assertEquals(role.name(),403,response.statusCode());
            }
            HttpResponse<String> unauthenticated=http.send(HttpRequest.newBuilder(uri).header("Authorization","Bearer invalid")
                .header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(body.toString())).build(),HttpResponse.BodyHandlers.ofString());
            assertEquals(401,unauthenticated.statusCode());
        } finally { server.stop(0); }
    }
}
