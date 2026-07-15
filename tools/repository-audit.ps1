[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$repoRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
Push-Location $repoRoot
try {
    $tracked = @(git ls-files)
    if ($LASTEXITCODE -ne 0) {
        throw 'Unable to enumerate tracked files.'
    }

    $forbidden = @()
    foreach ($path in $tracked) {
        $normalized = $path.Replace('\', '/')
        if ($normalized -match '(^|/)(build|bin|result|dist|diagnostics|test_place|tmp_[^/]*)(/|$)') {
            $forbidden += $path
            continue
        }
        if ($normalized -match '\.(class|exe|dll|log|zip|nupkg)$') {
            $forbidden += $path
            continue
        }
        if ($normalized -match '\.jar$' -and $normalized -ne 'gradle/wrapper/gradle-wrapper.jar') {
            $forbidden += $path
            continue
        }
        if ($normalized -eq 'config/endpoints.local.properties') {
            $forbidden += $path
        }
    }
    if ($forbidden.Count -gt 0) {
        throw "Forbidden generated or runtime files are tracked:`n$($forbidden -join "`n")"
    }

    $personalPaths = @(git grep -n -I -E '[A-Za-z]:\\Users\\|D:\\Minecraft' -- . 2>$null)
    $grepExitCode = $LASTEXITCODE
    if ($grepExitCode -gt 1) {
        throw "Unable to scan tracked files for personal paths (git grep exit code $grepExitCode)."
    }
    if ($personalPaths.Count -gt 0) {
        throw "Personal absolute paths were found in tracked files:`n$($personalPaths -join "`n")"
    }

    $localEndpointConfig = Join-Path $repoRoot 'config\endpoints.local.properties'
    if (Test-Path -LiteralPath $localEndpointConfig -PathType Leaf) {
        $privateBaseUrl = $null
        foreach ($line in Get-Content -LiteralPath $localEndpointConfig -Encoding UTF8) {
            if ($line -match '^\s*syncBaseUrl\s*=\s*(?<value>.+?)\s*$') {
                $privateBaseUrl = $Matches.value
                break
            }
        }

        $privateUri = $null
        if (-not [string]::IsNullOrWhiteSpace($privateBaseUrl) -and
            [Uri]::TryCreate($privateBaseUrl, [UriKind]::Absolute, [ref]$privateUri) -and
            -not [string]::IsNullOrWhiteSpace($privateUri.Host)) {
            $endpointLeaks = @(git grep -l -I -F -- $privateUri.Host -- . 2>$null)
            $endpointGrepExitCode = $LASTEXITCODE
            if ($endpointGrepExitCode -gt 1) {
                throw "Unable to scan tracked files for the private endpoint (git grep exit code $endpointGrepExitCode)."
            }
            if ($endpointLeaks.Count -gt 0) {
                throw "The private endpoint host was found in tracked files:`n$($endpointLeaks -join "`n")"
            }
        }
    }

    $oversized = @()
    foreach ($path in $tracked) {
        $item = Get-Item -LiteralPath (Join-Path $repoRoot $path)
        if ($item.Length -gt 10MB) {
            $oversized += "$path ($($item.Length) bytes)"
        }
    }
    if ($oversized.Count -gt 0) {
        throw "Unexpected tracked files larger than 10 MiB:`n$($oversized -join "`n")"
    }

    Write-Host "Repository audit passed for $($tracked.Count) tracked files." -ForegroundColor Green
    $global:LASTEXITCODE = 0
}
finally {
    Pop-Location
}
