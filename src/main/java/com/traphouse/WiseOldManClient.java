package com.traphouse;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.time.Instant;
import net.runelite.client.hiscore.HiscoreSkill;
import net.runelite.client.hiscore.HiscoreSkillType;
import javax.inject.Inject;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

final class WiseOldManClient {
    static final int GROUP_ID = 2504;
    private final OkHttpClient http;
    private final Gson gson;
    private JsonArray cachedCompetitions;
    private long competitionsFetched;

    @Inject
    WiseOldManClient(OkHttpClient http, Gson gson) {
        this.http = http.newBuilder().callTimeout(20, TimeUnit.SECONDS).build();
        this.gson = gson;
    }

    static String metric(String boss) {
        if (boss == null) throw new IllegalArgumentException("Choose a boss in plugin settings.");
        String metric = boss.trim().toLowerCase(Locale.ROOT).replace("'", "").replace("\u2019", "")
            .replaceAll("[^a-z0-9]+", "_").replaceAll("^_|_$", "");
        if (metric.isEmpty()) throw new IllegalArgumentException("Choose a boss in plugin settings.");
        return metric;
    }

    /** Runs on a background executor. A null result means no active boss event exists. */
    ClanEvent fetchEvent(int competitionId) throws IOException {
        return fetchEvent(competitionId, false);
    }

    ClanEvent fetchEvent(int competitionId, boolean skillEvent) throws IOException {
        if (competitionId <= 0) {
            competitionId = selectCurrentEvent(competitions(), Instant.now(), skillEvent);
            if (competitionId == 0) return null;
        }
        return parseEvent(getJson("competitions/" + competitionId), gson, skillEvent);
    }

    private synchronized JsonArray competitions() throws IOException {
        if (cachedCompetitions == null || System.nanoTime() - competitionsFetched > TimeUnit.SECONDS.toNanos(30)) {
            cachedCompetitions = gson.fromJson(getJson("groups/" + GROUP_ID + "/competitions"), JsonArray.class);
            competitionsFetched = System.nanoTime();
        }
        return cachedCompetitions;
    }

    private String getJson(String path) throws IOException {
        Request request = new Request.Builder().url("https://api.wiseoldman.net/v2/" + path)
            .header("User-Agent", "TrapHouseRuneLite/1.0 (Wise Old Man group 2504)")
            .header("Accept", "application/json").build();
        try (Response response = http.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException(response.code() == 429 ? "Rate limited. Please wait before refreshing."
                    : "Wise Old Man returned HTTP " + response.code() + ". Check the competition ID.");
            }
            if (response.body() == null) throw new IOException("Wise Old Man returned an empty response.");
            return response.body().string();
        }
    }

    static boolean isBossMetric(String metric) {
        for (HiscoreSkill skill : HiscoreSkill.values()) {
            if (skill.getType() == HiscoreSkillType.BOSS && metric(skill.getName()).equals(metric)) return true;
        }
        // Wise Old Man calls the three Moons of Peril bosses Lunar Chests.
        return "lunar_chests".equals(metric);
    }

    static int selectCurrentEvent(JsonArray events, Instant now) {
        return selectCurrentEvent(events, now, false);
    }

    static boolean isSkillMetric(String metric) {
        for (HiscoreSkill skill : HiscoreSkill.values()) {
            if (skill.getType() == HiscoreSkillType.SKILL && !"overall".equals(metric)
                && metric(skill.getName()).equals(metric)) return true;
        }
        return false;
    }

    static int selectCurrentEvent(JsonArray events, Instant now, boolean skillEvent) {
        int id = 0;
        Instant latestStart = Instant.MIN;
        for (JsonElement element : events) {
            JsonObject event = element.getAsJsonObject();
            String metric = event.get("metric").getAsString();
            if (!(skillEvent ? isSkillMetric(metric) : isBossMetric(metric))) continue;
            Instant start = Instant.parse(event.get("startsAt").getAsString());
            Instant end = Instant.parse(event.get("endsAt").getAsString());
            if (!now.isBefore(start) && now.isBefore(end) && start.isAfter(latestStart)) {
                id = event.get("id").getAsInt();
                latestStart = start;
            }
        }
        return id;
    }

    static ClanEvent parseEvent(String json, Gson gson) {
        return parseEvent(json, gson, false);
    }

    static ClanEvent parseEvent(String json, Gson gson, boolean skillEvent) {
        JsonObject event = gson.fromJson(json, JsonObject.class);
        if (event.get("groupId") == null || event.get("groupId").isJsonNull()
            || event.get("groupId").getAsInt() != GROUP_ID) {
            throw new IllegalArgumentException("Competition must belong to Trap House group 2504.");
        }
        String metric = event.get("metric").getAsString();
        if (!(skillEvent ? isSkillMetric(metric) : isBossMetric(metric))) {
            throw new IllegalArgumentException(skillEvent ? "Choose a single-skill competition for Skill of the Month."
                : "Choose a boss competition for the boss event tab.");
        }
        List<LeaderboardEntry> scores = new ArrayList<>();
        for (JsonElement element : event.getAsJsonArray("participations")) {
            JsonObject participation = element.getAsJsonObject();
            JsonObject progress = participation.getAsJsonObject("progress");
            if (progress == null || progress.get("gained").isJsonNull()) continue;
            long gained = progress.get("gained").getAsLong();
            // Do not treat missing hiscores baselines as an earned score.
            if (gained < 0 || progress.get("start").getAsLong() < 0 || progress.get("end").getAsLong() < 0) continue;
            JsonObject player = participation.getAsJsonObject("player");
            JsonElement displayName = player.get("displayName");
            String name = displayName == null || displayName.isJsonNull()
                ? player.get("username").getAsString() : displayName.getAsString();
            scores.add(new LeaderboardEntry(name, gained));
        }
        scores.sort(Comparator.comparingLong((LeaderboardEntry entry) -> entry.score).reversed()
            .thenComparing(entry -> entry.name, String.CASE_INSENSITIVE_ORDER));
        return new ClanEvent(event.get("id").getAsInt(), event.get("title").getAsString(), metric,
            Instant.parse(event.get("startsAt").getAsString()), Instant.parse(event.get("endsAt").getAsString()),
            scores);
    }

}
