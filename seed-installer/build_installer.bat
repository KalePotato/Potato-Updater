@echo off
setlocal
set "ROOT=%~dp0.."
set "CSC=C:\Windows\Microsoft.NET\Framework64\v4.0.30319\csc.exe"
set "BUILD=%~dp0build"
set "OUT=%BUILD%\Potato_Seed_Installer.exe"
set "SRC=%~dp0PotatoSeedInstaller.cs"
set "GENERATED=%BUILD%\BuildEndpoints.g.cs"
set "ENDPOINT_CONFIG=%ROOT%\config\endpoints.local.properties"

if not exist "%CSC%" (
  echo csc.exe not found.
  exit /b 1
)

if not exist "%BUILD%" mkdir "%BUILD%"

powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%ROOT%\tools\generate-installer-endpoints.ps1" -OutputPath "%GENERATED%" -ConfigPath "%ENDPOINT_CONFIG%"
if errorlevel 1 exit /b %ERRORLEVEL%

"%CSC%" ^
  /nologo ^
  /target:winexe ^
  /out:"%OUT%" ^
  /reference:System.dll ^
  /reference:System.Core.dll ^
  /reference:System.Drawing.dll ^
  /reference:System.Windows.Forms.dll ^
  /reference:System.Web.Extensions.dll ^
  "%SRC%" ^
  "%GENERATED%"

exit /b %ERRORLEVEL%
