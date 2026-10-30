package com.traphouse.server;

import com.google.gson.*;
import com.sun.net.httpserver.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.sql.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;

/** Deploy behind an HTTPS reverse proxy with body/time/rate limits. */
public final class RankingServer {
    private static final Gson JSON = new Gson();
    private final DiscordWorker.Connections connections;
    private final DiscordLogin discordLogin;
    private final DiscordInteractions discordInteractions;
    private final Map<UUID, Window> windows = new HashMap<>();
    private static final class Window { long minute; int count; }

    RankingServer(DiscordWorker.Connections connections) { this.connections = connections; this.discordLogin=new DiscordLogin(connections);this.discordInteractions=new DiscordInteractions(connections,System.getenv()); }

    private synchronized void rateLimit(UUID member) {
        long minute = System.currentTimeMillis() / 60000;
        windows.entrySet().removeIf(e -> e.getValue().minute < minute - 1);
        Window window = windows.get(member);
        if (window == null) {
            if (windows.size() >= 10000) throw new Policy.Rejected(429, "Server busy");
            window = new Window(); windows.put(member, window);
        }
        if (window.minute != minute) { window.minute = minute; window.count = 0; }
        if (++window.count > 120) throw new Policy.Rejected(429, "Rate limit reached");
    }

    void handle(HttpExchange exchange) throws IOException {
        if(exchange.getRequestURI().getPath().equals("/discord/interactions")){discordInteractions.handle(exchange);return;}
        if(DiscordLogin.matches(exchange.getRequestURI().getPath())) { discordLogin.handle(exchange); return; }
        int status = 200;
        JsonElement result;
        try {
            String method = exchange.getRequestMethod();
            String path = exchange.getRequestURI().getPath();
            if ("GET".equals(method) && "/health".equals(path)) {
                JsonObject health = new JsonObject(); health.addProperty("status", "ok"); result = health;
            } else if ("GET".equals(method) && "/ready".equals(path)) {
                try (Connection c = connections.open(); Statement s = c.createStatement()) {
                    s.executeQuery("SELECT 1 FROM members LIMIT 1").close();
                }
                JsonObject health = new JsonObject(); health.addProperty("status", "ready"); result = health;
            } else {
                JsonObject body = "POST".equals(method) ? readBody(exchange) : new JsonObject();
                try (Connection c = connections.open()) {
                    c.setAutoCommit(false);
                    try {
                        RankingStore.execute(c, "SET LOCAL statement_timeout='10s'");
                        RankingStore.execute(c, "SET LOCAL lock_timeout='5s'");
                        RankingStore.Actor actor = RankingStore.authenticate(c, exchange.getRequestHeaders().getFirst("Authorization"));
                        rateLimit(actor.id);
                        result = route(c, actor, method, path, exchange.getRequestURI().getRawQuery(), body);
                        c.commit();
                    } catch (Exception failure) { c.rollback(); throw failure; }
                }
            }
        } catch (Policy.Rejected rejection) {
            status = rejection.status; result = error(rejection.getMessage());
        } catch (JsonParseException | IllegalStateException | IllegalArgumentException malformed) {
            status = 400; result = error("Malformed request");
        } catch (Exception failure) {
            // Never log request headers, tokens, webhook URLs, SQL text, or database credentials.
            System.err.println("Ranking request failed: " + failure.getClass().getSimpleName());
            status = 503; result = error("Service unavailable; retry with the same request ID");
        }
        byte[] bytes = JSON.toJson(result).getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
        if (status == 429) exchange.getResponseHeaders().set("Retry-After", "60");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream output = exchange.getResponseBody()) { output.write(bytes); }
        finally { exchange.close(); }
    }

    static JsonObject readBody(HttpExchange exchange) throws IOException {
        String contentType = exchange.getRequestHeaders().getFirst("Content-Type");
        if (contentType == null || !contentType.toLowerCase(Locale.ROOT).matches("application/json(?:;.*)?")) {
            throw new Policy.Rejected(415, "application/json required");
        }
        byte[] bytes = exchange.getRequestBody().readNBytes(16385);
        if (bytes.length > 16384) throw new Policy.Rejected(413, "Request too large");
        JsonElement parsed = JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8));
        if (!parsed.isJsonObject()) throw new Policy.Rejected(400, "JSON object required");
        return parsed.getAsJsonObject();
    }

    static JsonElement route(Connection c, RankingStore.Actor actor, String method, String path, String query, JsonObject body) throws SQLException {
        if(method.equals("POST")&&path.matches("/v1/admin/members/[0-9a-fA-F-]{36}/(rank|points|permission)")) {
            String[] parts=path.split("/"); UUID member=Policy.uuid(parts[4]);
            if(parts[5].equals("rank"))return RankSecurity.changeRank(c,actor,member,body);
            if(parts[5].equals("points"))return RankSecurity.adjust(c,actor,member,body);
            return RankSecurity.permission(c,actor,member,body);
        }
        if(method.equals("POST")&&path.matches("/v1/admin/clan-events/[0-9a-fA-F-]{36}/publish"))
            return RankSecurity.publishEvent(c,actor,Policy.uuid(path.split("/")[4]),body);
        if(method.equals("GET")&&path.equals("/v1/admin/audit")) {
            long after=0;
            if(query!=null) {
                if(!query.matches("after=[0-9]{1,18}"))throw new Policy.Rejected(400,"Expected after audit ID");
                after=Long.parseLong(query.substring(6));
            }
            return RankSecurity.auditLog(c,actor,after);
        }
        if(method.equals("GET")&&path.equals("/v1/point-notifications"))return PointNotifications.read(c,actor,query);
        if(method.equals("GET")&&path.equals("/v1/points-log"))return PointsLog.read(c,query);
        if(method.equals("GET")&&path.equals("/v1/members"))return ClanFeatures.member(c,playerQuery(query));
        if(method.equals("POST")&&path.equals("/v1/progress"))return ClanFeatures.sync(c,actor,body);
        if(method.equals("POST")&&path.matches("/v1/clan-events/[0-9a-fA-F-]{36}/(register|cancel)")) {
            String[] parts=path.split("/");return ClanFeatures.register(c,actor,Policy.uuid(parts[3]),parts[4].equals("register"));
        }
        if (method.equals("GET") && path.equals("/v1/dashboard")) return RankingStore.dashboard(c, actor, playerQuery(query));
        if (method.equals("POST") && path.equals("/v1/events")) return RankingStore.event(c, actor, body);
        if (method.equals("POST") && path.equals("/v1/manual-rewards")) return RankingStore.requestReward(c, actor, body);
        if (method.equals("GET") && path.equals("/v1/admin/rewards")) return RankingStore.pending(c, actor);
        if (method.equals("POST") && path.matches("/v1/admin/rewards/[0-9a-fA-F-]{36}/(approve|reject)")) {
            String[] parts = path.split("/");
            return RankingStore.review(c, actor, Policy.uuid(parts[4]), parts[5].equals("approve"));
        }
        throw new Policy.Rejected(404, "Unknown API route");
    }

    static String playerQuery(String query) {
        if (query == null || query.length() > 200 || !query.startsWith("player=") || query.contains("&")) {
            throw new Policy.Rejected(400, "Exactly one player parameter required");
        }
        String player = URLDecoder.decode(query.substring(7), StandardCharsets.UTF_8);
        if (player.isEmpty() || player.length() > 12) throw new Policy.Rejected(400, "Invalid character name");
        return player;
    }

    private static JsonObject error(String message) { JsonObject error = new JsonObject(); error.addProperty("error", message); return error; }

    static Connection connect() throws SQLException {
        return new ServerConfig(System.getenv()).connect();
    }

    public static void main(String[] args) throws Exception {
        if (args.length > 0) { command(args); return; }
        // Fail fast before accepting traffic; migrations are a separate explicit deployment step.
        try (Connection c = connect(); Statement s = c.createStatement()) { s.executeQuery("SELECT 1 FROM members LIMIT 1").close(); }
        RankingServer service = new RankingServer(RankingServer::connect);
        ServerConfig config = new ServerConfig(System.getenv());
        int port = config.port;
        HttpServer server = HttpServer.create(new InetSocketAddress(config.bindAddress, port), 64);
        ThreadPoolExecutor pool = new ThreadPoolExecutor(8, 8, 0, TimeUnit.SECONDS, new ArrayBlockingQueue<>(64));
        server.setExecutor(pool);
        server.createContext("/", service::handle);
        ScheduledExecutorService notifications = Executors.newSingleThreadScheduledExecutor();
        notifications.scheduleWithFixedDelay(() -> RankSecurity.promotions(RankingServer::connect),10,30,TimeUnit.SECONDS);
        String webhook = System.getenv("TRAP_DISCORD_WEBHOOK");
        if (webhook != null && !webhook.isBlank()) notifications.scheduleWithFixedDelay(new DiscordWorker(RankingServer::connect, webhook), 5, 5, TimeUnit.SECONDS);
        String bot=System.getenv("TRAP_DISCORD_BOT_TOKEN");
        if(bot!=null&&!bot.isBlank())notifications.scheduleWithFixedDelay(new DiscordJoinBot(RankingServer::connect,System.getenv()),5,5,TimeUnit.SECONDS);
        if(!"false".equalsIgnoreCase(System.getenv("TRAP_WOM_STATS_ENABLED")))notifications.scheduleWithFixedDelay(new WiseOldManStats(RankingServer::connect),15,30,TimeUnit.SECONDS);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> { server.stop(2); pool.shutdownNow(); notifications.shutdownNow(); }));
        server.start();
        System.out.println("TRAP HOUSE ranking API listening on port " + port);
    }

    static String verifiedName(String value) {
        String name = value.trim();
        if (!name.matches("[A-Za-z0-9 _-]{1,12}")) throw new IllegalArgumentException("Invalid character name");
        return name;
    }

    static void command(String[] args) throws Exception {
        command(args, RankingServer::connect);
    }

    static void command(String[] args, DiscordWorker.Connections connections) throws Exception {
        if(args.length==1&&args[0].equals("discord-setup")){new DiscordJoinBot(connections,System.getenv()).setup();System.out.println("Discord commands registered and Link RuneLite button posted.");return;}
        if(args.length==1&&args[0].equals("discord-commands")){new DiscordJoinBot(connections,System.getenv()).setup(false);System.out.println("Discord link commands registered. No new button message posted.");return;}
        if(args.length==1&&args[0].equals("discord-clean-reviews")){int queued=new DiscordJoinBot(connections,System.getenv()).cleanReviewedMessages();System.out.println("Queued "+queued+" reviewed link messages. The worker will copy each decision to the staff log before removing its review message.");return;}
        if (args.length == 2 && args[0].equals("add-member")) verifiedName(args[1]);
        if(args.length==4&&args[0].equals("correct-member-name")) {
            verifiedName(args[1]);verifiedName(args[2]);
            if(args[3].trim().length()<10||args[3].length()>1000)throw new IllegalArgumentException("Give a correction reason of 10–1000 characters");
        }
        try (Connection c = connections.open()) {
            c.setAutoCommit(false);
            if(args.length==4&&args[0].equals("correct-member-name")) {
                String oldName=verifiedName(args[1]),newName=verifiedName(args[2]);UUID member;String storedName;
                try(PreparedStatement s=RankingStore.query(c,"SELECT id,name FROM members WHERE normalized_name=? AND active FOR UPDATE",Policy.normalize(oldName));ResultSet r=s.executeQuery()) {
                    if(!r.next())throw new IllegalArgumentException("Original active member not found; no correction made");
                    member=r.getObject(1,UUID.class);storedName=r.getString(2);
                }
                try(PreparedStatement s=RankingStore.query(c,"SELECT id FROM members WHERE normalized_name=? AND id<>?",Policy.normalize(newName),member);ResultSet r=s.executeQuery()) {
                    if(r.next())throw new IllegalArgumentException("Correct name already has a member record; operator review is required. No records were merged");
                }
                String discord;
                try(PreparedStatement s=RankingStore.query(c,"SELECT discord_user_id FROM discord_member_links WHERE member_id=? FOR UPDATE",member);ResultSet r=s.executeQuery()) {
                    if(!r.next())throw new IllegalArgumentException("Original member has no Discord link; no correction made");
                    discord=r.getString(1);
                }
                RankingStore.execute(c,"UPDATE members SET name=?,normalized_name=? WHERE id=?",newName,Policy.normalize(newName),member);
                RankingStore.execute(c,"UPDATE credentials SET revoked=true WHERE member_id=? AND NOT revoked",member);
                RankingStore.execute(c,"DELETE FROM discord_login_sessions WHERE member_id=?",member);
                JsonObject details=new JsonObject();details.addProperty("source","server operator CLI");
                details.addProperty("oldName",storedName);details.addProperty("newName",newName);
                details.addProperty("discordUserId",discord);details.addProperty("reason",args[3].trim());
                operatorAudit(c,member,"MEMBER_NAME_CORRECTED",details);
                c.commit();
                System.out.println("Corrected "+storedName+" to "+newName+". Member UUID, Discord link, points and history preserved. Old credentials revoked; sign in with Discord again.");return;
            }
            if(args.length==3&&args[0].equals("link-discord")) {
                String name=verifiedName(args[1]), discord=DiscordLogin.snowflake(args[2]);
                UUID member;
                try(PreparedStatement s=RankingStore.query(c,"SELECT id FROM members WHERE normalized_name=? AND active FOR UPDATE",Policy.normalize(name));ResultSet r=s.executeQuery()) {
                    if(!r.next())throw new IllegalArgumentException("Add and independently verify an active member first");member=r.getObject(1,UUID.class);
                }
                // Unique constraints reject relinking either identity. Never silently replace an existing link.
                RankingStore.execute(c,"INSERT INTO discord_member_links(discord_user_id,member_id) VALUES (?,?) ON CONFLICT DO NOTHING",discord,member);
                try(PreparedStatement s=RankingStore.query(c,"SELECT 1 FROM discord_member_links WHERE discord_user_id=? AND member_id=?",discord,member);ResultSet r=s.executeQuery()) {
                    if(!r.next())throw new IllegalArgumentException("An identity is already linked; review and revoke the old link first");
                }
                JsonObject details=new JsonObject();details.addProperty("source","server operator CLI");details.addProperty("discordUserId",discord);
                operatorAudit(c,member,"DISCORD_IDENTITY_LINKED",details);
                c.commit();System.out.println("Discord identity linked to the verified member. No permissions changed.");return;
            }
            if (args.length == 2 && args[0].equals("add-member")) {
                // Operator-only onboarding, after independently verifying membership and character ownership.
                String name = verifiedName(args[1]);
                UUID proposed = UUID.randomUUID();
                boolean created;
                try (PreparedStatement s = RankingStore.query(c,
                    "INSERT INTO members(id,name,normalized_name) VALUES (?,?,?) ON CONFLICT(normalized_name) DO NOTHING",
                    proposed, name, Policy.normalize(name))) { created = s.executeUpdate() == 1; }
                UUID member;
                try (PreparedStatement s = RankingStore.query(c, "SELECT id,active FROM members WHERE normalized_name=? FOR UPDATE", Policy.normalize(name));
                     ResultSet r = s.executeQuery()) {
                    if (!r.next() || !r.getBoolean("active")) throw new IllegalArgumentException("Member is inactive; review roster before reactivating");
                    member = r.getObject("id", UUID.class);
                }
                if (created) {
                    JsonObject details = new JsonObject();
                    details.addProperty("source", "server operator CLI");
                    details.addProperty("memberName", name);
                    operatorAudit(c,member,"MEMBER_PROVISIONED",details);
                }
                c.commit();
                System.out.println("Verified member UUID: " + member);
                return;
            }
            if (args.length == 1 && args[0].equals("init")) {
                try (InputStream input = RankingServer.class.getResourceAsStream("/schema.sql"); Statement s = c.createStatement()) {
                    if (input == null) throw new IllegalStateException("Schema resource missing");
                    s.execute(new String(input.readAllBytes(), StandardCharsets.UTF_8));
                }
                try(InputStream input=RankingServer.class.getResourceAsStream("/starter-goals.sql");Statement s=c.createStatement()) {
                    if(input==null)throw new IllegalStateException("Starter goals missing");s.execute(new String(input.readAllBytes(),StandardCharsets.UTF_8));
                }
                try(InputStream input=RankingServer.class.getResourceAsStream("/rank-thresholds-v1.sql");Statement s=c.createStatement()) {
                    if(input==null)throw new IllegalStateException("Rank threshold migration missing");s.execute(new String(input.readAllBytes(),StandardCharsets.UTF_8));
                }
                MilestoneCatalog.seed(c);
                MilestoneCatalog.applyRewards(c);
                MilestoneCatalog.applyRewardBoost(c);
                MilestoneCatalog.applyCurrentRewards(c);
                try(InputStream input=RankingServer.class.getResourceAsStream("/activity-rewards-v3.sql");Statement s=c.createStatement()) {
                    if(input==null)throw new IllegalStateException("Activity rewards missing");
                    s.execute(new String(input.readAllBytes(),StandardCharsets.UTF_8));
                }
                AutomaticRewards.seed(c);
                try(InputStream input=RankingServer.class.getResourceAsStream("/discord-joining.sql");Statement s=c.createStatement()) {
                    if(input==null)throw new IllegalStateException("Discord joining schema missing");s.execute(new String(input.readAllBytes(),StandardCharsets.UTF_8));
                }
                try(InputStream input=RankingServer.class.getResourceAsStream("/achievement-diaries.sql");Statement s=c.createStatement()) {
                    if(input==null)throw new IllegalStateException("Achievement diary definitions missing");
                    s.execute(new String(input.readAllBytes(),StandardCharsets.UTF_8));
                }
                c.commit(); System.out.println("Schema initialized."); return;
            }
            if(args.length==2&&args[0].equals("bootstrap-owner")) {
                UUID member=Policy.uuid(args[1]);RankingStore.lockMember(c,member);
                RankingStore.execute(c,"INSERT INTO member_permissions VALUES (?,'OWNER') ON CONFLICT(member_id) DO UPDATE SET permission='OWNER'",member);
                JsonObject details=new JsonObject();details.addProperty("source","server operator CLI");
                RankSecurity.audit(c,new RankingStore.Actor(member,"",RankSecurity.Role.OWNER),"OWNER_BOOTSTRAPPED",member,details);
                c.commit();System.out.println("Owner permission provisioned. Existing member credentials now use Owner permission.");return;
            }
            if (args.length == 3 && args[0].equals("issue-token") && (args[2].equals("member") || args[2].equals("admin"))) {
                String name = verifiedName(args[1]);
                // Staff must independently verify membership and ownership before issuing credentials.
                UUID member;
                try (PreparedStatement s = RankingStore.query(c, "SELECT id FROM members WHERE normalized_name=? AND active", Policy.normalize(name)); ResultSet r = s.executeQuery()) {
                    if (!r.next()) throw new IllegalArgumentException("Add and verify an active member before issuing a token");
                    member = r.getObject(1, UUID.class);
                }
                byte[] random = new byte[32]; new SecureRandom().nextBytes(random);
                String token = Base64.getUrlEncoder().withoutPadding().encodeToString(random);
                RankingStore.execute(c, "INSERT INTO credentials(token_hash,member_id,administrator,expires_at) VALUES (?,?,?,?)", Policy.hash(token), member, args[2].equals("admin"), Timestamp.from(Instant.now().plusSeconds(90L * 86400)));
                RankingStore.execute(c, "INSERT INTO audit(actor,action,target) VALUES (?,'CREDENTIAL_ISSUED',?)", member, member);
                c.commit();
                System.out.println("Credential (shown once; expires in 90 days): " + token); return;
            }
            throw new IllegalArgumentException("Commands: init | discord-setup | discord-commands | add-member <verified-character-name> | correct-member-name <old-name> <verified-correct-name> <reason> | link-discord <verified-character-name> <discord-user-id> | bootstrap-owner <verified-member-uuid> | issue-token <verified-character-name> member|admin");
        }
    }

    static void operatorAudit(Connection c, UUID member, String action, JsonObject details) throws SQLException {
        // Legacy audit.actor is a required member FK, including for bootstrap operations.
        // Explicit metadata distinguishes operator actions from authenticated member requests.
        details.addProperty("actorKind","SERVER_OPERATOR");
        details.addProperty("actorReference","affected member; operator command has no authenticated API actor");
        RankingStore.execute(c,"INSERT INTO audit(actor,action,target,details) VALUES (?,?,?,?::jsonb)",member,action,member,details.toString());
    }
}
