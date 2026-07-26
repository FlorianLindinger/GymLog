@echo off
setlocal
set "SCRIPT_DIR=%~dp0"
for %%I in ("%SCRIPT_DIR%..\..") do set "REPO_ROOT=%%~fI"
set "APP_ROOT=%REPO_ROOT%\app"
cd /d "%REPO_ROOT%"

call "%SCRIPT_DIR%helpers\android_env.bat"
if errorlevel 1 exit /b 1

set "ADB_TARGET="
if not "%~1"=="" set "ADB_TARGET=-s %~1"

"%GYMLOG_ADB%" %ADB_TARGET% get-state >nul 2>&1
if errorlevel 1 (
    echo ERROR: No single authorized phone was found.
    echo Pass a serial when multiple devices are connected: screenshot.bat SERIAL
    "%GYMLOG_ADB%" devices -l
    exit /b 1
)

if not exist "%APP_ROOT%\screenshots" mkdir "%APP_ROOT%\screenshots"
for /f "delims=" %%T in ('powershell -NoProfile -Command "Get-Date -Format yyyyMMdd-HHmmss"') do set "TIMESTAMP=%%T"
set "OUTPUT=%APP_ROOT%\screenshots\GymLog-%TIMESTAMP%.png"

"%GYMLOG_ADB%" %ADB_TARGET% exec-out screencap -p > "%OUTPUT%"
if errorlevel 1 (
    echo SCREENSHOT FAILED
    exit /b 1
)

echo Screenshot saved:
echo %OUTPUT%
exit /b 0
