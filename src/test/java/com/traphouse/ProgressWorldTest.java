package com.traphouse;

import java.lang.reflect.Proxy;
import java.util.EnumSet;
import net.runelite.api.Client;
import net.runelite.api.WorldType;
import org.junit.Test;
import static org.junit.Assert.*;

public class ProgressWorldTest {
    private Client client(EnumSet<WorldType> types) {
        return (Client) Proxy.newProxyInstance(Client.class.getClassLoader(), new Class<?>[]{Client.class},
            (proxy, method, args) -> {
                if (method.getName().equals("getWorldType")) return types;
                throw new AssertionError("Must check world before reading progress: " + method.getName());
            });
    }

    @Test public void rejectsTemporaryProfilesEvenOnMembersWorlds() {
        for (WorldType type : new WorldType[]{WorldType.PVP_ARENA, WorldType.QUEST_SPEEDRUNNING,
                WorldType.BETA_WORLD, WorldType.NOSAVE_MODE, WorldType.TOURNAMENT_WORLD}) {
            assertFalse(type.name(), ProgressWorld.allowsTracking(client(EnumSet.of(WorldType.MEMBERS, type))));
        }
    }

    @Test public void permitsOrdinaryAndPvpWorlds() {
        assertTrue(ProgressWorld.allowsTracking(client(EnumSet.noneOf(WorldType.class))));
        assertTrue(ProgressWorld.allowsTracking(client(EnumSet.of(WorldType.MEMBERS))));
        assertTrue(ProgressWorld.allowsTracking(client(EnumSet.of(WorldType.MEMBERS, WorldType.PVP))));
        assertFalse(ProgressWorld.allowsTracking(client(null)));
    }
}
