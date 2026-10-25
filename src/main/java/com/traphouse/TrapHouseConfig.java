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

    @ConfigItem(keyName="announceOnLogin", name="Announce events on login", description="Show the boss and skill events in your game chat once per login.", position=3)
    default boolean announceOnLogin() { return true; }

    @ConfigItem(keyName="showEventOverlay", name="Show event overlay", description="Display the boss and skill event targets and time remaining on screen.", position=4)
    default boolean showEventOverlay() { return false; }

}
