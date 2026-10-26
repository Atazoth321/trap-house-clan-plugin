package com.traphouse.server;

import com.google.gson.*;
import com.sun.net.httpserver.HttpExchange;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.spec.X509EncodedKeySpec;
import java.sql.*;
import java.time.Instant;
import java.util.*;

/** Public endpoint: verify Discord Ed25519 signatures over the exact raw request bytes. */
final class DiscordInteractions {
    private final DiscordWorker.Connections connections;
    private final Map<String,String> env;
    DiscordInteractions(DiscordWorker.Connections connections,Map<String,String> env){this.connections=connections;this.env=env;}
    static byte[] hex(String value) {
        if(value==null||value.length()%2!=0||!value.matches("[0-9a-fA-F]+"))throw new IllegalArgumentException("Invalid hex");
        byte[] out=new byte[value.length()/2];for(int i=0;i<out.length;i++)out[i]=(byte)Integer.parseInt(value.substring(i*2,i*2+2),16);return out;
    }
    static boolean verify(String key,String signature,String timestamp,byte[] body,Instant now) {
        try {
            if(key==null||key.length()!=64||signature==null||signature.length()!=128||timestamp==null||!timestamp.matches("[0-9]{10}"))return false;
            if(Math.abs(now.getEpochSecond()-Long.parseLong(timestamp))>300)return false;
            byte[] prefix=hex("302a300506032b6570032100"),raw=hex(key),encoded=Arrays.copyOf(prefix,prefix.length+raw.length);System.arraycopy(raw,0,encoded,prefix.length,raw.length);
            Signature verifier=Signature.getInstance("Ed25519");verifier.initVerify(KeyFactory.getInstance("Ed25519").generatePublic(new X509EncodedKeySpec(encoded)));
            verifier.update(timestamp.getBytes(StandardCharsets.UTF_8));verifier.update(body);return verifier.verify(hex(signature));
        }catch(Exception invalid){return false;}
    }
    void handle(HttpExchange exchange)throws IOException {
        int status=200;JsonObject response;
        try {
            if(!exchange.getRequestMethod().equals("POST"))throw new Policy.Rejected(405,"Use POST");
            String publicKey=env.get("TRAP_DISCORD_PUBLIC_KEY");
            if(publicKey==null||!publicKey.matches("[0-9a-fA-F]{64}"))throw new Policy.Rejected(503,"Discord interactions are not configured");
            byte[] body=exchange.getRequestBody().readNBytes(16385);if(body.length>16384)throw new Policy.Rejected(413,"Request too large");
            if(!verify(publicKey,exchange.getRequestHeaders().getFirst("X-Signature-Ed25519"),exchange.getRequestHeaders().getFirst("X-Signature-Timestamp"),body,Instant.now()))throw new Policy.Rejected(401,"Invalid Discord signature");
            JsonObject interaction=JsonParser.parseString(new String(body,StandardCharsets.UTF_8)).getAsJsonObject();
            if(!Objects.equals(env.get("TRAP_DISCORD_CLIENT_ID"),Policy.text(interaction,"application_id",20)))throw new Policy.Rejected(403,"Wrong Discord application");
            if(interaction.get("type").getAsInt()==1){response=new JsonObject();response.addProperty("type",1);}
            else {
                if(!Objects.equals(env.get("TRAP_DISCORD_GUILD_ID"),Policy.text(interaction,"guild_id",20)))throw new Policy.Rejected(403,"Use this action inside the configured clan Discord server");
                String discord=DiscordLogin.snowflake(Policy.text(interaction.getAsJsonObject("member").getAsJsonObject("user"),"id",20));
                String id=DiscordLogin.snowflake(Policy.text(interaction,"id",20));
                try(Connection c=connections.open()) {
                    c.setAutoCommit(false);
                    try {
                        RankingStore.execute(c,"SET LOCAL statement_timeout='2s'");RankingStore.execute(c,"SET LOCAL lock_timeout='1s'");
                        // Response caching makes Discord retries idempotent, including modal submits.
                        int inserted;
                        try(PreparedStatement s=RankingStore.query(c,"INSERT INTO discord_interactions(id,response) VALUES (?,'{}') ON CONFLICT DO NOTHING",id)){inserted=s.executeUpdate();}
                        if(inserted==0) {
                            try(PreparedStatement s=RankingStore.query(c,"SELECT response::text FROM discord_interactions WHERE id=?",id);ResultSet r=s.executeQuery()){r.next();response=DiscordJoining.object(r.getString(1));}
                        } else {
                            Savepoint operation=c.setSavepoint();
                            try {response=DiscordJoining.dispatch(c,interaction,discord);}
                            catch(Policy.Rejected rejected){c.rollback(operation);response=DiscordJoining.message(rejected.getMessage());}
                            RankingStore.execute(c,"UPDATE discord_interactions SET response=?::jsonb WHERE id=?",response.toString(),id);
                        }
                        c.commit();
                    }catch(Exception failure){c.rollback();throw failure;}
                }
            }
        }catch(Policy.Rejected rejected){status=rejected.status;response=new JsonObject();response.addProperty("error",rejected.getMessage());}
        catch(JsonParseException|IllegalArgumentException|NullPointerException invalid){status=400;response=DiscordJoining.object("{\"error\":\"Invalid Discord request\"}");}
        catch(Exception unavailable){response=DiscordJoining.message("Linking is temporarily unavailable. No partial approval was saved. Please retry shortly.");}
        byte[] bytes=response.toString().getBytes(StandardCharsets.UTF_8);exchange.getResponseHeaders().set("Content-Type","application/json; charset=utf-8");exchange.getResponseHeaders().set("Cache-Control","no-store");
        exchange.sendResponseHeaders(status,bytes.length);try(OutputStream out=exchange.getResponseBody()){out.write(bytes);}finally{exchange.close();}
    }
}
