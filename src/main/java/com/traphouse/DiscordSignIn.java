package com.traphouse;

import com.google.gson.*;
import java.io.IOException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.concurrent.*;
import java.util.function.Consumer;
import javax.inject.Inject;
import javax.swing.SwingUtilities;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.util.LinkBrowser;
import okhttp3.*;

/** Device authorization: the browser never sees the plugin credential or device secret. */
final class DiscordSignIn {
    private static final class Rejected extends IOException {
        Rejected(String message){super(message);}
    }
    private final OkHttpClient http;
    private final Gson gson;
    private final ConfigManager settings;
    private final ScheduledExecutorService executor;
    private ScheduledFuture<?> poll;
    private long revision;
    @Inject DiscordSignIn(OkHttpClient http,Gson gson,ConfigManager settings,ScheduledExecutorService executor) {
        this.http=http.newBuilder().followRedirects(false).followSslRedirects(false).callTimeout(20,TimeUnit.SECONDS).build();
        this.gson=gson;this.settings=settings;this.executor=executor;
    }
    synchronized void cancel(){revision++;if(poll!=null)poll.cancel(false);poll=null;}
    private synchronized boolean current(long epoch){return revision==epoch;}
    private static void report(Consumer<String> status,String message){SwingUtilities.invokeLater(()->status.accept(message));}
    void start(TrapHouseConfig config,Consumer<String> status) {
        start(config,status,null);
    }
    void startCode(TrapHouseConfig config,Consumer<String> status,Consumer<String> showCode) {
        start(config,status,showCode);
    }
    private void start(TrapHouseConfig config,Consumer<String> status,Consumer<String> showCode) {
        cancel();final long epoch; synchronized(this){epoch=revision;}
        final String origin;
        try {origin=RankingClient.endpoint(config.rankingUrl().trim());}
        catch(IllegalArgumentException invalid){report(status,"Set a valid HTTPS ranking API address first.");return;}
        report(status,showCode==null?"Opening Discord sign-in…":"Generating Discord connection code…");
        executor.execute(()->{
            try {
                byte[] random=new byte[32];new SecureRandom().nextBytes(random);
                JsonObject request=new JsonObject();request.addProperty("secret",Base64.getUrlEncoder().withoutPadding().encodeToString(random));
                JsonObject session=post(origin+(showCode==null?"/auth/discord/device":"/auth/discord/code"),request);
                String url=showCode==null?session.get("url").getAsString():null;
                if(showCode==null&&!validBrowserUrl(origin,url))throw new IOException("Invalid sign-in address");
                String code=showCode==null?null:session.get("code").getAsString();
                if(code!=null&&!code.matches("[A-Za-z0-9_-]{43}"))throw new IOException("Invalid connection code");
                request.addProperty("session",session.get("session").getAsString());
                long deadline=System.nanoTime()+TimeUnit.MINUTES.toNanos(10);
                synchronized(this) {
                    if(!current(epoch))return;
                    SwingUtilities.invokeLater(()->{if(current(epoch)){if(showCode==null)LinkBrowser.browse(url);else showCode.accept(code);}});
                    poll=executor.scheduleWithFixedDelay(()->{
                        if(!current(epoch))return;
                        try {
                            if(System.nanoTime()>deadline)throw new IOException("Sign-in expired. Click Sign in with Discord to try again.");
                            JsonObject result=post(origin+"/auth/discord/poll",request);
                            if(!"complete".equals(result.get("status").getAsString()))return;
                            String token=result.get("token").getAsString();
                            if(!token.matches("[A-Za-z0-9_-]{43}"))throw new IOException("Invalid sign-in response");
                            synchronized(this) {
                                if(!current(epoch)||!origin.equals(RankingClient.endpoint(config.rankingUrl().trim())))return;
                                cancel();
                                settings.setConfiguration("traphouse","rankingToken",token);
                                settings.setConfiguration("traphouse","rankingEnabled",true);
                            }
                            report(status,"Connected. Log into your linked clan character to sync.");
                        } catch(IOException failure){
                            if(current(epoch)) {
                                if(failure instanceof Rejected || System.nanoTime()>deadline) {
                                    cancel();report(status,failure.getMessage());
                                } else report(status,"Connection interrupted; still waiting for Discord sign-in. Retrying automatically…");
                            }
                        } catch(Exception failure){if(current(epoch)){cancel();report(status,"Sign-in failed. Please try again.");}}
                    },5,5,TimeUnit.SECONDS);
                }
                report(status,showCode==null?"Finish signing in through your browser. First-time members need leadership to link their character.":"Use /verify code in the clan Discord within 10 minutes. Waiting for verification…");
            } catch(Exception failure){if(current(epoch))report(status,failure instanceof IOException?failure.getMessage():"Sign-in failed. Please try again.");}
        });
    }
    static boolean validBrowserUrl(String origin,String url) {
        try {java.net.URI base=java.net.URI.create(origin), target=java.net.URI.create(url);
            return base.getScheme().equals(target.getScheme())&&base.getRawAuthority().equals(target.getRawAuthority())
                &&target.getUserInfo()==null&&target.getFragment()==null&&"/auth/discord/start".equals(target.getPath());
        } catch(IllegalArgumentException invalid){return false;}
    }
    private JsonObject post(String url,JsonObject body)throws IOException {
        try(Response response=http.newCall(new Request.Builder().url(url).post(RequestBody.create(MediaType.parse("application/json"),gson.toJson(body))).build()).execute()) {
            if(response.body()==null)throw new IOException("Sign-in server returned no response.");
            if(response.body().contentLength()>65536)throw new IOException("Invalid sign-in response.");
            try {
                byte[] bytes=response.body().byteStream().readNBytes(65537);
                if(bytes.length>65536)throw new IOException("Invalid sign-in response.");
                JsonObject result=gson.fromJson(new String(bytes,java.nio.charset.StandardCharsets.UTF_8),JsonObject.class);
                if(!response.isSuccessful()) {
                    if(response.code()==429 || response.code()>=500)throw new IOException("Discord sign-in is temporarily unavailable. Please try again shortly.");
                    throw new Rejected("Sign-in expired or declined. Please try again.");
                }
                return result;
            } catch(JsonParseException invalid){throw new IOException("Invalid sign-in response.");}
        }
    }
}
