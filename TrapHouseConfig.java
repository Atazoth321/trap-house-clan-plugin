package com.traphouse;

import net.runelite.client.config.*;

@ConfigGroup("traphouse")
public interface TrapHouseConfig extends Config {
    @ConfigItem(keyName="eventName", name="Active event", description="Clan event name", position=1)
    default String eventName() { return "Boss of the Week"; }

    @ConfigItem(keyName="trackedBoss", name="Tracked boss", description="Exact boss NPC name", position=2)
    default String trackedBoss() { return "Sarachnis"; }

    @ConfigItem(keyName="firstPrize", name="1st prize (GP)", description="First-place prize", position=3)
    default int firstPrize() { return 10000000; }

    @ConfigItem(keyName="secondPrize", name="2nd prize (GP)", description="Second-place prize", position=4)
    default int secondPrize() { return 5000000; }

    @ConfigItem(keyName="thirdPrize", name="3rd prize (GP)", description="Third-place prize", position=5)
    default int thirdPrize() { return 2500000; }

    @ConfigItem(keyName="discordUrl", name="Discord invite URL", description="Your clan Discord invite", position=6)
    default String discordUrl() { return ""; }

    @ConfigItem(keyName="websiteUrl", name="Clan website URL", description="Your clan website", position=7)
    default String websiteUrl() { return ""; }
}
