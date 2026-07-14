[CmdletBinding()]
param(
    [switch]$SkipTests
)

$ErrorActionPreference = 'Stop'
$repoRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$distDir = [IO.Path]::GetFullPath((Join-Path $repoRoot 'dist'))

if (-not $distDir.StartsWith($repoRoot + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase)) {
    throw "Refusing to use a distribution directory outside the repository: $distDir"
}

Push-Location $repoRoot
try {
    $javaVersionText = (& cmd.exe /d /c "java -version 2>&1" | Out-String)
    if ($LASTEXITCODE -ne 0 -or $javaVersionText -notmatch 'version\s+"(?<major>\d+)') {
        throw 'Unable to determine the active Java version. Install and select JDK 17.'
    }
    if ([int]$Matches.major -lt 17) {
        throw "JDK 17 or newer is required. Active runtime:`n$javaVersionText"
    }

    $gradleTasks = @('clean')
    if (-not $SkipTests) {
        $gradleTasks += 'test'
    }
    $gradleTasks += @(':seed-agent:jar', ':updater:jar', '--no-daemon', '--console=plain', '--warning-mode=all')
    & .\gradlew.bat @gradleTasks
    if ($LASTEXITCODE -ne 0) {
        throw "Gradle build failed with exit code $LASTEXITCODE"
    }

    & cmd.exe /d /c seed-installer\build_installer.bat
    if ($LASTEXITCODE -ne 0) {
        throw "Installer build failed with exit code $LASTEXITCODE"
    }

    if (Test-Path -LiteralPath $distDir) {
        $resolvedDist = [IO.Path]::GetFullPath((Resolve-Path -LiteralPath $distDir).Path)
        if (-not $resolvedDist.StartsWith($repoRoot + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase)) {
            throw "Refusing to delete an unexpected distribution directory: $resolvedDist"
        }
        Remove-Item -LiteralPath $resolvedDist -Recurse -Force
    }
    New-Item -ItemType Directory -Path $distDir | Out-Null

    $products = @(
        @{ Source = 'seed-agent\build\libs\Potato_Seed.jar'; Name = 'Potato_Seed.jar' },
        @{ Source = 'updater\build\libs\Potato_Updater.jar'; Name = 'Potato_Updater.jar' },
        @{ Source = 'seed-installer\build\Potato_Seed_Installer.exe'; Name = 'Potato_Seed_Installer.exe' }
    )

    foreach ($product in $products) {
        $sourcePath = Join-Path $repoRoot $product.Source
        if (-not (Test-Path -LiteralPath $sourcePath -PathType Leaf)) {
            throw "Expected build product is missing: $sourcePath"
        }
        Copy-Item -LiteralPath $sourcePath -Destination (Join-Path $distDir $product.Name)
    }

    $hashLines = Get-ChildItem -LiteralPath $distDir -File |
        Sort-Object Name |
        ForEach-Object {
            $hash = (Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash.ToLowerInvariant()
            "$hash  $($_.Name)"
        }
    Set-Content -LiteralPath (Join-Path $distDir 'SHA256SUMS.txt') -Value $hashLines -Encoding UTF8

    Write-Host "Release products staged at $distDir" -ForegroundColor Green
    $hashLines | ForEach-Object { Write-Host $_ }
}
finally {
    Pop-Location
}
