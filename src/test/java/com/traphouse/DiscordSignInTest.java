package com.traphouse;

import org.junit.Test;
import static org.junit.Assert.*;

public class DiscordSignInTest {
    @Test public void browserMayOnlyOpenTheConfiguredApiLoginRoute() {
        String origin="https://clan.example";
        assertTrue(DiscordSignIn.validBrowserUrl(origin,origin+"/auth/discord/start?session=one&browser=two"));
        for(String bad:new String[]{"http://clan.example/auth/discord/start","https://evil.example/auth/discord/start",
            "https://clan.example.evil.example/auth/discord/start","https://evil@clan.example/auth/discord/start",
            "https://clan.example/auth/discord/start#token","https://clan.example/other","file:///tmp/login"})
            assertFalse(bad,DiscordSignIn.validBrowserUrl(origin,bad));
    }
}
