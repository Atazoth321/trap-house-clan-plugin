package com.traphouse;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class TrapHousePluginLauncher {
    public static void main(String[] args) throws Exception {
        ExternalPluginManager.loadBuiltin(TrapHouseClanPlugin.class);
        RuneLite.main(args);
    }
}
