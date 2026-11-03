package com.traphouse.server;

import com.google.gson.*;
import com.sun.net.httpserver.HttpServer;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.*;
import org.junit.*;
import static org.junit.Assert.*;

/** Real session lifecycle, isolated in a disposable database schema. */
public class DiscordCodePostgresTest {
    private Connection c;
    private String schema,origin;
    private HttpServer server;
    private final UUID member=UUID.randomUUID();
    private static final String DISCORD="123456789012345678";
    private Connection connect()throws SQLException {
        Connection connection=DriverManager.getConnection(System.getenv("TRAP_TEST_DATABASE_URL"),System.getenv("TRAP_TEST_DATABASE_USER"),System.getenv("TRAP_TEST_DATABASE_PASSWORD"));
        if(schema!=null)try(Statement s=connection.createStatement()){s.execute("SET search_path TO "+schema);}
        return connection;
    }
    @Before public void setup()throws Exception {
        c=connect();schema="code_test_"+UUID.randomUUID().toString().replace("-","");
        try(Statement s=c.createStatement()) {
            s.execute("CREATE SCHEMA "+schema);s.execute("SET search_path TO "+schema);
            try(java.io.InputStream input=RankingServer.class.getResourceAsStream("/schema.sql")){s.execute(new String(input.readAllBytes(),StandardCharsets.UTF_8));}
        }
        RankingStore.execute(c,"INSERT INTO members(id,name,normalized_name,active) VALUES (?,'Member','member',true)",member);
        RankingStore.execute(c,"INSERT INTO discord_member_links(discord_user_id,member_id) VALUES (?,?)",DISCORD,member);
        Map<String,String> env=new HashMap<>();env.put("TRAP_DISCORD_PUBLIC_KEY","a".repeat(64));env.put("TRAP_DISCORD_CLIENT_ID",DISCORD);env.put("TRAP_DISCORD_GUILD_ID",DISCORD);
        server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/",new DiscordLogin(this::connect,env,code->{throw new AssertionError("Code login must not contact OAuth");})::handle);
        server.start();origin="http://127.0.0.1:"+server.getAddress().getPort();
    }
    @After public void cleanup()throws Exception {
        if(server!=null)server.stop(0);
        if(c!=null){try{if(schema!=null)try(Statement s=c.createStatement()){s.execute("DROP SCHEMA "+schema+" CASCADE");}}finally{c.close();}}
    }
    private HttpResponse<String> post(String path,JsonObject body)throws Exception {
        return HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create(origin+path)).header("Content-Type","application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body.toString())).build(),HttpResponse.BodyHandlers.ofString());
    }
    private JsonObject session(String secret)throws Exception {
        JsonObject body=new JsonObject();body.addProperty("secret",secret);
        HttpResponse<String> response=post("/auth/discord/code",body);assertEquals(200,response.statusCode());
        return JsonParser.parseString(response.body()).getAsJsonObject();
    }
    private void rejected(int status,String discord,String code)throws Exception {
        try{DiscordLogin.verifyCode(c,discord,code);fail("Expected rejection");}catch(Policy.Rejected expected){assertEquals(status,expected.status);}
    }
    @Test public void codeRequiresAnActiveLinkAndIsSingleUseAndDeviceBound()throws Exception {
        String secret=DiscordLogin.secret();JsonObject session=session(secret);String code=session.get("code").getAsString();
        rejected(403,"999999999999999999",code);
        RankingStore.execute(c,"UPDATE members SET active=false WHERE id=?",member);rejected(403,DISCORD,code);
        RankingStore.execute(c,"UPDATE members SET active=true WHERE id=?",member);
        JsonObject reply=DiscordLogin.verifyCode(c,DISCORD,code);assertEquals(64,reply.getAsJsonObject("data").get("flags").getAsInt());
        rejected(400,DISCORD,code);
        JsonObject poll=new JsonObject();poll.add("session",session.get("session"));poll.addProperty("secret",DiscordLogin.secret());
        assertEquals(410,post("/auth/discord/poll",poll).statusCode());
        poll.addProperty("secret",secret);HttpResponse<String> response=post("/auth/discord/poll",poll);assertEquals(200,response.statusCode());
        JsonObject complete=JsonParser.parseString(response.body()).getAsJsonObject();assertEquals("complete",complete.get("status").getAsString());
        try(PreparedStatement s=RankingStore.query(c,"SELECT member_id,administrator FROM credentials WHERE token_hash=?",Policy.hash(complete.get("token").getAsString()));ResultSet r=s.executeQuery()) {
            assertTrue(r.next());assertEquals(member,r.getObject(1,UUID.class));assertFalse(r.getBoolean(2));
        }
        assertEquals(410,post("/auth/discord/poll",poll).statusCode());
    }
    @Test public void expiredCodesAndBrowserSessionsCannotBeVerifiedAsCodes()throws Exception {
        JsonObject session=session(DiscordLogin.secret());String code=session.get("code").getAsString();
        RankingStore.execute(c,"UPDATE discord_login_sessions SET expires_at=now()-interval '1 second' WHERE id=?",UUID.fromString(session.get("session").getAsString()));
        rejected(400,DISCORD,code);
        String browser=DiscordLogin.secret();
        RankingStore.execute(c,"INSERT INTO discord_login_sessions(id,device_hash,browser_hash) VALUES (?,?,?)",UUID.randomUUID(),Policy.hash(DiscordLogin.secret()),Policy.hash(browser));
        rejected(400,DISCORD,browser);
    }
}
