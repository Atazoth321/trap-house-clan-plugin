# Plugin Hub submission

This updates the existing TRAP HOUSE listing at `plugins/trap-house-clan-plugin`, not a replacement project or second listing. The expanded update was merged in [PR #19437](https://github.com/runelite/plugin-hub/pull/19437), with the manifest pointing to commit `bd17a093c687f3b37d0d6276fc8f45a3f097aaed`. RuneLite controls review and distribution of subsequent updates.

The next client update recognizes RuneLite's mapped Fire cape and Dragon defender variants in inventory, equipment and loaded bank containers. Previously these milestones compared only the regular item IDs, so ornamented, broken or locked versions could leave progress at zero. Other item milestones retain their existing matching behavior. Regression tests cover every currently mapped variant across all three containers and reject unrelated items. The source also contains server-side link-management changes, which are excluded from the standard Plugin Hub artifact.

## Local checks

The pending client also adds personal-loot tracking for Gauntlet seeds, Perilous Moons equipment, Tormented demon uniques and Araxxor uniques/halberd components. First tracked clue completions have a separate Clues points area, one reward per difficulty and matching reward-casket sprites. New drop cards use the actual game item sprites. The separately deployed backend selects points, applies shared item caps and prevents repeated clue rewards. See [encounter and clue rewards](encounter-and-clue-rewards.md) for the catalog, values and rollout requirements. This accompanies the pending leaderboard layout change that places rank emblems beside player names.

Further pending additions cover Royal Titans prayer scrolls, Hueycoatl hide/wand, Nightmare Inquisitor equipment/orbs, and personal-casket ranger boots, 3rd age and gilded uniques. All clue drops appear in Clues, with their game item sprites. Once-only 10/50/100 clue milestones use game completion counters and difficulty-specific casket sprites. The client declares its dependency on RuneLite's built-in Loot Tracker for personal loot callbacks. The backend seeds these rewards through a separate additive migration; these client observations require that backend rollout before release.

Run `gradlew.bat build verifyPluginHub` (or `./gradlew` on Unix). The local check verifies metadata, entry point, license presence, icon dimensions, Java 11 class compatibility, bundled resources, and absence of backend/JDBC classes in the plugin JAR. It does not replace official Hub CI or API compatibility checks.

The plugin uses `build=standard`, Java 11 output, and only RuneLite-provided runtime dependencies. The standard Hub builder replaces this repository's Gradle files and builds `src/main`; PostgreSQL and the server source set are separate. The BSD 2-Clause license and 32x32 root icon already exist. No gameplay behavior or existing feature is removed for submission.


Existing pet recognition also reads the 55 supported pet families from inventory and a loaded bank, including known transformed forms, and uses the existing once-only collection keys and in-game pet sprites. See [existing pet tracking](existing-pet-tracking.md).

## Reviewer description

TRAP HOUSE clan sidebar with Wise Old Man boss/skill event standings, member profiles, clan points, configurable progression, milestone tracking, and drop/pet rewards. Existing in-game clan ranks are never automatically changed. No input automation or gameplay interaction is performed.

External services: `https://api.wiseoldman.net/v2/` for public competitions; default ranking origin `https://trap-house-ranking-api.onrender.com`, configurable to another HTTPS origin. Ranking submissions are disabled until opted in. Discord device sign-in is initiated by a button, opens the configured server's browser flow, and enables ranking sync after authentication. The server binds Discord identities to Owner-approved characters; client credentials are saved in RuneLite configuration. The README describes transmitted observations and retained server history. Browser links also include clan Discord, clan website, and WOM competition pages.

Networking uses the injected OkHttp client and background executor. The plugin reads supported events/state and displays information. Clan points are internal recognition, independent of actual OSRS clan permissions. The backend is independently deployed and is not included in the plugin JAR. Server-side permission enforcement and PostgreSQL tests are maintained in this repository but are not part of the standard Hub artifact.

## Submission procedure

1. Commit the tested plugin and push it to the existing public repository. Confirm the exact commit is available on GitHub. A push can trigger the existing Render auto-deploy; confirm the production database backup first.
2. Run `powershell -File tools/prepare-plugin-hub-submission.ps1`. This writes an exact-commit manifest and reviewer text under `build/plugin-hub-submission`; it does not publish anything.
3. In your fork of `https://github.com/runelite/plugin-hub`, create a branch and update the existing `plugins/trap-house-clan-plugin` file with the generated `trap-house-clan-plugin` manifest. Do not create a second listing or copy the entire plugin project into the Hub repository.
4. Open a pull request to `runelite/plugin-hub` with the generated description. Address CI/reviewer requests in that same pull request and update its manifest commit after any plugin changes.
5. Wait for RuneLite review and distribution before telling members the update is available. Verify the updated plugin in normal RuneLite and the live Discord/point flow after release. Later releases update this same manifest.

Official instructions: https://github.com/runelite/plugin-hub#submitting-a-plugin

The owner must control the public source repository and authorize publication. An authenticated GitHub session/CLI is needed to create the fork and pull request. No GitHub credentials should be pasted into project files or chat. Backend deployments, live backups, real game observations, and RuneLite reviewer approval are separate from a passing local build.
