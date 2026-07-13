@echo off
setlocal

cd /d "%~dp0"

set "APK=%~dp0app\build\outputs\apk\debug\app-debug.apk"
set "ADB=%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe"

if not exist "%ADB%" (
    echo ERROR: Android Debug Bridge was not found.
    echo Expected: %ADB%
    echo Install Android SDK Platform-Tools through Android Studio.
    pause
    exit /b 1
)

if not exist "%APK%" (
    echo ERROR: The GymLog APK has not been built yet.
    echo Run build.bat first.
    pause
    exit /b 1
)

echo Looking for an authorized Android phone...
"%ADB%" start-server >nul 2>&1
"%ADB%" get-state >nul 2>&1
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
"%ADB%" install -r "%APK%"
if errorlevel 1 (
    echo.
    echo INSTALL FAILED
    echo Check the message above and make sure the phone is unlocked.
    pause
    exit /b 1
)

echo Launching GymLog...
"%ADB%" shell am start -n com.gymlog.app/.MainActivity >nul

echo.
echo INSTALL SUCCESSFUL
echo GymLog should now be open on your phone.
pause
exit /b 0
