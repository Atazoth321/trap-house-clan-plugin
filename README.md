# TRAP HOUSE RuneLite plugin (starter project)

A custom sidebar for the TRAP HOUSE Old School RuneScape clan.

## Current features
- Red/black clan dashboard, slogan, configurable event and prize values
- Configurable boss name and **local nearby boss-death counter** (not a verified personal kill count; may include other players' kills)
- Counter saved per RuneLite RS profile, reset confirmation
- Optional Discord and website buttons (disabled until HTTPS links are configured)

## Build
1. Install JDK 11 and a recent Gradle release (or open project in IntelliJ with Gradle support).
2. Run `gradle build` in this folder.
3. For local RuneLite development, use the official RuneLite plugin development workflow / Plugin Hub template to launch a development client with this plugin. Building this project alone does not install it into the official client.
4. Configure links, event and prizes in RuneLite settings.

## Notes
This is an initial source project, not a Plugin Hub submission. Clan-wide events, leaderboards, bingo and verified kill credit require additional implementation and potentially a backend. The boss death signal may vary by NPC and client API version. Test with the current RuneLite API before relying on it for payouts. No credentials or automation are included.

## Public distribution / Plugin Hub submission
This plugin does not require clan membership, login credentials, whitelists, or private servers. Anyone can use the local dashboard after installing it.

**Not yet published:** Public installation through the RuneLite Plugin Hub requires a public source repository, a working build, review, and acceptance by RuneLite maintainers. This ZIP is source code and cannot be installed through the Plugin Hub until approved.

1. Publish the source under a public GitHub repository (for example `trap-house-runelite`). Add an appropriate open-source license and commit the logo assets.
2. Verify that the code compiles and runs against the current RuneLite API; test the sidebar, configuration, and boss-death counter. The current starter project has not been build-verified.
3. Follow the **current** official instructions at https://github.com/runelite/plugin-hub/blob/master/README.md for the required repository structure, manifest, and submission pull request.
4. After acceptance, any RuneLite user can find and install the plugin from the Plugin Hub without being a TRAP HOUSE member.

**Privacy:** The local death counter is not a verified personal kill count and is not shared with the clan. Links to Discord and the website are optional and disabled until configured.
