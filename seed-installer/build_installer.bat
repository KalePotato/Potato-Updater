@echo off
setlocal
set "ROOT=%~dp0.."
set "CSC=C:\Windows\Microsoft.NET\Framework64\v4.0.30319\csc.exe"
set "BUILD=%~dp0build"
set "OUT=%BUILD%\Potato_Seed_Installer.exe"
set "SRC=%~dp0PotatoSeedInstaller.cs"

if not exist "%CSC%" (
  echo csc.exe not found.
  exit /b 1
)

if not exist "%BUILD%" mkdir "%BUILD%"

"%CSC%" ^
  /nologo ^
  /target:winexe ^
  /out:"%OUT%" ^
  /reference:System.dll ^
  /reference:System.Core.dll ^
  /reference:System.Drawing.dll ^
  /reference:System.Windows.Forms.dll ^
  /reference:System.Web.Extensions.dll ^
  "%SRC%"

exit /b %ERRORLEVEL%
