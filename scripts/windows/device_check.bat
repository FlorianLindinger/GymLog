@echo off
setlocal
set "SCRIPT_DIR=%~dp0"
for %%I in ("%SCRIPT_DIR%..\..") do set "REPO_ROOT=%%~fI"
cd /d "%REPO_ROOT%"

call "%SCRIPT_DIR%helpers\android_env.bat"
if errorlevel 1 exit /b 1

set "ADB_TARGET="
if not "%~1"=="" set "ADB_TARGET=-s %~1"

echo ============================================================
echo                 Android device check
echo ============================================================
"%GYMLOG_ADB%" start-server >nul 2>&1
"%GYMLOG_ADB%" devices -l
echo.

"%GYMLOG_ADB%" %ADB_TARGET% get-state >nul 2>&1
if errorlevel 1 (
    echo No single authorized device could be selected.
    echo Unlock the phone, approve USB debugging, or pass its serial:
    echo     scripts\windows\device_check.bat SERIAL
    exit /b 1
)

for /f "delims=" %%V in ('adb %ADB_TARGET% shell getprop ro.product.manufacturer') do set "MANUFACTURER=%%V"
for /f "delims=" %%V in ('adb %ADB_TARGET% shell getprop ro.product.model') do set "MODEL=%%V"
for /f "delims=" %%V in ('adb %ADB_TARGET% shell getprop ro.build.version.release') do set "ANDROID_VERSION=%%V"
for /f "delims=" %%V in ('adb %ADB_TARGET% shell getprop ro.build.version.sdk') do set "ANDROID_SDK=%%V"

echo Device:  %MANUFACTURER% %MODEL%
echo Android: %ANDROID_VERSION% ^(API %ANDROID_SDK%^)
echo.
echo GymLog package:
"%GYMLOG_ADB%" %ADB_TARGET% shell dumpsys package com.gymlog.app | findstr /R /C:"versionName=" /C:"versionCode="
if errorlevel 1 echo Not installed.
exit /b 0
