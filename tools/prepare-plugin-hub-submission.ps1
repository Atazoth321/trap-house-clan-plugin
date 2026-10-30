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
Trap House Clan Plugin: clue rewards, encounter tracking and leaderboard icons

Updates the existing plugins/trap-house-clan-plugin listing from the currently published df35330 client. Adds a Clues points area with personal-casket unique rewards, first tracked completions and once-only 10/50/100 completion milestones, with real game item/casket sprites and received history. Adds supported encounter observations for Gauntlet, Perilous Moons, Tormented demons, Araxxor, Royal Titans, Hueycoatl and Nightmare/Phosani's Nightmare. Improves Fire cape/defender variant matching and places leaderboard rank emblems beside player names.

Clue uniques are observed only from personal clue-casket loot events, not inventory ownership, purchases or trades. Milestones read exact game-system clue completion counters; player chat is excluded. Declares the built-in Loot Tracker plugin dependency for personal loot callbacks. The separate backend controls reward amounts, caps and once-only awards. Reward state remains per existing member record; no alt-account or Discord-wide points changes are included. Backend catalog changes in the source are excluded from the Plugin Hub artifact and require separate deployment.

Uses the standard build and RuneLite-provided runtime libraries, with Java 11 output and a BSD 2-Clause license. The separately deployed backend and PostgreSQL dependencies are excluded from the plugin JAR.

Validation: local build, client/backend unit tests, verifyPluginHub and serverDist passed. All 71 PostgreSQL integration tests passed against an isolated local database. These checks do not establish live in-game encounter behavior or a production backend rollout.

Public event queries use api.wiseoldman.net. Optional ranking sync sends character/activity/progress observations to a configurable HTTPS backend (default trap-house-ranking-api.onrender.com). Sync is disabled by default; user-initiated Discord device sign-in enables it after authentication. README and docs/plugin-hub-submission.md describe the network/data flow and credential handling.

The plugin does not automate gameplay or change actual OSRS clan ranks. Please review through the normal Hub CI and review process; local checks do not establish approval.
'@
    [System.IO.File]::WriteAllText((Join-Path $outputDirectory 'pull-request.md'), $review, $utf8)
    Write-Output "Prepared $outputDirectory for commit $pluginCommit. Push that commit before submitting; no publication was performed."
} finally { Pop-Location }
