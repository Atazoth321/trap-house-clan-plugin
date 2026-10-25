package com.traphouse.server;

import com.google.gson.*;
import com.sun.net.httpserver.HttpExchange;
import java.io.*;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.sql.*;
import java.time.Duration;
import java.util.*;

/** Discord identifies a preverified roster entry; it never supplies permissions or an OSRS identity. */
final class DiscordLogin {
    private static final Gson JSON = new Gson();
    private static final SecureRandom RANDOM = new SecureRandom();
    private static long minute;
    private static int starts;
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10))
        .followRedirects(HttpClient.Redirect.NEVER).build();
    interface IdentityProvider { String identify(String code) throws Exception; }
    private final DiscordWorker.Connections connections;
    private final Map<String,String> env;
    private final IdentityProvider provider;

    DiscordLogin(DiscordWorker.Connections connections) { this(connections, System.getenv(), null); }
    DiscordLogin(DiscordWorker.Connections connections, Map<String,String> env, IdentityProvider provider) {
        this.connections=connections; this.env=env; this.provider=provider == null ? this::discordIdentity : provider;
    }
    static boolean matches(String path) { return path.startsWith("/auth/discord/"); }
    static String secret() { byte[] b=new byte[32]; RANDOM.nextBytes(b); return Base64.getUrlEncoder().withoutPadding().encodeToString(b); }
    static String capability(JsonObject body) {
        String value=Policy.text(body,"secret",43);
        if(!value.matches("[A-Za-z0-9_-]{43}"))throw new Policy.Rejected(400,"Invalid login secret");
        return value;
    }
    static String snowflake(String value) {
        if(value==null||!value.matches("[0-9]{17,20}"))throw new IllegalArgumentException("Invalid Discord user ID");
        return value;
    }
    private String required(String key) {
        String value=env.get(key);
        if(value==null||value.isBlank())throw new Policy.Rejected(503,"Discord sign-in is not configured yet");
        return value;
    }
    private String origin() {
        String value=required("TRAP_PUBLIC_URL");
        URI uri=URI.create(value);
        if(!"https".equals(uri.getScheme())||uri.getHost()==null||uri.getUserInfo()!=null||uri.getQuery()!=null||uri.getFragment()!=null
            ||!(uri.getPath().isEmpty()||uri.getPath().equals("/")))throw new Policy.Rejected(503,"Invalid public API address");
        return value.replaceAll("/+$","");
    }
    private static synchronized void limitStarts() {
        long now=System.currentTimeMillis()/60000;
        if(now!=minute){minute=now;starts=0;}
        if(++starts>30)throw new Policy.Rejected(429,"Too many sign-in attempts; try again in a minute");
    }
    static Map<String,String> parameters(String query) {
        Map<String,String> result=new HashMap<>();
        if(query==null||query.length()>4096)throw new Policy.Rejected(400,"Invalid sign-in link");
        for(String part:query.split("&")) {
            String[] pair=part.split("=",2);
            String key=URLDecoder.decode(pair[0],StandardCharsets.UTF_8);
            if(pair.length!=2||result.put(key,URLDecoder.decode(pair[1],StandardCharsets.UTF_8))!=null)
                throw new Policy.Rejected(400,"Invalid sign-in link");
        }
        return result;
    }
    void handle(HttpExchange exchange) throws IOException {
        int status=200; String type="application/json; charset=utf-8"; String output;
        try {
            String path=exchange.getRequestURI().getPath(), method=exchange.getRequestMethod();
            if(method.equals("POST")&&path.equals("/auth/discord/device")) {
                limitStarts(); required("TRAP_DISCORD_CLIENT_ID");required("TRAP_DISCORD_CLIENT_SECRET");required("TRAP_DISCORD_GUILD_ID");
                JsonObject body=RankingServer.readBody(exchange);String hash=Policy.hash(capability(body));
                UUID id=UUID.randomUUID();String browser=secret();
                try(Connection c=connections.open()) {
                    RankingStore.execute(c,"DELETE FROM discord_login_sessions WHERE expires_at<now()");
                    RankingStore.execute(c,"INSERT INTO discord_login_sessions(id,device_hash,browser_hash) VALUES (?,?,?)",id,hash,Policy.hash(browser));
                }
                JsonObject data=new JsonObject();data.addProperty("session",id.toString());data.addProperty("expiresIn",600);
                data.addProperty("url",origin()+"/auth/discord/start?session="+id+"&browser="+browser);output=JSON.toJson(data);
            } else if(method.equals("GET")&&path.equals("/auth/discord/start")) {
                Map<String,String> params=parameters(exchange.getRequestURI().getRawQuery());
                UUID id=Policy.uuid(params.getOrDefault("session",""));String browser=params.getOrDefault("browser","");String state=secret();
                try(Connection c=connections.open();PreparedStatement s=RankingStore.query(c,
                    "UPDATE discord_login_sessions SET state_hash=? WHERE id=? AND browser_hash=? AND expires_at>now() AND state_hash IS NULL RETURNING id",
                    Policy.hash(state),id,Policy.hash(browser));ResultSet r=s.executeQuery()) {
                    if(!r.next())throw new Policy.Rejected(400,"Sign-in link expired or already opened; start again in RuneLite");
                }
                String location="https://discord.com/oauth2/authorize?response_type=code&client_id="+encode(required("TRAP_DISCORD_CLIENT_ID"))
                    +"&redirect_uri="+encode(origin()+"/auth/discord/callback")+"&scope=identify%20guilds.members.read&state="+state+"&prompt=consent";
                exchange.getResponseHeaders().set("Location",location);status=302;output="{}";
            } else if(method.equals("GET")&&path.equals("/auth/discord/callback")) {
                Map<String,String> params=parameters(exchange.getRequestURI().getRawQuery());
                String state=params.getOrDefault("state","");
                if(!state.matches("[A-Za-z0-9_-]{43}")||!params.containsKey("code"))throw new Policy.Rejected(400,"Sign-in cancelled or invalid; start again in RuneLite");
                try(Connection c=connections.open()) {
                    c.setAutoCommit(false);
                    try {
                        UUID session;
                        try(PreparedStatement s=RankingStore.query(c,"SELECT id FROM discord_login_sessions WHERE state_hash=? AND expires_at>now() AND NOT callback_used FOR UPDATE",Policy.hash(state));ResultSet r=s.executeQuery()) {
                            if(!r.next())throw new Policy.Rejected(400,"Sign-in expired or already completed");session=r.getObject(1,UUID.class);
                        }
                        // Consume before contacting Discord: failed or replayed callbacks require a new login.
                        RankingStore.execute(c,"UPDATE discord_login_sessions SET callback_used=true WHERE id=?",session);c.commit();
                        String discord=snowflake(provider.identify(params.get("code")));
                        UUID member;
                        try(PreparedStatement s=RankingStore.query(c,"SELECT m.id FROM discord_member_links l JOIN members m ON m.id=l.member_id WHERE l.discord_user_id=? AND m.active",discord);ResultSet r=s.executeQuery()) {
                            if(!r.next()) {
                                c.rollback();
                                reply(exchange,403,"text/html; charset=utf-8",page("Account linking required","Ask clan leadership to link Discord user ID "+discord+" to your verified OSRS character, then sign in again. No access has been granted."));return;
                            }
                            member=r.getObject(1,UUID.class);
                        }
                        RankingStore.execute(c,"UPDATE discord_login_sessions SET member_id=? WHERE id=?",member,session);c.commit();
                    } catch(Exception failure){c.rollback();throw failure;}
                }
                type="text/html; charset=utf-8";output=page("Discord sign-in complete","Return to RuneLite. It will finish connecting automatically.");
            } else if(method.equals("POST")&&path.equals("/auth/discord/poll")) {
                JsonObject body=RankingServer.readBody(exchange);UUID id=Policy.uuid(Policy.text(body,"session",36));String hash=Policy.hash(capability(body));
                JsonObject data=new JsonObject();data.addProperty("status","pending");
                try(Connection c=connections.open()) {
                    c.setAutoCommit(false);
                    try {
                        UUID member;
                        try(PreparedStatement s=RankingStore.query(c,"SELECT member_id,last_poll FROM discord_login_sessions WHERE id=? AND device_hash=? AND expires_at>now() FOR UPDATE",id,hash);ResultSet r=s.executeQuery()) {
                            if(!r.next())throw new Policy.Rejected(410,"Sign-in expired; start again");
                            Timestamp previous=r.getTimestamp(2);
                            if(previous!=null&&previous.toInstant().plusSeconds(3).isAfter(java.time.Instant.now()))throw new Policy.Rejected(429,"Please wait before checking sign-in again");
                            member=r.getObject(1,UUID.class);
                        }
                        RankingStore.execute(c,"UPDATE discord_login_sessions SET last_poll=now() WHERE id=?",id);
                        if(member!=null) {
                            RankingStore.lockMember(c,member);
                            String token=secret();
                            RankingStore.execute(c,"INSERT INTO credentials(token_hash,member_id,expires_at) VALUES (?,?,now()+interval '90 days')",Policy.hash(token),member);
                            RankingStore.execute(c,"INSERT INTO audit(actor,action,target) VALUES (?,'DISCORD_CREDENTIAL_ISSUED',?)",member,member);
                            RankingStore.execute(c,"DELETE FROM discord_login_sessions WHERE id=?",id);
                            data.addProperty("status","complete");data.addProperty("token",token);
                        }
                        c.commit();
                    } catch(Exception failure){c.rollback();throw failure;}
                }
                output=JSON.toJson(data);
            } else throw new Policy.Rejected(404,"Unknown sign-in route");
        } catch(Policy.Rejected rejected) { status=rejected.status;JsonObject error=new JsonObject();error.addProperty("error",rejected.getMessage());output=JSON.toJson(error);
        } catch(IllegalArgumentException|JsonParseException invalid) {status=400;output="{\"error\":\"Invalid sign-in request\"}";
        } catch(Exception failure) {status=503;output="{\"error\":\"Discord sign-in unavailable; start again shortly\"}";}
        reply(exchange,status,type,output);
    }
    private static String page(String title,String message) {
        return "<!doctype html><html><meta charset='utf-8'><meta name='viewport' content='width=device-width'><title>TRAP HOUSE sign-in</title><body style='background:#11151c;color:#eee;font:18px system-ui;max-width:640px;margin:80px auto;padding:24px'><h1>"+title+"</h1><p>"+message+"</p></body></html>";
    }
    private static void reply(HttpExchange exchange,int status,String type,String body)throws IOException {
        byte[] bytes=body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type",type);exchange.getResponseHeaders().set("Cache-Control","no-store");
        exchange.getResponseHeaders().set("Referrer-Policy","no-referrer");exchange.getResponseHeaders().set("X-Content-Type-Options","nosniff");
        exchange.getResponseHeaders().set("Content-Security-Policy","default-src 'none'; style-src 'unsafe-inline'; frame-ancestors 'none'; base-uri 'none'");
        exchange.sendResponseHeaders(status,bytes.length);try(OutputStream out=exchange.getResponseBody()){out.write(bytes);}finally{exchange.close();}
    }
    private static String encode(String value){return URLEncoder.encode(value,StandardCharsets.UTF_8);}
    static String verificationError(String step,int status) {
        if(step.equals("token"))return "Discord sign-in configuration was rejected (HTTP "+status+"). Check that the Render Client ID and Client Secret belong to the same Discord application and that its redirect URL matches exactly. Then start a new sign-in from RuneLite.";
        if(step.equals("identity"))return "Discord could not verify your account (HTTP "+status+"). Start a new sign-in from RuneLite and authorize it again.";
        return "Discord server membership could not be verified (HTTP "+status+"). Check that TRAP_DISCORD_GUILD_ID is your clan server ID and that the Discord account you authorized has joined that server. Then start a new sign-in from RuneLite.";
    }
    private JsonObject fetch(HttpRequest request,String step)throws Exception {
        HttpResponse<InputStream> response=HTTP.send(request,HttpResponse.BodyHandlers.ofInputStream());
        try(InputStream input=response.body()) {
            byte[] bytes=input.readNBytes(65537);
            if(response.statusCode()!=200)throw new Policy.Rejected(403,verificationError(step,response.statusCode()));
            if(bytes.length>65536)throw new Policy.Rejected(503,"Discord returned an invalid response; start a new sign-in shortly");
            return JsonParser.parseString(new String(bytes,StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }
    private String discordIdentity(String code)throws Exception {
        String form="grant_type=authorization_code&code="+encode(code)+"&client_id="+encode(required("TRAP_DISCORD_CLIENT_ID"))
            +"&client_secret="+encode(required("TRAP_DISCORD_CLIENT_SECRET"))+"&redirect_uri="+encode(origin()+"/auth/discord/callback");
        JsonObject tokens=fetch(HttpRequest.newBuilder(URI.create("https://discord.com/api/v10/oauth2/token")).timeout(Duration.ofSeconds(15))
            .header("Content-Type","application/x-www-form-urlencoded").POST(HttpRequest.BodyPublishers.ofString(form)).build(),"token");
        String access=Policy.text(tokens,"access_token",4096);
        JsonObject user=fetch(HttpRequest.newBuilder(URI.create("https://discord.com/api/v10/users/@me")).timeout(Duration.ofSeconds(15)).header("Authorization","Bearer "+access).build(),"identity");
        fetch(HttpRequest.newBuilder(URI.create("https://discord.com/api/v10/users/@me/guilds/"+snowflake(required("TRAP_DISCORD_GUILD_ID"))+"/member"))
            .timeout(Duration.ofSeconds(15)).header("Authorization","Bearer "+access).build(),"membership");
        return snowflake(Policy.text(user,"id",20));
    }
}
