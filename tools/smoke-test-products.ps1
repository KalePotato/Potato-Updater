[CmdletBinding()]
param(
    [switch]$SkipBuild,
    [switch]$AllowPlaceholderEndpoint
)

$ErrorActionPreference = 'Stop'
$repoRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$distDir = Join-Path $repoRoot 'dist'
$smokeRoot = [IO.Path]::GetFullPath((Join-Path $repoRoot 'build\product-smoke-test'))

if (-not $smokeRoot.StartsWith($repoRoot + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase)) {
    throw "Refusing to use a smoke-test directory outside the repository: $smokeRoot"
}

Push-Location $repoRoot
try {
    if (-not $SkipBuild) {
        $buildArguments = @('-SkipTests')
        if ($AllowPlaceholderEndpoint) {
            $buildArguments += '-AllowPlaceholderEndpoint'
        }
        & (Join-Path $PSScriptRoot 'build-release.ps1') @buildArguments
        if ($LASTEXITCODE -ne 0) {
            throw "Release build failed with exit code $LASTEXITCODE"
        }
    }

    $seedJar = Join-Path $distDir 'Potato_Seed.jar'
    $updaterJar = Join-Path $distDir 'Potato_Updater.jar'
    $installerExe = Join-Path $distDir 'Potato_Seed_Installer.exe'
    foreach ($product in @($seedJar, $updaterJar, $installerExe)) {
        if (-not (Test-Path -LiteralPath $product -PathType Leaf)) {
            throw "Missing release product: $product"
        }
    }

    Add-Type -AssemblyName System.IO.Compression.FileSystem
    function Read-ZipTextEntry([string]$archivePath, [string]$entryName) {
        $archive = [IO.Compression.ZipFile]::OpenRead($archivePath)
        try {
            $entry = $archive.GetEntry($entryName)
            if ($null -eq $entry) {
                throw "Missing $entryName in $archivePath"
            }
            $reader = New-Object IO.StreamReader($entry.Open(), [Text.Encoding]::UTF8)
            try {
                return $reader.ReadToEnd()
            }
            finally {
                $reader.Dispose()
            }
        }
        finally {
            $archive.Dispose()
        }
    }

    $seedManifest = Read-ZipTextEntry $seedJar 'META-INF/MANIFEST.MF'
    $updaterManifest = Read-ZipTextEntry $updaterJar 'META-INF/MANIFEST.MF'
    if ($seedManifest -notmatch '(?m)^Premain-Class: com\.potato\.seed\.agent\.PotatoSeedAgent\s*$') {
        throw 'Potato_Seed.jar has an invalid or missing Premain-Class.'
    }
    if ($updaterManifest -notmatch '(?m)^Main-Class: com\.potato\.updater\.PotatoUpdater\s*$') {
        throw 'Potato_Updater.jar has an invalid or missing Main-Class.'
    }

    $seedEndpointResource = Read-ZipTextEntry $seedJar 'potato-endpoints.properties'
    $updaterEndpointResource = Read-ZipTextEntry $updaterJar 'potato-endpoints.properties'
    if ($seedEndpointResource.Trim() -ne $updaterEndpointResource.Trim()) {
        throw 'Seed and Updater were built with different endpoint profiles.'
    }
    $usesPlaceholder = $seedEndpointResource -match 'https://example\.invalid(?:/|\s|$)'
    if ($usesPlaceholder -and -not $AllowPlaceholderEndpoint) {
        throw 'Release products contain the placeholder endpoint.'
    }
    if (-not $usesPlaceholder -and $AllowPlaceholderEndpoint) {
        Write-Host 'Smoke test is using a configured endpoint even though placeholder mode was allowed.' -ForegroundColor Yellow
    }

    if (Test-Path -LiteralPath $smokeRoot) {
        $resolvedSmokeRoot = [IO.Path]::GetFullPath((Resolve-Path -LiteralPath $smokeRoot).Path)
        if (-not $resolvedSmokeRoot.StartsWith($repoRoot + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase)) {
            throw "Refusing to delete an unexpected smoke-test directory: $resolvedSmokeRoot"
        }
        Remove-Item -LiteralPath $resolvedSmokeRoot -Recurse -Force
    }

    $gameCore = Join-Path $smokeRoot 'game-core'
    $seedConfigDir = Join-Path $gameCore 'A_Potato_Seed'
    New-Item -ItemType Directory -Path $seedConfigDir -Force | Out-Null
    $isolatedSeedJar = Join-Path $gameCore 'Potato_Seed.jar'
    Copy-Item -LiteralPath $seedJar -Destination $isolatedSeedJar
    $disabledSeedConfig = @'
{
  "enableSeed": false,
  "enableUpdaterCheck": false,
  "remoteConfigUrl": "https://example.invalid/seed.json",
  "updaterDirName": "A_Potato_Updater",
  "updaterJarName": "Potato_Updater.jar"
}
'@
    [IO.File]::WriteAllText(
        (Join-Path $seedConfigDir 'seed_config.json'),
        $disabledSeedConfig,
        (New-Object Text.UTF8Encoding($false)))

    $previousErrorActionPreference = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    $seedOutput = & java "-javaagent:$isolatedSeedJar=gameCoreDir=$gameCore" -version 2>&1 | Out-String
    $seedExitCode = $LASTEXITCODE
    $ErrorActionPreference = $previousErrorActionPreference
    if ($seedExitCode -ne 0 -or $seedOutput -notmatch 'enableSeed=false') {
        throw "Seed Java Agent smoke test failed:`n$seedOutput"
    }
    Write-Host 'Potato_Seed.jar Java Agent smoke test passed.' -ForegroundColor Green

    $updaterOutput = & java -jar $updaterJar --smoke-test 2>&1 | Out-String
    if ($LASTEXITCODE -ne 0 -or $updaterOutput -notmatch 'POTATO_UPDATER_SMOKE_TEST_OK') {
        throw "Updater smoke test failed:`n$updaterOutput"
    }
    Write-Host 'Potato_Updater.jar smoke test passed.' -ForegroundColor Green

    $installerProcess = Start-Process -FilePath $installerExe `
        -ArgumentList '--smoke-test' `
        -WindowStyle Hidden `
        -Wait `
        -PassThru
    if ($installerProcess.ExitCode -ne 0) {
        throw "Installer smoke test failed with exit code $($installerProcess.ExitCode)."
    }
    Write-Host 'Potato_Seed_Installer.exe smoke test passed.' -ForegroundColor Green

    Write-Host 'All release-product smoke tests passed.' -ForegroundColor Green
}
finally {
    Pop-Location
}
