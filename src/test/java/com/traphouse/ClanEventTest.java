package com.traphouse;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import java.time.Instant;
import org.junit.Test;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okhttp3.MediaType;
import static org.junit.Assert.*;

public class ClanEventTest {
    private static final Gson GSON = new Gson();
    private static final Instant NOW = Instant.parse("2026-10-10T12:00:00Z");

    private static String event(int id, String metric, String start, String end) {
        return "{\"id\":" + id + ",\"groupId\":2504,\"title\":\"Boss event\",\"metric\":\"" + metric
            + "\",\"startsAt\":\"" + start + "\",\"endsAt\":\"" + end + "\",\"participations\":[]}";
    }

    @Test public void selectsActiveClanEventAndExcludesXpAndEndedEvents() {
        String ended = event(1, "sarachnis", "2026-09-07T04:00:00Z", "2026-09-14T04:00:00Z");
        String xp = event(2, "overall", "2025-10-13T00:00:00Z", "2026-10-20T00:00:00Z");
        String boss = event(3, "vorkath", "2026-10-09T00:00:00Z", "2026-10-16T00:00:00Z");
        assertEquals(3, WiseOldManClient.selectCurrentEvent(GSON.fromJson("[" + ended + "," + xp + "," + boss + "]", JsonArray.class), NOW));
        assertEquals(0, WiseOldManClient.selectCurrentEvent(GSON.fromJson("[" + ended + "," + xp + "]", JsonArray.class), NOW));
    }

    @Test public void eventEndIsExclusiveAndStartIsInclusive() {
        JsonArray events = GSON.fromJson("[" + event(1, "sarachnis", "2026-10-10T12:00:00Z", "2026-10-11T12:00:00Z") + "]", JsonArray.class);
        assertEquals(1, WiseOldManClient.selectCurrentEvent(events, NOW));
        assertEquals(0, WiseOldManClient.selectCurrentEvent(events, Instant.parse("2026-10-11T12:00:00Z")));
        assertEquals(0, WiseOldManClient.selectCurrentEvent(events, NOW.minusSeconds(1)));
    }

    @Test public void parsesEventGainsInsteadOfLifetimeTotals() {
        String json = event(154611, "sarachnis", "2026-09-07T04:00:00Z", "2026-09-14T04:00:00Z")
            .replace("\"participations\":[]", "\"participations\":[{\"player\":{\"displayName\":\"Member\"},\"progress\":{\"start\":133,\"end\":1108,\"gained\":975}}]");
        ClanEvent event = WiseOldManClient.parseEvent(json, GSON);
        assertEquals(975, event.scores.get(0).score);
        assertEquals(154611, event.id);
    }

    @Test(expected = IllegalArgumentException.class) public void rejectsOtherGroups() {
        WiseOldManClient.parseEvent(event(1, "sarachnis", "2026-10-01T00:00:00Z", "2026-10-20T00:00:00Z")
            .replace("2504", "99"), GSON);
    }

    @Test public void fetchesActiveCompetitionDetails() throws Exception {
        String active = event(7, "sarachnis", "2020-01-01T00:00:00Z", "2099-01-01T00:00:00Z");
        OkHttpClient http = new OkHttpClient.Builder().addInterceptor(chain -> {
            String path = chain.request().url().encodedPath();
            assertTrue(path.equals("/v2/groups/2504/competitions") || path.equals("/v2/competitions/7"));
            String json = path.endsWith("competitions") ? "[" + active + "]" : active;
            return new Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body(ResponseBody.create(MediaType.parse("application/json"), json)).build();
        }).build();
        assertEquals(7, new WiseOldManClient(http, GSON).fetchEvent(0).id);
    }

    @Test public void selectsSkillCompetitionWithoutUsingOverallXpOrBosses() {
        String xp = event(1, "overall", "2020-01-01T00:00:00Z", "2099-01-01T00:00:00Z");
        String boss = event(2, "sarachnis", "2020-01-01T00:00:00Z", "2099-01-01T00:00:00Z");
        String skill = event(3, "woodcutting", "2026-10-01T00:00:00Z", "2026-11-01T00:00:00Z");
        JsonArray events = GSON.fromJson("[" + xp + "," + boss + "," + skill + "]", JsonArray.class);
        assertEquals(3, WiseOldManClient.selectCurrentEvent(events, NOW, true));
        assertEquals(2, WiseOldManClient.selectCurrentEvent(events, NOW, false));
    }

    @Test public void parsesSkillXpGainsUsingLongValues() {
        String json = event(1, "sailing", "2026-10-01T00:00:00Z", "2026-11-01T00:00:00Z")
            .replace("\"participations\":[]", "\"participations\":[{\"player\":{\"displayName\":\"Member\"},\"progress\":{\"start\":1000000,\"end\":3001000000,\"gained\":3000000000}}]");
        ClanEvent event = WiseOldManClient.parseEvent(json, GSON, true);
        assertEquals(3000000000L, event.scores.get(0).score);
    }

    @Test(expected = IllegalArgumentException.class) public void skillTabRejectsBossCompetitionOverride() {
        WiseOldManClient.parseEvent(event(1, "sarachnis", "2026-10-01T00:00:00Z", "2026-11-01T00:00:00Z"), GSON, true);
    }
}
