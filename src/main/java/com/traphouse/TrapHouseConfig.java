package com.traphouse;

import net.runelite.client.config.*;

@ConfigGroup("traphouse")
public interface TrapHouseConfig extends Config {
    @ConfigItem(keyName="competitionId", name="Wise Old Man competition ID", description="0 automatically selects the current Trap House boss competition. Enter a competition ID to view a specific event, including ended events.", position=1)
    @Range(min=0)
    default int competitionId() { return 0; }

    @ConfigItem(keyName="skillCompetitionId", name="Skill of the Month competition ID", description="0 detects the current Trap House single-skill competition. Enter an ID to select a specific skill event.", position=2)
    @Range(min=0)
    default int skillCompetitionId() { return 0; }

    @ConfigItem(keyName="skillFirstPrize", name="Skill 1st prize (GP)", description="Skill of the Month first-place prize", position=8)
    default int skillFirstPrize() { return 10000000; }

    @ConfigItem(keyName="skillSecondPrize", name="Skill 2nd prize (GP)", description="Skill of the Month second-place prize", position=9)
    default int skillSecondPrize() { return 5000000; }

    @ConfigItem(keyName="skillThirdPrize", name="Skill 3rd prize (GP)", description="Skill of the Month third-place prize", position=10)
    default int skillThirdPrize() { return 2500000; }

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
