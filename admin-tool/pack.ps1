[CmdletBinding()]
param ()

# ================= Logic Encoding Safety =================
$OutputEncoding = [System.Text.Encoding]::UTF8
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8

# ================= String Constants =================
$Str_Banner = "================================================================"
$Str_Title  = "                Potato Oven (Manager Console)                  "
$Str_Step1  = "[Step 1] Verifying permissions and loading list.json..."
$Str_Step2  = "[Step 2] Scanning physical files and calculating hashes..."
$Str_Step3  = "[Step 3] Generating updated list.json (OperationTime)..."
$Str_Step4  = "[Step 4] Compressing Potato_Pack to ZIP..."
$Str_Confirm = "Confirm to proceed with packaging? (Y/N) [Default: Y]"

$CN_Added    = "Added Files"
$CN_Modified = "Modified Files"
$CN_Deleted  = "Files moved to DeleteZone"
$CN_Total    = "Total Managed"
$CN_Cleanup  = "Cleanup Tasks"

# ================= Configuration =================
$ScriptDir = $PSScriptRoot
if (-not $ScriptDir) { $ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path }

$PotatoPackDir = Join-Path $ScriptDir "Potato_Pack"
$ResultDir     = Join-Path $ScriptDir "RESULT"
$ListJsonPath  = Join-Path $PotatoPackDir "list.json"

