package com.traphouse.server;

import com.sun.net.httpserver.HttpServer;
import java.net.*;
import java.net.http.*;
import java.util.*;
import org.junit.*;
import static org.junit.Assert.*;

public class DiscordLoginTest {
    @Test public void verificationFailuresIdentifyTheStageWithoutProviderResponseData() {
        assertTrue(DiscordLogin.verificationError("token",401).contains("Client ID and Client Secret"));
        assertTrue(DiscordLogin.verificationError("identity",401).contains("verify your account"));
        assertTrue(DiscordLogin.verificationError("membership",404).contains("TRAP_DISCORD_GUILD_ID"));
        assertTrue(DiscordLogin.verificationError("membership",404).contains("HTTP 404"));
    }
    private HttpServer server;
    private String origin;
    private final HttpClient http=HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();
    @Before public void start()throws Exception {
        server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        DiscordLogin login=new DiscordLogin(()->{throw new AssertionError("Invalid login must not access the database");},Collections.emptyMap(),code->{throw new AssertionError("Invalid state must not contact Discord");});
        server.createContext("/",login::handle);server.start();origin="http://127.0.0.1:"+server.getAddress().getPort();
    }
    @After public void stop(){server.stop(0);}
    @Test public void disabledSignInFailsClosedWithoutDatabaseOrDiscord()throws Exception {
        HttpResponse<String> response=post("/auth/discord/device","{}");
        assertEquals(503,response.statusCode());assertTrue(response.body().contains("not configured"));
        assertEquals("no-store",response.headers().firstValue("Cache-Control").get());
        assertEquals("no-referrer",response.headers().firstValue("Referrer-Policy").get());
    }
    @Test public void missingOrForgedCallbackStateCannotContactDiscord()throws Exception {
        for(String query:new String[]{"code=fake","code=fake&state=invalid","state=invalid&state=another&code=fake"}) {
            HttpResponse<String> response=http.send(HttpRequest.newBuilder(URI.create(origin+"/auth/discord/callback?"+query)).build(),HttpResponse.BodyHandlers.ofString());
            assertEquals(400,response.statusCode());
        }
    }
    @Test public void pollRequiresFullDeviceSecretAndCanonicalSession()throws Exception {
        assertEquals(400,post("/auth/discord/poll","{\"session\":\"00000000-0000-0000-0000-000000000000\",\"secret\":\"short\",\"permission\":\"OWNER\"}").statusCode());
        assertEquals(400,post("/auth/discord/poll","{\"session\":\"invalid\",\"secret\":\""+DiscordLogin.secret()+"\"}").statusCode());
    }
    @Test public void secretHas256BitsAndIdentityUsesNumericId() {
        assertEquals(32,Base64.getUrlDecoder().decode(DiscordLogin.secret()).length);
        assertNotEquals(DiscordLogin.secret(),DiscordLogin.secret());
        assertEquals("123456789012345678",DiscordLogin.snowflake("123456789012345678"));
        for(String invalid:new String[]{"Atazoth","OWNER","123","123456789012345678<script>"}) {
            try{DiscordLogin.snowflake(invalid);fail();}catch(IllegalArgumentException expected){}
        }
    }
    private HttpResponse<String> post(String path,String body)throws Exception {
        return http.send(HttpRequest.newBuilder(URI.create(origin+path)).header("Content-Type","application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body)).build(),HttpResponse.BodyHandlers.ofString());
    }
}
