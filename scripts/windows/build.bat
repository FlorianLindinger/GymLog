@echo off
setlocal
set "SCRIPT_DIR=%~dp0"
for %%I in ("%SCRIPT_DIR%..\..") do set "REPO_ROOT=%%~fI"
cd /d "%REPO_ROOT%"
call "%SCRIPT_DIR%_android_env.bat"
if errorlevel 1 exit /b 1

echo Building GymLog...
call gradlew.bat assembleDebug --no-daemon
if errorlevel 1 (
    echo.
    echo BUILD FAILED
    exit /b 1
)

set "APK_SOURCE=%GYMLOG_BUILD_DIR%\outputs\apk\debug\app-debug.apk"
set "APK_DIR=%REPO_ROOT%\artifacts\debug"
set "APK=%APK_DIR%\app-debug.apk"
if not exist "%APK_DIR%" mkdir "%APK_DIR%"
copy /y "%APK_SOURCE%" "%APK%" >nul
if errorlevel 1 (
    echo.
    echo BUILD FAILED: The APK could not be copied into the repository.
    exit /b 1
)

echo.
echo BUILD SUCCESSFUL
echo APK: %APK%
exit /b 0