function Normalize-ManagedPath {
    param(
        [string]$PathValue
    )

    if ([string]::IsNullOrWhiteSpace($PathValue)) {
        return $PathValue
    }

    $normalized = $PathValue.Replace("\", "/").TrimStart("/")
    if ($normalized -like "luncher_dir/*") {
        return ("launcher_dir/" + $normalized.Substring("luncher_dir/".Length))
    }
    if ($normalized -eq "luncher_dir") {
        return "launcher_dir"
    }
    return $normalized
}

function Test-ManagedUpdatePath {
    param(
        [string]$PathValue
    )

    $normalized = Normalize-ManagedPath $PathValue
    if ([string]::IsNullOrWhiteSpace($normalized)) {
        return $false
    }
    return $normalized -like "game_core_dir/*" -or $normalized -like "launcher_dir/*"
}

Write-Host $Str_Banner -ForegroundColor Cyan
Write-Host $Str_Title  -ForegroundColor Yellow
Write-Host $Str_Banner -ForegroundColor Cyan
Write-Host "Local Dir: $PotatoPackDir" -ForegroundColor DarkGray
Write-Host "----------------------------------------------------------------" -ForegroundColor Cyan

# Step 1: Init
Write-Host "`n$Str_Step1" -ForegroundColor Yellow

if (-not (Test-Path -LiteralPath $PotatoPackDir)) {
    Write-Host "Error: Cannot find $PotatoPackDir" -ForegroundColor Red
    pause
    exit 1
}

if (-not (Test-Path -LiteralPath $ResultDir)) {
    New-Item -ItemType Directory -Path $ResultDir -Force | Out-Null
}

$oldFilesMap = @{}
$previousDeleteZone = @()

if (Test-Path -LiteralPath $ListJsonPath) {
    try {
        $jsonContent = Get-Content -LiteralPath $ListJsonPath -Encoding UTF8 -Raw
        $oldManifest = $jsonContent | ConvertFrom-Json

        if ($oldManifest.files) {
            foreach ($f in $oldManifest.files) {
                $urlKey = Normalize-ManagedPath $f.path
                if (-not $urlKey) { $urlKey = Normalize-ManagedPath $f.downloadUrl }
                if (-not $urlKey) { $urlKey = Normalize-ManagedPath $f.url }
                if (-not $urlKey) { $urlKey = Normalize-ManagedPath $f.relativePath }
                if ($f.path) { $f.path = Normalize-ManagedPath $f.path }
                if ($urlKey -and (Test-ManagedUpdatePath $urlKey)) { $oldFilesMap[$urlKey] = $f }
            }
        }
        if ($oldManifest.deleteZone) {
            $previousDeleteZone = @($oldManifest.deleteZone)
        }
        Write-Host "Success: list.json loaded." -ForegroundColor Green
    } catch {
        Write-Host "Warning: Failed to parse list.json. Starting fresh." -ForegroundColor Yellow
    }
}

# Step 2: Scan
Write-Host "`n$Str_Step2" -ForegroundColor Yellow
$currentFiles = Get-ChildItem -LiteralPath $PotatoPackDir -Recurse -File | Where-Object {
    $relPath = $_.FullName.Substring($PotatoPackDir.Length).TrimStart('\')
    Test-ManagedUpdatePath $relPath
}

$newFilesList = @()
$newFilesMap = @{}
$addedFiles = @()
$modifiedFiles = @()

foreach ($f in $currentFiles) {
    # Convert path to forward slashes
    $relPath = $f.FullName.Substring($PotatoPackDir.Length).TrimStart('\')
    $cleanRelPath = Normalize-ManagedPath $relPath
    if (-not (Test-ManagedUpdatePath $cleanRelPath)) {
        continue
    }

    $hashObj = Get-FileHash -LiteralPath $f.FullName -Algorithm SHA256 -ErrorAction SilentlyContinue
    if ($null -eq $hashObj -or $null -eq $hashObj.Hash) {
        Write-Host "  [Warning] Failed to calculate hash: $cleanRelPath. Skipping." -ForegroundColor Yellow
        continue
    }
    $hash = $hashObj.Hash.ToLower()

    $entry = @{
        path = $cleanRelPath
        fileName = $f.Name
        sizeBytes = [int64]$f.Length
        updateTime = (Get-Date).ToString("yyyy-MM-dd HH:mm:ss")
        hash256 = $hash
        category = "default"
    }

    $newFilesList += $entry
    $newFilesMap[$cleanRelPath] = $entry

    if (-not $oldFilesMap.ContainsKey($cleanRelPath)) {
        $addedFiles += $entry
    } else {
        $oldEntry = $oldFilesMap[$cleanRelPath]
        if ($oldEntry.hash256 -ne $hash) {
            $modifiedFiles += $entry
        } else {
            if ($oldEntry.updateTime) { $entry.updateTime = $oldEntry.updateTime }
        }
    }
}

$deletedFiles = @()
foreach ($oldKey in $oldFilesMap.Keys) {
    if (-not $newFilesMap.ContainsKey($oldKey)) {
        $deletedEntry = $oldFilesMap[$oldKey]
        # Compatibility mapping for old properties
        if (-not $deletedEntry.path) {
            $deletedEntry | Add-Member -MemberType NoteProperty -Name "path" -Value $oldKey -Force
        }
        $deletedFiles += $deletedEntry
    }
}

$newDeleteZone = @()
$deleteZoneMap = @{}

# Inherit old deleteZone items not restored
foreach ($dz in $previousDeleteZone) {
    $key = Normalize-ManagedPath $dz.path
    if (-not $key) {
        $key = $dz.relativePath
        if     ($dz.pathType -eq "GameCore")       { $key = "game_core_dir/$key" }
        elseif ($dz.pathType -eq "MinecraftUpper") { $key = "launcher_dir/$key" }
    }
    $key = Normalize-ManagedPath $key

    if (-not $key -or -not (Test-ManagedUpdatePath $key)) { continue }

    # Keep if it has not returned to the physical scan
    if (-not $newFilesMap.ContainsKey($key)) {
        $dzEntry = @{
            path = $key
            reason = $dz.reason
        }
        if (-not $deleteZoneMap.ContainsKey($key)) {
            $deleteZoneMap[$key] = $dzEntry
            $newDeleteZone += $dzEntry
        }
    }
}

# Add newly deleted items to deleteZone
foreach ($df in $deletedFiles) {
    $key = Normalize-ManagedPath $df.path
    if (-not $deleteZoneMap.ContainsKey($key)) {
        $dzEntry = @{
            path = $key
            reason = "Removed at $((Get-Date).ToString('yyyy-MM-dd HH:mm:ss'))"
        }
        $newDeleteZone += $dzEntry
        $deleteZoneMap[$key] = $dzEntry
    }
}

# Results Display
Write-Host "`n---[ Diff Result ]---" -ForegroundColor Cyan
if ($addedFiles.Count -eq 0 -and $modifiedFiles.Count -eq 0 -and $deletedFiles.Count -eq 0) {
    Write-Host "No changes detected." -ForegroundColor Green
} else {
    if ($addedFiles.Count -gt 0) {
        $count = $addedFiles.Count
        Write-Host "+ $CN_Added ($count):" -ForegroundColor Green
        foreach ($af in $addedFiles) { Write-Host "  -> $($af.path)" -ForegroundColor DarkGreen }
    }
    if ($modifiedFiles.Count -gt 0) {
        $count = $modifiedFiles.Count
        Write-Host "* $CN_Modified ($count):" -ForegroundColor Yellow
        foreach ($mf in $modifiedFiles) { Write-Host "  -> $($mf.path)" -ForegroundColor DarkYellow }
    }
    if ($deletedFiles.Count -gt 0) {
        $count = $deletedFiles.Count
        Write-Host "- $CN_Deleted ($count):" -ForegroundColor Red
        foreach ($df in $deletedFiles) { Write-Host "  -> $($df.path)" -ForegroundColor DarkRed }
    }
}
Write-Host "---------------------" -ForegroundColor Cyan
Write-Host "$CN_Total : $($newFilesList.Count)"
Write-Host "$CN_Cleanup : $($newDeleteZone.Count)"
Write-Host ""

$response = Read-Host $Str_Confirm
if ($response -match "^[Nn]") {
    Write-Host "Operation cancelled." -ForegroundColor Yellow
    exit 0
}

# Step 3: list.json
Write-Host "`n$Str_Step3" -ForegroundColor Yellow
$opTime = (Get-Date).ToString("yyyy-MM-dd HH:mm:ss")
$manifestObj = [ordered]@{
    operationTime = $opTime
    deleteZone = $newDeleteZone
    files = $newFilesList
}
$manifestObj | ConvertTo-Json -Depth 6 | Out-File -LiteralPath $ListJsonPath -Encoding utf8
Write-Host "Success: list.json updated." -ForegroundColor Green

# Step 4: Zip
Write-Host "`n$Str_Step4" -ForegroundColor Yellow
$zipName = "potato_pack_$((Get-Date).ToString('yyyyMMdd_HHmmss')).zip"
$zipPath = Join-Path $ResultDir $zipName
if (Test-Path -LiteralPath $zipPath) { Remove-Item -LiteralPath $zipPath -Force }
Compress-Archive -LiteralPath $PotatoPackDir -DestinationPath $zipPath -CompressionLevel Optimal

Write-Host "`nSuccessfully packed: $zipPath" -ForegroundColor Green
Write-Host $Str_Banner -ForegroundColor Cyan
