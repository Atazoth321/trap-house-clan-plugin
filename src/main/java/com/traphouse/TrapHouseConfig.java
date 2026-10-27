package com.traphouse;

import net.runelite.client.config.*;

@ConfigGroup("traphouse")
public interface TrapHouseConfig extends Config {
    @ConfigItem(keyName="rankingEnabled", name="Enable ranking sync", description="Opt in to sending your character name, supported events and configured achievement/progress observations to your clan ranking server.", position=20)
    default boolean rankingEnabled() { return false; }

    @ConfigItem(keyName="rankingUrl", name="Ranking API URL", description="HTTPS base URL of the TRAP HOUSE ranking server. No trailing path or query.", position=21)
    default String rankingUrl() { return "https://trap-house-ranking-api.onrender.com"; }

    @ConfigItem(keyName="rankingToken", name="Ranking member token", description="Member-specific credential issued by clan staff. Stored in local RuneLite configuration; never share it.", secret=true, position=22)
    default String rankingToken() { return ""; }
    @ConfigItem(keyName="rankingPointMessages", name="Points in chat", description="Show confirmed clan point awards and what earned them in your local game chat.", position=23)
    default boolean rankingPointMessages() { return true; }

    @ConfigItem(keyName="rankingPointMessageColors", name="Color point messages", description="Use category colors for confirmed point awards in your local game chat.", position=24)
    default boolean rankingPointMessageColors() { return true; }

    @ConfigItem(keyName="rankingDropMessageColor", name="Drop message color", description="Color for points earned from drops.", position=25)
    default java.awt.Color rankingDropMessageColor() { return new java.awt.Color(255,192,72); }

    @ConfigItem(keyName="rankingPetMessageColor", name="Pet message color", description="Color for points earned from pets.", position=26)
    default java.awt.Color rankingPetMessageColor() { return new java.awt.Color(192,128,255); }

    @ConfigItem(keyName="rankingMilestoneMessageColor", name="Milestone message color", description="Color for points earned from milestones.", position=27)
    default java.awt.Color rankingMilestoneMessageColor() { return new java.awt.Color(100,210,130); }

    @ConfigItem(keyName="rankingOtherMessageColor", name="Other point message color", description="Color for combat achievements, diaries, events, efficiency and other point awards.", position=28)
    default java.awt.Color rankingOtherMessageColor() { return new java.awt.Color(120,200,255); }
    @ConfigItem(keyName="competitionId", name="Wise Old Man competition ID", description="0 automatically selects the current Trap House boss competition. Enter a competition ID to view a specific event, including ended events.", position=1)
    @Range(min=0)
    default int competitionId() { return 0; }

    @ConfigItem(keyName="skillCompetitionId", name="Skill of the Month competition ID", description="0 detects the current Trap House single-skill competition. Enter an ID to select a specific skill event.", position=2)
    @Range(min=0)
    default int skillCompetitionId() { return 0; }

    @ConfigItem(keyName="announceOnLogin", name="Announce events on login", description="Show the boss and skill events in your game chat once per login.", position=3)
    default boolean announceOnLogin() { return true; }

    @ConfigItem(keyName="showEventOverlay", name="Show event overlay", description="Display the boss and skill event targets and time remaining on screen.", position=4)
    default boolean showEventOverlay() { return true; }

    @ConfigItem(keyName="showBossOverlay", name="Boss event in overlay", description="Include the boss event when the event overlay is enabled.", position=5)
    default boolean showBossOverlay() { return true; }

    @ConfigItem(keyName="showSkillOverlay", name="Skill event in overlay", description="Include the skill event when the event overlay is enabled.", position=6)
    default boolean showSkillOverlay() { return true; }

    @ConfigItem(keyName="showOverlayLeaders", name="Top three in overlay", description="Include the top three participants for each visible event.", position=7)
    default boolean showOverlayLeaders() { return false; }

    @ConfigItem(keyName="eventEndingReminders", name="Event ending reminders", description="Privately remind you when an active event has a day or an hour remaining.", position=8)
    default boolean eventEndingReminders() { return true; }

    @ConfigItem(keyName="newEventAlerts", name="New event alerts", description="Privately announce new active events detected while you are playing.", position=9)
    default boolean newEventAlerts() { return true; }

}
