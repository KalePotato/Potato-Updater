[CmdletBinding()]
param(
    [string]$TestCore = $env:POTATO_TEST_CORE,
    [switch]$SkipBuild
)

$ErrorActionPreference = 'Stop'
$repoRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))

if ([string]::IsNullOrWhiteSpace($TestCore)) {
    throw 'Set POTATO_TEST_CORE or pass -TestCore with an external Minecraft game-core directory.'
}
if (-not (Test-Path -LiteralPath $TestCore -PathType Container)) {
    throw "Test game-core directory does not exist: $TestCore"
}

$core = [IO.Path]::GetFullPath((Resolve-Path -LiteralPath $TestCore).Path)
if ($core.StartsWith($repoRoot + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase)) {
    throw 'The test client must be outside the Git repository.'
}

$coreLeaf = Split-Path -Leaf $core
$parent = Split-Path -Parent $core
$parentLeaf = Split-Path -Leaf $parent
$grandParent = Split-Path -Parent $parent
$grandParentLeaf = Split-Path -Leaf $grandParent
$standardLayout = $coreLeaf -ieq '.minecraft' -or
    ($parentLeaf -ieq 'versions' -and $grandParentLeaf -ieq '.minecraft')
$managedLayout = (Test-Path -LiteralPath (Join-Path $core 'Potato_Seed.jar')) -or
    (Test-Path -LiteralPath (Join-Path $core 'A_Potato_Seed')) -or
    (Test-Path -LiteralPath (Join-Path $core 'A_Potato_Updater'))

if (-not $standardLayout -and -not $managedLayout) {
    throw "Unsupported test game-core layout: $core"
}

if (-not $SkipBuild) {
    & (Join-Path $PSScriptRoot 'build-release.ps1')
}

$distDir = Join-Path $repoRoot 'dist'
$seedSource = Join-Path $distDir 'Potato_Seed.jar'
$updaterSource = Join-Path $distDir 'Potato_Updater.jar'
$installerSource = Join-Path $distDir 'Potato_Seed_Installer.exe'
foreach ($product in @($seedSource, $updaterSource, $installerSource)) {
    if (-not (Test-Path -LiteralPath $product -PathType Leaf)) {
        throw "Missing staged build product: $product"
    }
}

$updaterDir = Join-Path $core 'A_Potato_Updater'
New-Item -ItemType Directory -Path $updaterDir -Force | Out-Null
Copy-Item -LiteralPath $seedSource -Destination (Join-Path $core 'Potato_Seed.jar') -Force
Copy-Item -LiteralPath $updaterSource -Destination (Join-Path $updaterDir 'Potato_Updater.jar') -Force
Copy-Item -LiteralPath $installerSource -Destination (Join-Path $core 'Potato_Seed_Installer.exe') -Force

Write-Host "Deployed current build to $core" -ForegroundColor Green
Get-FileHash -Algorithm SHA256 -LiteralPath @(
    (Join-Path $core 'Potato_Seed.jar'),
    (Join-Path $updaterDir 'Potato_Updater.jar'),
    (Join-Path $core 'Potato_Seed_Installer.exe')
) | Format-Table Hash, Path -AutoSize
