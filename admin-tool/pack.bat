@echo off
:: Potato Oven Launcher
:: Using simple ASCII here to avoid Windows CMD encoding issues with UTF-8/BOM.

setlocal
set "SCRIPT_DIR=%~dp0"
cd /d "%SCRIPT_DIR%"

echo [INFO] Initializing Potato Oven Environment...
echo [INFO] Launching PowerShell context...

powershell.exe -NoProfile -ExecutionPolicy Bypass -File "pack.ps1"

if %ERRORLEVEL% NEQ 0 (
    echo.
    echo [ERROR] The script exited with error code: %ERRORLEVEL%
)

echo.
echo ===================================================
echo   Process finished.
echo ===================================================
pause
endlocal
