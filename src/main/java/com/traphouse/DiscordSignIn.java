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
        cancel();final long epoch; synchronized(this){epoch=revision;}
        final String origin;
        try {origin=RankingClient.endpoint(config.rankingUrl().trim());}
        catch(IllegalArgumentException invalid){report(status,"Set a valid HTTPS ranking API address first.");return;}
        report(status,"Opening Discord sign-in…");
        executor.execute(()->{
            try {
                byte[] random=new byte[32];new SecureRandom().nextBytes(random);
                JsonObject request=new JsonObject();request.addProperty("secret",Base64.getUrlEncoder().withoutPadding().encodeToString(random));
                JsonObject session=post(origin+"/auth/discord/device",request);
                String url=session.get("url").getAsString();
                if(!validBrowserUrl(origin,url))throw new IOException("Invalid sign-in address");
                request.addProperty("session",session.get("session").getAsString());
                long deadline=System.nanoTime()+TimeUnit.MINUTES.toNanos(10);
                synchronized(this) {
                    if(!current(epoch))return;
                    SwingUtilities.invokeLater(()->{if(current(epoch))LinkBrowser.browse(url);});
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
                        } catch(Exception failure){if(current(epoch)){cancel();report(status,failure instanceof IOException?failure.getMessage():"Sign-in failed. Please try again.");}}
                    },5,5,TimeUnit.SECONDS);
                }
                report(status,"Finish signing in through your browser. First-time members need leadership to link their character.");
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
                if(!response.isSuccessful())throw new IOException(response.code()==503?"Discord sign-in is not available yet. Ask leadership to check its Render setup.":"Sign-in expired or declined. Please try again.");
                return result;
            } catch(JsonParseException invalid){throw new IOException("Invalid sign-in response.");}
        }
    }
}
