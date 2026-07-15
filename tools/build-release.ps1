[CmdletBinding()]
param(
    [switch]$SkipTests,
    [switch]$AllowPlaceholderEndpoint
)

$ErrorActionPreference = 'Stop'
$repoRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$distDir = [IO.Path]::GetFullPath((Join-Path $repoRoot 'dist'))
$localEndpointConfig = Join-Path $repoRoot 'config\endpoints.local.properties'

function Resolve-SyncBaseUrl {
    if (-not [string]::IsNullOrWhiteSpace($env:POTATO_SYNC_BASE_URL)) {
        return $env:POTATO_SYNC_BASE_URL.Trim().TrimEnd('/')
    }

    if (Test-Path -LiteralPath $localEndpointConfig -PathType Leaf) {
        foreach ($line in Get-Content -LiteralPath $localEndpointConfig -Encoding UTF8) {
            if ($line -match '^\s*syncBaseUrl\s*=\s*(?<value>.+?)\s*$') {
                return $Matches.value.Trim().TrimEnd('/')
            }
        }
    }

    return 'https://example.invalid'
}

$syncBaseUrl = Resolve-SyncBaseUrl
$syncUri = $null
if (-not [Uri]::TryCreate($syncBaseUrl, [UriKind]::Absolute, [ref]$syncUri) -or
    [string]::IsNullOrWhiteSpace($syncUri.Host) -or
    ($syncUri.Scheme -ne 'http' -and $syncUri.Scheme -ne 'https')) {
    throw 'POTATO_SYNC_BASE_URL or config/endpoints.local.properties must contain an absolute HTTP(S) syncBaseUrl.'
}

$usesPlaceholderEndpoint = $syncUri.Host.Equals('example.invalid', [StringComparison]::OrdinalIgnoreCase)
if ($usesPlaceholderEndpoint -and -not $AllowPlaceholderEndpoint) {
    throw @'
No private sync endpoint is configured. Release packaging has stopped to avoid producing an unusable placeholder build.

Choose one option before packaging:
  1. Copy config/endpoints.example.properties to config/endpoints.local.properties and replace example.invalid.
  2. Set the POTATO_SYNC_BASE_URL environment variable for the current shell.

The local properties file is ignored by Git. -AllowPlaceholderEndpoint is reserved for non-distributable CI verification.
'@
}

if ($usesPlaceholderEndpoint) {
    Write-Host 'Endpoint profile: placeholder (explicitly allowed for verification)' -ForegroundColor Yellow
} else {
    Write-Host 'Endpoint profile: private/local configuration' -ForegroundColor Green
}

if (-not $distDir.StartsWith($repoRoot + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase)) {
    throw "Refusing to use a distribution directory outside the repository: $distDir"
}

Push-Location $repoRoot
$previousAllowPlaceholder = $env:POTATO_ALLOW_PLACEHOLDER_ENDPOINT
try {
    if ($AllowPlaceholderEndpoint) {
        $env:POTATO_ALLOW_PLACEHOLDER_ENDPOINT = '1'
    } else {
        Remove-Item Env:POTATO_ALLOW_PLACEHOLDER_ENDPOINT -ErrorAction SilentlyContinue
    }

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
    if ($null -eq $previousAllowPlaceholder) {
        Remove-Item Env:POTATO_ALLOW_PLACEHOLDER_ENDPOINT -ErrorAction SilentlyContinue
    } else {
        $env:POTATO_ALLOW_PLACEHOLDER_ENDPOINT = $previousAllowPlaceholder
    }
    Pop-Location
}
