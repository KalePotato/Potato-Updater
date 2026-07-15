[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string]$OutputPath,
    [string]$ConfigPath
)

$ErrorActionPreference = 'Stop'
$syncBaseUrl = $env:POTATO_SYNC_BASE_URL

if ([string]::IsNullOrWhiteSpace($syncBaseUrl) -and
    -not [string]::IsNullOrWhiteSpace($ConfigPath) -and
    (Test-Path -LiteralPath $ConfigPath -PathType Leaf)) {
    foreach ($line in Get-Content -LiteralPath $ConfigPath -Encoding UTF8) {
        if ($line -match '^\s*syncBaseUrl\s*=\s*(?<value>.+?)\s*$') {
            $syncBaseUrl = $Matches.value
            break
        }
    }
}

if ([string]::IsNullOrWhiteSpace($syncBaseUrl)) {
    if ($env:POTATO_ALLOW_PLACEHOLDER_ENDPOINT -eq '1') {
        $syncBaseUrl = 'https://example.invalid'
    } else {
        throw 'No sync endpoint is configured. Set POTATO_SYNC_BASE_URL or create config/endpoints.local.properties before building the installer.'
    }
}
$syncBaseUrl = $syncBaseUrl.Trim().TrimEnd('/')

$uri = $null
if (-not [Uri]::TryCreate($syncBaseUrl, [UriKind]::Absolute, [ref]$uri) -or
    [string]::IsNullOrWhiteSpace($uri.Host) -or
    ($uri.Scheme -ne 'http' -and $uri.Scheme -ne 'https')) {
    throw 'POTATO_SYNC_BASE_URL or syncBaseUrl must be an absolute HTTP(S) URL.'
}
if ($uri.Host.Equals('example.invalid', [StringComparison]::OrdinalIgnoreCase) -and
    $env:POTATO_ALLOW_PLACEHOLDER_ENDPOINT -ne '1') {
    throw 'The placeholder endpoint is not allowed for installer packaging. Configure a private endpoint first.'
}

$outputDirectory = Split-Path -Parent ([IO.Path]::GetFullPath($OutputPath))
if (-not (Test-Path -LiteralPath $outputDirectory -PathType Container)) {
    New-Item -ItemType Directory -Path $outputDirectory | Out-Null
}

$escapedBaseUrl = $syncBaseUrl.Replace('\', '\\').Replace('"', '\"')
$source = @"
namespace PotatoSeedInstaller
{
    internal static class BuildEndpoints
    {
        internal const string SeedDownloadUrl = "$escapedBaseUrl/Potato_Seed.jar";
        internal const string SeedConfigUrl = "$escapedBaseUrl/seed.json";
    }
}
"@

Set-Content -LiteralPath $OutputPath -Value $source -Encoding UTF8
