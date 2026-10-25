package com.traphouse.server;

import com.google.gson.JsonObject;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

final class Policy {
    static final class Rejected extends RuntimeException {
        final int status;
        Rejected(int status, String message) { super(message); this.status = status; }
    }

    static String text(JsonObject object, String key, int max) {
        if (!object.has(key) || !object.get(key).isJsonPrimitive() || !object.getAsJsonPrimitive(key).isString()) {
            throw new Rejected(400, "Missing string: " + key);
        }
        String text = object.get(key).getAsString().trim();
        if (text.isEmpty() || text.length() > max || text.chars().anyMatch(Character::isISOControl)) {
            throw new Rejected(400, "Invalid field: " + key);
        }
        return text;
    }

    static String normalize(String name) {
        return name.replace('\u00a0', ' ').trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }

    static UUID uuid(String value) {
        try {
            UUID id = UUID.fromString(value);
            if (!id.toString().equalsIgnoreCase(value)) throw new IllegalArgumentException();
            return id;
        } catch (IllegalArgumentException invalid) { throw new Rejected(400, "Invalid UUID"); }
    }

    static Instant observed(String value, Instant now) {
        try {
            Instant time = Instant.parse(value);
            if (time.isBefore(now.minus(Duration.ofHours(24))) || time.isAfter(now.plusSeconds(300))) {
                throw new Rejected(422, "Observation outside accepted time window");
            }
            return time;
        } catch (java.time.format.DateTimeParseException invalid) { throw new Rejected(400, "Invalid time"); }
    }

    static void observationType(String type) {
        if (!type.equals("LOOT") && !type.equals("COMBAT") && !type.equals("COLLECTION")) {
            throw new Rejected(400, "Unsupported event type");
        }
    }

    static void independentReviewer(boolean administrator, UUID actor, UUID recipient) {
        if (!administrator) throw new Rejected(403, "Administrator required");
        if (actor.equals(recipient)) throw new Rejected(403, "A different administrator must review this reward");
    }

    static String hash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : digest) hex.append(String.format("%02x", b & 255));
            return hex.toString();
        } catch (java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
}
