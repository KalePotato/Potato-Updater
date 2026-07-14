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
