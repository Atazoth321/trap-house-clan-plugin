package com.traphouse.server;

import com.google.gson.*;
import com.sun.net.httpserver.HttpServer;
import java.lang.reflect.Proxy;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.sql.*;
import java.time.Instant;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class DiscordInteractionsTest {
    private static String hex(byte[] bytes){StringBuilder s=new StringBuilder();for(byte b:bytes)s.append(String.format("%02x",b&255));return s.toString();}
    private static String publicKey(KeyPair pair){byte[] encoded=pair.getPublic().getEncoded();return hex(Arrays.copyOfRange(encoded,encoded.length-32,encoded.length));}
    private static String sign(KeyPair pair,String timestamp,String body)throws Exception {
        Signature signature=Signature.getInstance("Ed25519");signature.initSign(pair.getPrivate());signature.update((timestamp+body).getBytes(StandardCharsets.UTF_8));return hex(signature.sign());
    }
    @Test public void rawBodySignaturesRejectForgeryTamperingAndStaleRequests()throws Exception {
        KeyPair pair=KeyPairGenerator.getInstance("Ed25519").generateKeyPair();Instant now=Instant.now();String timestamp=Long.toString(now.getEpochSecond()),body="{\"type\":1}";
        String signature=sign(pair,timestamp,body),key=publicKey(pair);
        assertTrue(DiscordInteractions.verify(key,signature,timestamp,body.getBytes(StandardCharsets.UTF_8),now));
        assertFalse(DiscordInteractions.verify(key,signature,timestamp,(body+" ").getBytes(StandardCharsets.UTF_8),now));
        assertFalse(DiscordInteractions.verify(key,signature,timestamp,body.getBytes(StandardCharsets.UTF_8),now.plusSeconds(301)));
        assertFalse(DiscordInteractions.verify(key,signature,timestamp,body.getBytes(StandardCharsets.UTF_8),now.minusSeconds(301)));
        assertFalse(DiscordInteractions.verify(key,"00".repeat(64),timestamp,body.getBytes(StandardCharsets.UTF_8),now));
        assertFalse(DiscordInteractions.verify(key,signature,"invalid",body.getBytes(StandardCharsets.UTF_8),now));
    }
    private interface Call {Object invoke(String method,Object[] args)throws Throwable;}
    private static <T>T proxy(Class<T> type,Call call){return type.cast(Proxy.newProxyInstance(type.getClassLoader(),new Class<?>[]{type},(object,method,args)->call.invoke(method.getName(),args)));}
    private static Connection nonOwnerOnly() {
        return proxy(Connection.class,(method,args)->{
            switch(method) {
                case "setAutoCommit":case "commit":case "rollback":case "close":return null;
                case "setSavepoint":return proxy(Savepoint.class,(op,values)->0);
                case "prepareStatement":
                    String sql=(String)args[0];
                    boolean cache=sql.startsWith("INSERT INTO discord_interactions")||sql.startsWith("UPDATE discord_interactions");
                    boolean owner=sql.startsWith("SELECT m.id,m.name FROM discord_member_links")&&sql.contains("p.permission='OWNER'")&&sql.contains("m.active");
                    if(!cache&&!owner&&!sql.startsWith("SET LOCAL "))throw new AssertionError("Unauthorized target access: "+sql);
                    return proxy(PreparedStatement.class,(op,values)->{
                        switch(op){case "setObject":case "close":return null;case "executeUpdate":return 1;
                            case "executeQuery":return proxy(ResultSet.class,(read,cols)->{if(read.equals("next"))return false;if(read.equals("close"))return null;throw new AssertionError(read);});
                            default:throw new AssertionError(op);}
                    });
                default:throw new AssertionError(method);
            }
        });
    }
    @Test public void directSignedNonOwnerApprovalsCannotAccessRequestOrMembershipTables()throws Exception {
        KeyPair pair=KeyPairGenerator.getInstance("Ed25519").generateKeyPair();Map<String,String> env=new HashMap<>();
        env.put("TRAP_DISCORD_PUBLIC_KEY",publicKey(pair));env.put("TRAP_DISCORD_CLIENT_ID","111111111111111111");env.put("TRAP_DISCORD_GUILD_ID","222222222222222222");
        HttpServer server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);server.createContext("/",new DiscordInteractions(DiscordInteractionsTest::nonOwnerOnly,env)::handle);server.start();
        try {
            for(RankSecurity.Role role:RankSecurity.Role.values()) {
                if(role==RankSecurity.Role.OWNER)continue;
                for(int type:new int[]{2,3,5}) {
                    JsonObject request=DiscordJoining.object("{\"id\":\"333333333333333333\",\"application_id\":\"111111111111111111\",\"guild_id\":\"222222222222222222\",\"member\":{\"user\":{\"id\":\"444444444444444444\"},\"roles\":[\"OWNER\"]},\"permission\":\"OWNER\"}");
                    request.addProperty("type",type);JsonObject data=new JsonObject();
                    if(type==2)data.addProperty("name","pending");
                    else data.addProperty("custom_id",(type==3?"join:approve:":"join:accept:")+UUID.randomUUID());
                    if(type==5){JsonArray rows=new JsonArray();JsonObject input=new JsonObject();input.addProperty("custom_id","reason");input.addProperty("value","Forged Owner approval");rows.add(DiscordJoining.row(input));data.add("components",rows);}
                    data.addProperty("permission",role.name());request.add("data",data);
                    HttpResponse<String> response=post(server,pair,request.toString(),true);
                    assertEquals(200,response.statusCode());assertTrue(response.body(),response.body().contains("Only the linked clan Owner"));assertTrue(response.body().contains("64"));
                }
            }
            assertEquals(401,post(server,pair,"{}",false).statusCode());
            JsonObject ping=DiscordJoining.object("{\"application_id\":\"111111111111111111\",\"type\":1}");
            assertEquals(1,JsonParser.parseString(post(server,pair,ping.toString(),true).body()).getAsJsonObject().get("type").getAsInt());
            ping.addProperty("application_id","999999999999999999");assertEquals(403,post(server,pair,ping.toString(),true).statusCode());
        }finally{server.stop(0);}
    }
    private static HttpResponse<String> post(HttpServer server,KeyPair pair,String body,boolean signed)throws Exception {
        String time=Long.toString(Instant.now().getEpochSecond());HttpRequest.Builder request=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+server.getAddress().getPort()+"/discord/interactions"));
        if(signed)request.header("X-Signature-Timestamp",time).header("X-Signature-Ed25519",sign(pair,time,body));
        return HttpClient.newHttpClient().send(request.POST(HttpRequest.BodyPublishers.ofString(body)).build(),HttpResponse.BodyHandlers.ofString());
    }
    @Test public void requestFormsRequireValidNamesAndNeverMentionUsers() {
        assertEquals("Atazoth",DiscordJoining.characterName("Atazoth"));
        for(String invalid:new String[]{"", "   ","@everyone","name\n","abcdefghijklmn"})try{DiscordJoining.characterName(invalid);fail(invalid);}catch(Policy.Rejected expected){}
        JsonObject message=DiscordJoining.message("Member");assertEquals(64,message.getAsJsonObject("data").get("flags").getAsInt());assertEquals(0,message.getAsJsonObject("data").getAsJsonObject("allowed_mentions").getAsJsonArray("parse").size());
    }
}
