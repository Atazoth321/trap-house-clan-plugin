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
Update Trap House Clan Plugin

Updates the existing plugins/trap-house-clan-plugin listing with confirmed drop/pet celebration overlays, earned rank milestone banners with optional sound, a server-backed clan Activity page, and faster background sync after loot/achievement observations. Also includes a Clues reward browser, additional encounter/clue observations, recognition of supported existing pets in inventory/loaded bank, item sprites and leaderboard rank emblems.

Celebrations display server-confirmed points; they do not award points locally. Initial connection is silent, queues are bounded and cleared on session/credential changes, and repeated notification pages cannot replay an award. Loot bursts are coalesced over 150 ms with a 30-second fallback sync. In-game promotions remain manual. Sound is an optional system beep and defaults to off. No new network origin is introduced.

Uses the standard build and RuneLite-provided runtime libraries, with Java 11 output and a BSD 2-Clause license. The separately deployed backend and PostgreSQL dependencies are excluded from the plugin JAR.

Public event queries use api.wiseoldman.net. Optional ranking sync sends character/activity/progress observations to a configurable HTTPS backend (default trap-house-ranking-api.onrender.com). Sync is disabled by default; user-initiated Discord device sign-in enables it after authentication. README and docs/plugin-hub-submission.md describe the network/data flow and credential handling.

The plugin does not automate gameplay or change actual OSRS clan ranks. Please review through the normal Hub CI and review process; local checks do not establish approval.
'@
    [System.IO.File]::WriteAllText((Join-Path $outputDirectory 'pull-request.md'), $review, $utf8)
    Write-Output "Prepared $outputDirectory for commit $pluginCommit. Push that commit before submitting; no publication was performed."
} finally { Pop-Location }
