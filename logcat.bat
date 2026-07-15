@echo off
setlocal
cd /d "%~dp0"

call _android_env.bat
if errorlevel 1 exit /b 1

set "ADB_TARGET="
if not "%~1"=="" set "ADB_TARGET=-s %~1"

"%GYMLOG_ADB%" %ADB_TARGET% get-state >nul 2>&1
if errorlevel 1 (
    echo ERROR: No single authorized phone was found.
    echo Pass a serial when multiple devices are connected: logcat.bat SERIAL
    "%GYMLOG_ADB%" devices -l
    exit /b 1
)

echo Clearing old logs and launching GymLog...
"%GYMLOG_ADB%" %ADB_TARGET% logcat -c
"%GYMLOG_ADB%" %ADB_TARGET% shell am force-stop com.gymlog.app
"%GYMLOG_ADB%" %ADB_TARGET% shell am start -n com.gymlog.app/.MainActivity >nul
ping 127.0.0.1 -n 2 >nul

for /f "delims=" %%P in ('adb %ADB_TARGET% shell pidof com.gymlog.app') do set "GYMLOG_PID=%%P"
if not defined GYMLOG_PID (
    echo ERROR: GymLog did not start. Showing Android runtime errors instead.
    echo Press Ctrl+C to stop.
    "%GYMLOG_ADB%" %ADB_TARGET% logcat AndroidRuntime:E *:S
    exit /b 1
)

echo Showing logs for GymLog process %GYMLOG_PID%.
echo Press Ctrl+C to stop.
"%GYMLOG_ADB%" %ADB_TARGET% logcat --pid=%GYMLOG_PID%
exit /b %errorlevel%
