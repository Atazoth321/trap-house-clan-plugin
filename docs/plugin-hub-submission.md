# Plugin Hub submission

This submits the existing TRAP HOUSE plugin, not a replacement project. Status: prepared locally; not yet submitted, approved, or listed. RuneLite controls review and publication.

## Local checks

Run `gradlew.bat build verifyPluginHub` (or `./gradlew` on Unix). The local check verifies metadata, entry point, license presence, icon dimensions, Java 11 class compatibility, bundled resources, and absence of backend/JDBC classes in the plugin JAR. It does not replace official Hub CI or API compatibility checks.

The plugin uses `build=standard`, Java 11 output, and only RuneLite-provided runtime dependencies. The standard Hub builder replaces this repository's Gradle files and builds `src/main`; PostgreSQL and the server source set are separate. The BSD 2-Clause license and 32x32 root icon already exist. No gameplay behavior or existing feature is removed for submission.

## Reviewer description

TRAP HOUSE clan sidebar with Wise Old Man boss/skill event standings, member profiles, clan points, configurable progression, milestone tracking, and drop/pet rewards. Existing in-game clan ranks are never automatically changed. No input automation or gameplay interaction is performed.

External services: `https://api.wiseoldman.net/v2/` for public competitions; default ranking origin `https://trap-house-ranking-api.onrender.com`, configurable to another HTTPS origin. Ranking submissions are disabled until opted in. Discord device sign-in is initiated by a button, opens the configured server's browser flow, and enables ranking sync after authentication. The server binds Discord identities to Owner-approved characters; client credentials are saved in RuneLite configuration. The README describes transmitted observations and retained server history. Browser links also include clan Discord, clan website, and WOM competition pages.

Networking uses the injected OkHttp client and background executor. The plugin reads supported events/state and displays information. Clan points are internal recognition, independent of actual OSRS clan permissions. The backend is independently deployed and is not included in the plugin JAR. Server-side permission enforcement and PostgreSQL tests are maintained in this repository but are not part of the standard Hub artifact.

## Submission procedure

1. Commit the tested plugin and push it to the existing public repository. Confirm the exact commit is available on GitHub. A push can trigger the existing Render auto-deploy; confirm the production database backup first.
2. Run `powershell -File tools/prepare-plugin-hub-submission.ps1`. This writes an exact-commit manifest and reviewer text under `build/plugin-hub-submission`; it does not publish anything.
3. Fork `https://github.com/runelite/plugin-hub`, create a branch, and add the generated `trap-house-clan` file under `plugins/trap-house-clan`. Do not copy the entire plugin project into the Hub repository.
4. Open a pull request to `runelite/plugin-hub` with the generated description. Address CI/reviewer requests in that same pull request and update its manifest commit after any plugin changes.
5. Wait for RuneLite review and listing before telling members it is available. Verify installation in normal RuneLite and the live Discord/point flow after listing. Later releases require another manifest commit update in Plugin Hub.

Official instructions: https://github.com/runelite/plugin-hub#submitting-a-plugin

The owner must control the public source repository and authorize publication. An authenticated GitHub session/CLI is needed to create the fork and pull request. No GitHub credentials should be pasted into project files or chat. Backend deployments, live backups, real game observations, and RuneLite reviewer approval are separate from a passing local build.
