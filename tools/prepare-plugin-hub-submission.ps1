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
Update Trap House Clan Plugin with Owner award tools

Adds Owner profile buttons for manual awards and verified historical clan membership. Each opens a separate popup with a searchable member dropdown. Direct awards apply through the existing audited adjustment API without a staff approval request; verified dates earn server-calculated points. Member search supports older dashboard roster responses and refreshes when updated roster data arrives.

Data and behavior:
- Owner tools submit selected member UUIDs, point amounts/reasons or verified start/end dates and verification notes to the existing configured ranking backend. Credentials and Owner permissions are enforced server-side. No new network origin is introduced.
- Direct point adjustments and verified date awards are retry-safe. The backend rejects overlapping historical membership ranges and keeps a permanent audit. The form disables historical awards when the server does not supply the required tracking dates.
- The separate backend automatically awards all members 25 points per full seven-day membership period after its migration baseline. Historical ranges are an explicit Owner exception to no automatic backpay. Backend code, migrations and JDBC dependencies are excluded from the plugin JAR.
- Ordinary members retain the manual reward approval workflow. The plugin does not automate gameplay, open game interfaces, send input or change in-game clan ranks.

Validation: Gradle build, serverDist, local verifyPluginHub packaging checks and disposable PostgreSQL integration tests passed. Includes tests for searchable roster fallback/refresh, retry-safe direct awards, weekly membership boundaries, Owner self-awards, inactive recipients and historical range overlap. Standard build, Java 11 client output, BSD 2-Clause license. Local checks do not replace official Hub CI/review.

docs/weekly-membership-rewards.md documents the feature and separate backend deployment requirements.
'@
    [System.IO.File]::WriteAllText((Join-Path $outputDirectory 'pull-request.md'), $review, $utf8)
    Write-Output "Prepared $outputDirectory for commit $pluginCommit. Push that commit before submitting; no publication was performed."
} finally { Pop-Location }
