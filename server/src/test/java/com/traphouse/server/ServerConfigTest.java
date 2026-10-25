package com.traphouse.server;

import java.util.HashMap;
import java.util.Map;
import org.junit.Test;
import static org.junit.Assert.*;

public class ServerConfigTest {
    private Map<String, String> render() {
        Map<String, String> env = new HashMap<>();
        env.put("TRAP_DATABASE_URL", "postgresql://clan:p%40ss+word@db.internal:5432/traphouse?sslmode=require");
        return env;
    }

    @Test public void renderUrlPreservesEncodedCredentialsAndOptions() {
        ServerConfig config = new ServerConfig(render());
        assertEquals("jdbc:postgresql://db.internal:5432/traphouse?sslmode=require", config.url);
        assertEquals("clan", config.properties.getProperty("user"));
        assertEquals("p@ss+word", config.properties.getProperty("password"));
        assertEquals("127.0.0.1", config.bindAddress);
        assertEquals(8090, config.port);
    }

    @Test public void renderPortTakesPrecedenceAndBindingIsExplicit() {
        Map<String, String> env = render();
        env.put("PORT", "10000"); env.put("TRAP_PORT", "8091"); env.put("TRAP_BIND_ADDRESS", "0.0.0.0");
        ServerConfig config = new ServerConfig(env);
        assertEquals(10000, config.port);
        assertEquals("0.0.0.0", config.bindAddress);
    }

    @Test public void existingJdbcConfigurationStillWorks() {
        Map<String, String> env = new HashMap<>();
        env.put("TRAP_DATABASE_URL", "jdbc:postgresql://localhost/clan");
        env.put("TRAP_DATABASE_USER", "operator"); env.put("TRAP_DATABASE_PASSWORD", "secret");
        ServerConfig config = new ServerConfig(env);
        assertEquals("jdbc:postgresql://localhost/clan", config.url);
        assertEquals("operator", config.properties.getProperty("user"));
    }

    @Test public void invalidConfigurationDoesNotExposeConnectionSecrets() {
        Map<String, String> env = render();
        env.put("TRAP_DATABASE_URL", "postgresql://name:TOPSECRET@/missing-host");
        try { new ServerConfig(env); fail(); }
        catch (IllegalArgumentException invalid) {
            assertFalse(invalid.toString().contains("TOPSECRET"));
            assertNull(invalid.getCause());
        }
        for (String port : new String[]{"0", "65536", "invalid"}) {
            env = render(); env.put("PORT", port);
            try { new ServerConfig(env); fail(); }
            catch (IllegalArgumentException expected) { assertEquals("Invalid server port", expected.getMessage()); }
        }
    }
}
