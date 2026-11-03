package com.traphouse.server;

import com.google.gson.*;
import com.sun.net.httpserver.HttpServer;
import java.io.InputStream;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.*;
import org.junit.*;
import static org.junit.Assert.*;

/** Real PostgreSQL storage, aggregation, anonymous score reads, and authenticated detail reads. */
public class ClanBrowserPostgresTest {
    private Connection c;private String schema;private HttpServer server;
    private final RankingStore.Actor main=new RankingStore.Actor(UUID.randomUUID(),"Main",false),iron=new RankingStore.Actor(UUID.randomUUID(),"Iron",false),hardcore=new RankingStore.Actor(UUID.randomUUID(),"Hardcore",false);
    private Connection connect()throws SQLException {
        Connection connection=DriverManager.getConnection(System.getenv("TRAP_TEST_DATABASE_URL"),System.getenv("TRAP_TEST_DATABASE_USER"),System.getenv("TRAP_TEST_DATABASE_PASSWORD"));
        if(schema!=null)try(Statement s=connection.createStatement()){s.execute("SET search_path TO "+schema);}return connection;
    }
    @Before public void setup()throws Exception {
        c=connect();schema="browser_test_"+UUID.randomUUID().toString().replace("-","");
        try(Statement s=c.createStatement()){s.execute("CREATE SCHEMA "+schema);s.execute("SET search_path TO "+schema);}
        script("/schema.sql");script("/clan-browser.sql");
        for(RankingStore.Actor actor:new RankingStore.Actor[]{main,iron,hardcore})RankingStore.execute(c,"INSERT INTO members VALUES (?,?,?,true)",actor.id,actor.name,Policy.normalize(actor.name));
        RankingStore.execute(c,"INSERT INTO credentials VALUES (?,?,false,now()+interval '1 day',false)",Policy.hash("browser-member"),main.id);
        RankingStore.execute(c,"INSERT INTO rules(type,event_key,points,daily_limit,enabled) VALUES ('LOOT','1:2',25,100,true)");
        c.setAutoCommit(false);
    }
    private void script(String name)throws Exception{try(InputStream input=RankingServer.class.getResourceAsStream(name);Statement s=c.createStatement()){s.execute(new String(input.readAllBytes(),StandardCharsets.UTF_8));}}
    @After public void cleanup()throws Exception{if(server!=null)server.stop(0);if(c!=null){try{c.rollback();c.setAutoCommit(true);if(schema!=null)try(Statement s=c.createStatement()){s.execute("DROP SCHEMA "+schema+" CASCADE");}}finally{c.close();}}}
    private static JsonObject json(String value){return JsonParser.parseString(value).getAsJsonObject();}
    private JsonObject snapshot(RankingStore.Actor actor,String type,long collection) {
        JsonObject body=json("{stats:{xp:5000000000,combat:100,pets:3,bosses:{vorkath:524,zulrah:200},clues:{hard:20,easy:5}},pages:[]}");body.addProperty("player",actor.name);body.getAsJsonObject("stats").addProperty("accountType",type);body.getAsJsonObject("stats").addProperty("clog",collection);return body;
    }
    private JsonObject page(long counter,boolean completed,Long drop) {
        JsonObject page=json("{key:'vorkath',label:'Vorkath',section:'Bosses',items:[{id:21992,name:'Vorki',quantity:1},{id:11286,name:'Draconic visage'}]}");page.addProperty("counter",counter);if(completed)page.getAsJsonArray("items").get(1).getAsJsonObject().addProperty("quantity",1);if(drop!=null)page.addProperty("lastDropCounter",drop);return page;
    }
    private void syncPage(RankingStore.Actor actor,JsonObject page)throws Exception{JsonObject body=new JsonObject();body.addProperty("player",actor.name);JsonArray pages=new JsonArray();pages.add(page);body.add("pages",pages);ClanBrowserData.sync(c,actor,body);}
    private JsonObject view(String mode,String metric,String category)throws Exception{Map<String,String> request=new HashMap<>();request.put("mode",mode);if(metric!=null)request.put("metric",metric);if(category!=null)request.put("category",category);return ClanBrowserData.view(c,request);}
    private JsonObject first(JsonObject data,int panel){return data.getAsJsonArray("panels").get(panel).getAsJsonObject().getAsJsonArray("rows").get(0).getAsJsonObject();}
    private void award(RankingStore.Actor actor)throws Exception{JsonObject event=json("{type:'LOOT',key:'unused'}");event.addProperty("id",UUID.randomUUID().toString());event.addProperty("player",actor.name);event.addProperty("eventKey","1:2");event.addProperty("key","1:2");event.addProperty("observedAt",java.time.Instant.now().toString());RankingStore.event(c,actor,eventWithKey(event));}
    private JsonObject eventWithKey(JsonObject body){body.addProperty("key","1:2");body.addProperty("type","LOOT");return body;}
    @Test public void allTenStatisticsHaveThreeAccountGroupsAndMonthlyGainsAreNotDoubleCounted()throws Exception {
        RankingStore.Actor[] actors={main,iron,hardcore};String[] types={"NORMAL","GROUP_IRONMAN","HARDCORE_IRONMAN"};
        for(int i=0;i<actors.length;i++){ClanBrowserData.sync(c,actors[i],snapshot(actors[i],types[i],100));ClanBrowserData.sync(c,actors[i],snapshot(actors[i],types[i],110));ClanBrowserData.sync(c,actors[i],snapshot(actors[i],types[i],110));syncPage(actors[i],page(500+i,true,null));award(actors[i]);}
        for(String metric:ClanBrowserData.METRICS){JsonObject data=view("leaderboard",metric,null);assertEquals(3,data.getAsJsonArray("panels").size());for(int group=0;group<3;group++)assertEquals(actors[group].name,first(data,group).get("player").getAsString());}
        assertEquals(10,first(view("leaderboard","monthly-clog",null),0).get("value").getAsInt());
        assertEquals(25,first(view("leaderboard","monthly-points",null),0).get("value").getAsInt());
        assertEquals(5_000_000_000L,first(view("leaderboard","xp",null),0).get("value").getAsLong());
        assertEquals(20,first(view("leaderboard","clues","hard"),0).get("value").getAsInt());assertEquals(25,first(view("leaderboard","clues",null),0).get("value").getAsInt());
        assertEquals(524,first(view("leaderboard","bosses","vorkath"),0).get("value").getAsInt());assertEquals(724,first(view("leaderboard","bosses",null),0).get("value").getAsInt());
        assertEquals(25,RankingStore.dashboard(c,main,main.name).getAsJsonObject("profile").get("points").getAsInt());
    }
    @Test public void pageMergesPreserveHistoricalItemsAndFreezeCompletionCountersAndDryStreaks()throws Exception {
        ClanBrowserData.sync(c,main,snapshot(main,"NORMAL",100));ClanBrowserData.sync(c,iron,snapshot(iron,"ULTIMATE_IRONMAN",100));
        syncPage(main,page(100,true,90L));syncPage(main,page(150,false,null));syncPage(iron,page(200,true,190L));
        JsonObject luck=view("luck",null,"vorkath"),deserved=view("deserved",null,"vorkath");assertEquals("Main",first(luck,0).get("player").getAsString());assertEquals(100,first(luck,0).get("value").getAsInt());assertTrue(first(luck,0).get("estimated").getAsBoolean());assertEquals("Iron",first(deserved,0).get("player").getAsString());
        JsonObject dry=view("dryboard",null,"vorkath");assertEquals(60,first(dry,0).get("value").getAsInt());
        syncPage(main,page(155,false,154L));assertEquals(1,first(view("dryboard",null,"vorkath"),0).get("value").getAsInt());
        assertEquals(100,first(view("luck",null,"vorkath"),0).get("value").getAsInt());
        Map<String,String> query=new HashMap<>();query.put("mode","clog");query.put("player",main.name);query.put("category","vorkath");JsonObject log=ClanBrowserData.view(c,query);assertEquals(1,log.getAsJsonArray("pages").get(0).getAsJsonObject().getAsJsonArray("items").get(1).getAsJsonObject().get("quantity").getAsInt());assertEquals("vorkath",log.getAsJsonArray("completedPages").get(0).getAsString());
        syncPage(hardcore,page(300,false,null));assertEquals(0,view("dryboard",null,"vorkath").getAsJsonArray("panels").get(2).getAsJsonObject().getAsJsonArray("rows").size());
    }
    @Test public void anonymousRequestsCanReadEveryBrowserModeIncludingCachedDetails()throws Exception {
        ClanBrowserData.sync(c,main,snapshot(main,"NORMAL",100));syncPage(main,page(123,true,null));c.commit();
        assertTrue(new PublicClanBrowser(this::connect).read("mode=leaderboard&metric=clog",null).has("panels"));
        server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);server.createContext("/",new RankingServer(this::connect)::handle);server.start();
        assertEquals(200,get("mode=leaderboard&metric=clog",null).statusCode());
        for(String query:new String[]{"mode=catalogue","mode=clog&player=Main&category=vorkath","mode=luck&category=vorkath","mode=deserved&category=vorkath","mode=dryboard&category=vorkath"})assertEquals(200,get(query,null).statusCode());
        String selection="mode=clog&player=Main&category=vorkath";assertEquals(200,get(selection,"browser-member").statusCode());RankingStore.execute(c,"UPDATE credentials SET revoked=true WHERE token_hash=?",Policy.hash("browser-member"));c.commit();assertEquals(200,get(selection,null).statusCode());
    }
    private HttpResponse<String> get(String query,String token)throws Exception{HttpRequest.Builder request=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+server.getAddress().getPort()+"/v1/browser?"+query));if(token!=null)request.header("Authorization","Bearer "+token);return HttpClient.newHttpClient().send(request.build(),HttpResponse.BodyHandlers.ofString());}
    @Test public void unknownTypesAreNotClassifiedAsMainsAndInactiveMembersAreExcluded()throws Exception {
        RankingStore.execute(c,"UPDATE members SET active=false WHERE id=?",hardcore.id);
        JsonObject data=view("leaderboard","points",null);assertEquals(4,data.getAsJsonArray("panels").size());assertEquals(0,data.getAsJsonArray("panels").get(0).getAsJsonObject().getAsJsonArray("rows").size());assertEquals(2,data.getAsJsonArray("panels").get(3).getAsJsonObject().getAsJsonArray("rows").size());
    }
    @Test public void observedItemsCannotAwardPointsOrChangeAnotherMembersStats()throws Exception {
        JsonObject body=snapshot(iron,"IRONMAN",100);
        try{ClanBrowserData.sync(c,main,body);fail();}catch(Policy.Rejected expected){assertEquals(403,expected.status);}
        body=snapshot(main,"NORMAL",100);body.addProperty("points",100000);try{ClanBrowserData.sync(c,main,body);fail();}catch(Policy.Rejected expected){assertEquals(400,expected.status);}
        assertEquals(0,RankingStore.dashboard(c,main,main.name).getAsJsonObject("profile").get("points").getAsInt());
    }
}
