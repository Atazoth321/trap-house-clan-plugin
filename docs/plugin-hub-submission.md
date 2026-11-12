# Plugin Hub update

Update the existing `plugins/trap-house-clan-plugin` manifest in `runelite/plugin-hub`. Keep the existing listing and point it at the exact tested, public source commit. RuneLite controls review and distribution.

## Changes in this release

- When the maximum quest-point variable is unavailable, the all-quests milestone checks the live game quest table and regular quest completion states. Miniquests do not block completion. Missing cache data and quests absent from the installed API remain unknown rather than granting an award.

The companion backend change completes the all-diaries milestone from the member's 48 recorded diary tiers, with the existing once-only reward handling. Deploy that change separately to Render; the quest fallback requires this client update. No new dependencies, network destinations, submitted data or gameplay interactions are introduced.

## Data and behavior

Wise Old Man event queries use `api.wiseoldman.net`. Optional ranking sync uses a configurable HTTPS origin, defaulting to `trap-house-ranking-api.onrender.com`. User-initiated Discord sign-in opens the configured service's browser flow and enables sync after authentication. Credentials are stored in RuneLite configuration. README documents submitted observations, shared clan data and retained server history.

Score standings and synced collection pages/category boards can be read publicly, including by nonmembers. The browser does not require uploads or credentials. Passive observation sync requires opt-in and the linked clan character; it ignores a POH host's collection book. Collection observations are submitted to the backend, which controls once-only jar and pet awards. Unseen quantities and unsupported counters remain unknown. Backend changes and database migrations are deployed separately.

Networking uses the injected OkHttp client and background executor. The plugin reads supported game events/state and displays information; it does not automate input, interact with gameplay, or change actual in-game clan ranks. Promotions remain manual. Visual celebrations do not create point awards.

## Verification and submission

1. Run `gradlew.bat build verifyPluginHub` against a clean checkout of the release commit. The standard Plugin Hub artifact must contain Java 11-compatible client code and bundled resources, without server or JDBC classes.
2. Push that exact commit to the public plugin repository.
3. Run `powershell -File tools/prepare-plugin-hub-submission.ps1` from the clean checkout. It generates the manifest and reviewer description under `build/plugin-hub-submission` without publishing.
4. Update only `plugins/trap-house-clan-plugin` on a separate branch in the owner's Plugin Hub fork. Open or update one pull request to `runelite/plugin-hub` and address its CI/reviewer feedback there.
5. Verify the distributed update in normal RuneLite after approval. A successful local check or source push does not publish the plugin.

Local build, unit and packaging checks do not replace official Hub CI/review. PostgreSQL integration tests require a separately configured disposable database. Server deployments and live gameplay verification are separate from client packaging checks.

Official instructions: https://github.com/runelite/plugin-hub#submitting-a-plugin
