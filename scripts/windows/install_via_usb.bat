@echo off
setlocal

rem GymLog USB installation manual:
rem 1. On the phone, tap Build number seven times to enable Developer options.
rem 2. Enable USB debugging in Developer options.
rem 3. Connect and unlock the phone, then approve the USB debugging prompt.
rem 4. Run build.bat after changing the app, then run this installer.

set "SCRIPT_DIR=%~dp0"
for %%I in ("%SCRIPT_DIR%..\..") do set "REPO_ROOT=%%~fI"
cd /d "%REPO_ROOT%"

call "%SCRIPT_DIR%_android_env.bat"
if errorlevel 1 exit /b 1

set "APK=%REPO_ROOT%\artifacts\debug\app-debug.apk"
set "ADB=%GYMLOG_ADB%"
set "ADB_TARGET="
if not "%~1"=="" set "ADB_TARGET=-s %~1"

echo ============================================================
echo                  GymLog USB installation
echo ============================================================
echo 1. Enable Developer options by tapping Build number 7 times.
echo 2. Enable USB debugging in Developer options.
echo 3. Connect the unlocked phone with a USB data cable.
echo 4. Approve the USB debugging prompt on the phone.
echo 5. Keep the phone unlocked while GymLog is installed.
echo ============================================================
echo.

if not exist "%ADB%" (
    echo ERROR: Android Debug Bridge was not found.
    echo Expected: %ADB%
    echo Install Android SDK Platform-Tools through Android Studio.
    pause
    exit /b 1
)

if not exist "%APK%" (
    echo ERROR: The GymLog APK has not been built yet.
    echo Run scripts\windows\build.bat first.
    pause
    exit /b 1
)

echo Looking for an authorized Android phone...
"%ADB%" start-server >nul 2>&1
"%ADB%" %ADB_TARGET% get-state >nul 2>&1
if errorlevel 1 (
    echo.
    echo No authorized phone was found.
    echo 1. Connect the phone with a USB data cable.
    echo 2. Enable Developer options and USB debugging.
    echo 3. Unlock the phone and approve the USB debugging prompt.
    echo.
    "%ADB%" devices
    pause
    exit /b 1
)

echo Installing GymLog without deleting existing app data...
"%ADB%" %ADB_TARGET% install -r "%APK%"
if errorlevel 1 (
    echo.
    echo INSTALL FAILED
    echo Check the message above and make sure the phone is unlocked.
    pause
    exit /b 1
)

echo Launching GymLog...
"%ADB%" %ADB_TARGET% shell am start -n com.gymlog.app/.MainActivity >nul

echo.
echo INSTALL SUCCESSFUL
echo GymLog should now be open on your phone.
pause
exit /b 0
