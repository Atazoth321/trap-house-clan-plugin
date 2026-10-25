# Trap House Clan Plugin

A RuneLite sidebar for the **TRAP HOUSE** Old School RuneScape clan. Follow Boss of the Week and Skill of the Month competitions from [Wise Old Man group 2504](https://wiseoldman.net/groups/2504), with event leaderboards and fixed prizes.

## Features

- **Boss event:** Boss of the Week details and the top ten participants ranked by kills gained during the competition.
- **Skill event:** Skill of the Month details and the top ten participants ranked by XP gained during the competition.
- Automatic detection of active clan competitions, with separate competition-ID overrides for each tab.
- Event titles, featured bosses or skills, competition dates, and gold, silver, and bronze ranks.
- Automatic refresh every five minutes and a manual **Refresh leaderboard** button.
- Private boss and skill event announcements once per login, without repeating on world hops.
- Optional on-screen event overlay showing both targets and time remaining.
- Fixed first-, second-, and third-place prizes of 10M, 5M, and 2.5M GP for both events.
- TRAP HOUSE branding, an **Open Clan Website** button linking to https://traphouseosrs.net/, and an **Open Discord** button linking to https://discord.gg/thosrs.

![Boss event and Skill event tabs](docs/plugin-preview.png)

## Build and run

Use **JDK 17 or newer** to run the included Gradle wrapper. The plugin compiles for Java 11. An internet connection is required to download dependencies and read Wise Old Man events.

On Windows, build and run the tests:

```powershell
.\gradlew.bat build
```

Launch a development RuneLite client with the plugin loaded:

```powershell
.\gradlew.bat run
```

On macOS or Linux, use `./gradlew` instead of `.\gradlew.bat`.

In RuneLite, enable **Trap House Clan Plugin** in plugin settings, then click the TRAP HOUSE sidebar icon. Building a JAR does not install the plugin into your regular RuneLite client.

### IntelliJ IDEA

1. Open the project as a Gradle project.
2. Select your installed JDK as the Gradle JVM and reload Gradle.
3. Run the Gradle `run` task, or run `TrapHousePluginLauncher.main()` from `src/test/java/com/traphouse`.

Restart the development client after changing plugin code.

## Settings

| Setting | Purpose |
| --- | --- |
| Wise Old Man competition ID | Boss event override. Leave at `0` to detect an active boss competition automatically. |
| Skill of the Month competition ID | Skill event override. Leave at `0` to detect an active single-skill competition automatically. |
| Announce events on login | Show both events privately in game chat once per login. Enabled by default. |
| Show event overlay | Show boss and skill targets and time remaining on screen. Disabled by default. |

A competition's ID is the number in its Wise Old Man URL, such as `154611` in `https://wiseoldman.net/competitions/154611`. Overrides must belong to **Trap House group 2504** and match the tab's event type. Historical and upcoming competitions are labeled accordingly.

## How tracking works

The plugin reads public competition data from Wise Old Man. Leaderboard scores are **gains recorded during the selected competition**, rather than lifetime kill counts, lifetime XP, or nearby NPC deaths.

Each tab selects an event whose start time has passed and whose end time has not passed. If several matching events overlap, it selects the most recently started one. The skill tab accepts individual skills and excludes overall-XP competitions.

If no matching event is running, the tab displays **No active boss event** or **No active skill event**. It does not substitute a finished event. Participants with missing hiscores baselines are omitted.

Members must update their Wise Old Man profiles for newer gains to appear. This plugin reads the recorded scores; it does not submit player updates or create competitions. Prizes are displayed as fixed amounts; the plugin does not distribute rewards.

XP scores are abbreviated in the sidebar. Hover a leaderboard row to see the exact gain. Event dates are shown in UTC. Manual refreshes are limited to once every 30 seconds. If a refresh fails, the last fetched scores remain visible with a stale-data message.

## Preview the sidebar

Render the layout without starting a game client:

```powershell
.\gradlew.bat preview
```

Preview the current boss event:

```powershell
.\gradlew.bat preview --args=--live
```

Preview the current skill event:

```powershell
.\gradlew.bat preview --args=--skill-live
```

Preview a specific competition:

```powershell
.\gradlew.bat preview --args="--live 154611"
.\gradlew.bat preview --args="--skill-live 150717"
```

These commands write `build/trap-house-preview.png`. To render both tabs side by side with current Wise Old Man data:

```powershell
.\gradlew.bat previewBoth
```

The combined image is saved as `build/trap-house-both-tabs.png`. Live previews require an internet connection. The README screenshot shows the layout at the time it was captured, not current event status.

## Troubleshooting

- **No active event:** Check whether group 2504 has a running competition of the correct type. Use the appropriate competition-ID setting to view a specific event.
- **Scores look outdated:** Update the participant's Wise Old Man profile, then refresh the leaderboard.
- **Wrong competition type or group:** Choose a Trap House boss competition for Boss event, or a Trap House single-skill competition for Skill event.
- **Java cannot be found:** Set `JAVA_HOME` to your JDK installation, or configure the Gradle JVM in IntelliJ.

## Privacy

The plugin makes HTTPS read requests to `api.wiseoldman.net` for public clan competitions and participant scores. It does not send your player name, login details, or local kill counts.

## Development and distribution

Plugin entry point: `com.traphouse.TrapHouseClanPlugin`.

Run `build` to compile and execute the tests. Tests cover event selection, time boundaries, kill and XP gains, group validation, leaderboard states, tab layout, and packaged logo resources.

This repository is a development project. Plugin Hub distribution requires a separate submission and acceptance; consult the [RuneLite Plugin Hub instructions](https://github.com/runelite/plugin-hub/blob/master/README.md) before submitting.
