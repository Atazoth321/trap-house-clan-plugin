# Plugin Hub update

Update the existing `plugins/trap-house-clan-plugin` manifest in `runelite/plugin-hub`. Keep the existing listing and point it at the exact tested, public source commit. RuneLite controls review and distribution.

## Changes in this release

- Full clan browser with ten statistics, separate account-type standings, search, collection pages, Luck, Deserved and Dryboard, styled to match the clan sidebar.
- Public browsing without a member credential; uploads and account actions remain authenticated. Optional sync now includes account type, total XP, collection and combat totals, clue/boss counters, and collection-page item observations. Opening/browsing the player's own collection book supplies historical observations; the plugin never opens it automatically.
- Discord `/verify` code fallback for approved active character links when browser sign-in fails, with bounded single-use sessions and clearer retry behavior.
- Expanded milestone/collection tracking, Profile collection thresholds with progress/completion indicators, and item sprites for new milestone and container rewards. The backend separately controls reward prices and caps, including higher EHP/EHB rewards and Sire exchange-only awards.

- Confirmed drop and pet award celebrations with item sprites, category colors and separate settings. Newly observed earned rank milestones show the existing rank emblem, with an optional system beep that defaults to off. Initial connection establishes a silent baseline; repeated notification pages do not replay awards.
- Loot and achievement observations request background ranking sync after a 150 ms batching window. Observations arriving during a sync request a follow-up; the regular 30-second sync remains a fallback. All displayed points still come from the server.
- An Activity page displays shared confirmed awards and earned rank milestones from the ranking service. Older servers display an availability message.
- Personal-loot observations cover additional boss encounters and clue caskets, with a separate Clues reward browser, first tracked clue completions, and 10/50/100 completion milestones. See [encounter and clue rewards](encounter-and-clue-rewards.md) for backend requirements.
- Existing pets in inventory or a loaded bank can be recognized through an explicit item allowlist. Observations use the existing once-only named pet reward keys. See [existing pet recognition](existing-pet-tracking.md).
- Reward cards resolve game item sprites, and leaderboard rows display clan rank emblems beside member names.

The built-in Loot Tracker's personal-loot events are received through the event bus. The plugin does not declare a `PluginDependency` on Loot Tracker because it exposes no services. Members should enable Loot Tracker for container-loot observations. Backend rules, caps, categories and migrations are independently deployed and never included in the plugin JAR.

## Data and behavior

Wise Old Man event queries use `api.wiseoldman.net`. Optional ranking sync uses a configurable HTTPS origin, defaulting to `trap-house-ranking-api.onrender.com`. User-initiated Discord sign-in opens the configured service's browser flow and enables sync after authentication. Credentials are stored in RuneLite configuration. README documents submitted observations, shared clan data and retained server history.

Score standings and synced collection pages/category boards can be read publicly, including by nonmembers. The browser does not require uploads or credentials. Passive observation sync requires opt-in and the linked clan character; it ignores a POH host's collection book. Collection observations do not award points themselves. Unseen quantities and unsupported counters remain unknown. Backend changes and database migrations are deployed separately.

Networking uses the injected OkHttp client and background executor. The plugin reads supported game events/state and displays information; it does not automate input, interact with gameplay, or change actual in-game clan ranks. Promotions remain manual. Visual celebrations do not create point awards.

## Verification and submission

1. Run `gradlew.bat build verifyPluginHub` against a clean checkout of the release commit. The standard Plugin Hub artifact must contain Java 11-compatible client code and bundled resources, without server or JDBC classes.
2. Push that exact commit to the public plugin repository.
3. Run `powershell -File tools/prepare-plugin-hub-submission.ps1` from the clean checkout. It generates the manifest and reviewer description under `build/plugin-hub-submission` without publishing.
4. Update only `plugins/trap-house-clan-plugin` on a separate branch in the owner's Plugin Hub fork. Open or update one pull request to `runelite/plugin-hub` and address its CI/reviewer feedback there.
5. Verify the distributed update in normal RuneLite after approval. A successful local check or source push does not publish the plugin.

Local build, unit and packaging checks do not replace official Hub CI/review. PostgreSQL integration tests require a separately configured disposable database. Server deployments and live gameplay verification are separate from client packaging checks.

Official instructions: https://github.com/runelite/plugin-hub#submitting-a-plugin
