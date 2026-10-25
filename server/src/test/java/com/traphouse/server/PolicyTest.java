package com.traphouse.server;

import com.google.gson.*;
import java.time.Instant;
import java.util.UUID;
import org.junit.Test;
import static org.junit.Assert.*;

public class PolicyTest {
    private void rejected(int status, Runnable call) {
        try { call.run(); fail("Expected rejection"); }
        catch (Policy.Rejected rejected) { assertEquals(status, rejected.status); }
    }

    @Test public void manualRewardsRequireIndependentAdministrator() {
        UUID member = UUID.randomUUID(), reviewer = UUID.randomUUID();
        rejected(403, () -> Policy.independentReviewer(false, reviewer, member));
        rejected(403, () -> Policy.independentReviewer(true, member, member));
        Policy.independentReviewer(true, reviewer, member);
    }

    @Test public void rejectsReplaysOutsideTimeWindow() {
        Instant now = Instant.parse("2026-10-25T12:00:00Z");
        assertEquals(now, Policy.observed(now.toString(), now));
        rejected(422, () -> Policy.observed(now.minusSeconds(86401).toString(), now));
        rejected(422, () -> Policy.observed(now.plusSeconds(301).toString(), now));
        rejected(400, () -> Policy.observed("bad", now));
    }

    @Test public void rejectsMalformedAndUnboundedFields() {
        JsonObject body = new JsonObject();
        rejected(400, () -> Policy.text(body, "key", 4));
        body.addProperty("key", 12);
        rejected(400, () -> Policy.text(body, "key", 4));
        body.addProperty("key", "12345");
        rejected(400, () -> Policy.text(body, "key", 4));
        body.addProperty("key", "a\nb");
        rejected(400, () -> Policy.text(body, "key", 4));
        rejected(400, () -> Policy.uuid("1-1-1-1-1"));
        rejected(400, () -> Policy.observationType("MANUAL"));
        rejected(400, () -> Policy.observationType("POINTS"));
    }

    @Test public void normalizesNamesAndRequiresUnambiguousPlayerQuery() {
        assertEquals("clan member", Policy.normalize(" Clan\u00a0Member "));
        assertEquals("Clan Member", RankingServer.playerQuery("player=Clan+Member"));
        rejected(400, () -> RankingServer.playerQuery("player=One&player=Two"));
        rejected(400, () -> RankingServer.playerQuery(null));
    }

    @Test public void hashesCredentialsWithoutStoringBearerSecret() {
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", Policy.hash("abc"));
    }

    @Test public void discordCannotRedirectSecretsOrPingMembers() {
        DiscordWorker.validate("https://discord.com/api/webhooks/123/abc_DEF-1");
        for (String url : new String[] {"http://discord.com/api/webhooks/123/abc", "https://example.com/api/webhooks/123/abc", "https://discord.com.evil.com/api/webhooks/123/abc", "https://discord.com/api/webhooks/123/abc?x=1"}) {
            try { DiscordWorker.validate(url); fail(url); } catch (IllegalArgumentException expected) { }
        }
        JsonObject body = JsonParser.parseString(DiscordWorker.payload("@everyone reward")).getAsJsonObject();
        assertEquals(0, body.getAsJsonObject("allowed_mentions").getAsJsonArray("parse").size());
    }
}
