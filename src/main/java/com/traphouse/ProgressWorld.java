package com.traphouse;

import java.util.Set;
import net.runelite.api.Client;
import net.runelite.api.WorldType;

/** Worlds with temporary character profiles must never supply permanent progress. */
final class ProgressWorld {
    static boolean allowsTracking(Client client) {
        Set<WorldType> types = client.getWorldType();
        return types != null
            && !types.contains(WorldType.PVP_ARENA)
            && !types.contains(WorldType.QUEST_SPEEDRUNNING)
            && !types.contains(WorldType.BETA_WORLD)
            && !types.contains(WorldType.NOSAVE_MODE)
            && !types.contains(WorldType.TOURNAMENT_WORLD);
    }
}
