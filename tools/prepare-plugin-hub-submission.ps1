$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
Push-Location $projectRoot
try {
    $changes = git status --porcelain --untracked-files=normal
    if ($LASTEXITCODE -ne 0) { throw 'Cannot read Git status.' }
    if ($changes) { throw 'Commit project changes before preparing an exact-commit submission.' }
    $pluginCommit = git rev-parse HEAD
    if ($LASTEXITCODE -ne 0 -or $pluginCommit -notmatch '^[0-9a-f]{40}$') { throw 'Cannot read plugin commit.' }
    $outputDirectory = Join-Path $projectRoot 'build/plugin-hub-submission'
    New-Item -ItemType Directory -Path $outputDirectory -Force | Out-Null
    $utf8 = New-Object System.Text.UTF8Encoding($false)
    [System.IO.File]::WriteAllText((Join-Path $outputDirectory 'trap-house-clan-plugin'), "repository=https://github.com/Atazoth321/trap-house-clan-plugin.git`ncommit=$pluginCommit`n", $utf8)
    $review = @'
Update Trap House Clan Plugin to 1.1.0

Adds a full clan browser with ten statistics and separate Mains, Ironman/GIM/UIM and Hardcore standings, collection pages, Luck, Deserved and Dryboard. Includes search, Find me, Refresh, matching clan styling and item sprites. Adds a Discord code fallback for approved active member links, Profile collection-log milestones and expanded passive milestone/container-loot observations.

Data and behavior:
- Public score and synced collection browsing uses the configurable ranking backend without a credential. Uploads and member account actions remain authenticated.
- Optional sync submits character/activity/progress observations plus account type, total XP, collection/CA/clue/boss counters and collection-page item IDs and observed quantities. Members supply historical collection observations by browsing their own collection book; a POH host's book is ignored. Unknown data remains unknown.
- The plugin does not automate gameplay, open interfaces, send input or change in-game clan ranks. Collection capture reads cache definitions and script events; it does not run scripts. Existing explicitly configured quest-state probes use RuneLite's quest API.
- Backend points, rules, migrations and PostgreSQL libraries are deployed separately and excluded from the plugin JAR. All point awards remain server-calculated.
- Public event queries use api.wiseoldman.net. Ranking requests use the configured HTTPS backend, default trap-house-ranking-api.onrender.com. No new network origin is introduced. Sync is disabled by default; successful user-initiated Discord sign-in enables it and stores the member credential in RuneLite configuration. The fallback /verify flow uses a single-use expiring code and a device-bound credential exchange.

Validation: Gradle build, serverDist and local verifyPluginHub packaging checks passed. Client/server tests and disposable PostgreSQL integration checks cover the new behavior. Standard build, Java 11 client output, BSD 2-Clause license; local checks do not replace official Hub CI/review. Live gameplay coverage still needs confirmation for new sources.

README and docs/plugin-hub-submission.md document the public data, optional uploads, credentials and separate backend deployment.
'@
    [System.IO.File]::WriteAllText((Join-Path $outputDirectory 'pull-request.md'), $review, $utf8)
    Write-Output "Prepared $outputDirectory for commit $pluginCommit. Push that commit before submitting; no publication was performed."
} finally { Pop-Location }
