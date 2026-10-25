package com.traphouse.server;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Map;
import java.util.Properties;

/** Environment configuration; connection strings and passwords must never be logged. */
final class ServerConfig {
    final String url;
    final Properties properties = new Properties();
    final String bindAddress;
    final int port;

    ServerConfig(Map<String, String> env) {
        bindAddress = env.getOrDefault("TRAP_BIND_ADDRESS", "127.0.0.1");
        try {
            port = Integer.parseInt(env.getOrDefault("PORT", env.getOrDefault("TRAP_PORT", "8090")));
            if (port < 1 || port > 65535) throw new NumberFormatException();
        } catch (NumberFormatException invalid) { throw new IllegalArgumentException("Invalid server port"); }
        String configured = required(env, "TRAP_DATABASE_URL");
        if (configured.startsWith("jdbc:postgresql:")) {
            url = configured;
            properties.setProperty("user", required(env, "TRAP_DATABASE_USER"));
            properties.setProperty("password", required(env, "TRAP_DATABASE_PASSWORD"));
        } else {
            try {
                URI uri = URI.create(configured);
                if (!("postgresql".equals(uri.getScheme()) || "postgres".equals(uri.getScheme()))
                    || uri.getHost() == null || uri.getRawUserInfo() == null
                    || uri.getRawPath() == null || uri.getRawPath().length() < 2 || uri.getFragment() != null)
                    throw new IllegalArgumentException();
                String[] credentials = uri.getRawUserInfo().split(":", 2);
                if (credentials.length != 2) throw new IllegalArgumentException();
                properties.setProperty("user", decode(credentials[0]));
                properties.setProperty("password", decode(credentials[1]));
                url = "jdbc:postgresql://" + uri.getHost() + (uri.getPort() < 0 ? "" : ":" + uri.getPort())
                    + uri.getRawPath() + (uri.getRawQuery() == null ? "" : "?" + uri.getRawQuery());
            } catch (IllegalArgumentException invalid) {
                throw new IllegalArgumentException("Invalid PostgreSQL connection configuration");
            }
        }
        properties.setProperty("connectTimeout", "5");
        properties.setProperty("socketTimeout", "15");
    }

    private static String decode(String value) {
        return URLDecoder.decode(value.replace("+", "%2B"), StandardCharsets.UTF_8);
    }

    private static String required(Map<String, String> env, String key) {
        String value = env.get(key);
        if (value == null || value.isEmpty()) throw new IllegalStateException("Missing environment variable: " + key);
        return value;
    }

    Connection connect() throws SQLException { return DriverManager.getConnection(url, properties); }
}
