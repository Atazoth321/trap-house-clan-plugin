$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
$outputDirectory = Join-Path $projectRoot 'build/share'
New-Item -ItemType Directory -Force -Path $outputDirectory | Out-Null
$assets = @{}
$assets['logo'] = 'data:image/png;base64,' + [Convert]::ToBase64String([IO.File]::ReadAllBytes((Join-Path $projectRoot 'src/main/resources/com/traphouse/logo.png')))
foreach ($assetGroup in @(@{Name='ranks';Path='src/main/resources/com/traphouse/ranks'}, @{Name='items';Path='src/test/resources/items'})) {
    foreach ($assetFile in Get-ChildItem -LiteralPath (Join-Path $projectRoot $assetGroup.Path) -Filter '*.png') {
        $assets[$assetGroup.Name + '/' + $assetFile.BaseName] = 'data:image/png;base64,' + [Convert]::ToBase64String([IO.File]::ReadAllBytes($assetFile.FullName))
    }
}
$fixture = [IO.File]::ReadAllText((Join-Path $projectRoot 'src/test/resources/clan-preview.json'))
# Escape less-than signs to prevent fixture strings from closing their JSON script element.
$fixture = $fixture.Replace('<', '\u003c')
$assetJson = ConvertTo-Json -InputObject $assets -Compress
$template = [IO.File]::ReadAllText((Join-Path $PSScriptRoot 'share-preview.html'))
$html = $template.Replace('__FIXTURE__', $fixture).Replace('__ASSETS__', $assetJson)
$outputPath = Join-Path $outputDirectory 'TRAP-HOUSE-Preview.html'
[IO.File]::WriteAllText($outputPath, $html, [Text.UTF8Encoding]::new($false))
$readme = "TRAP HOUSE - Interactive Sample Preview`r`n`r`nOpen TRAP-HOUSE-Preview.html in Chrome, Edge, Firefox, or Safari.`r`nNo Java, RuneLite, account, internet, or installation required.`r`nClick point categories for item awards. Click leaderboard members for profiles.`r`nThis is a browser recreation using sample data, not the installable RuneLite plugin.`r`nNo live accounts or clan data are included.`r`n"
$readmePath = Join-Path $outputDirectory 'READ-ME.txt'
[IO.File]::WriteAllText($readmePath, $readme, [Text.UTF8Encoding]::new($false))
Compress-Archive -LiteralPath $outputPath,$readmePath -DestinationPath (Join-Path $outputDirectory 'TRAP-HOUSE-Preview.zip') -Force
Get-Item -LiteralPath $outputPath,(Join-Path $outputDirectory 'TRAP-HOUSE-Preview.zip') | Select-Object FullName,Length
