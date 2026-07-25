@echo off
setlocal
set "SCRIPT_DIR=%~dp0"
for %%I in ("%SCRIPT_DIR%..\..") do set "REPO_ROOT=%%~fI"
cd /d "%REPO_ROOT%"

call "%SCRIPT_DIR%helpers\android_env.bat"
if errorlevel 1 exit /b 1

echo ============================================================
echo                    Verifying GymLog
echo ============================================================
echo Running compilation, unit tests, Android lint, and APK build.
echo.

call gradlew.bat :app:compileDebugKotlin :app:testDebugUnitTest :app:lintDebug :app:assembleDebug --no-daemon
if errorlevel 1 (
    echo.
    echo VERIFICATION FAILED
    echo Lint report: %GYMLOG_BUILD_DIR%\reports\lint-results-debug.html
    exit /b 1
)

git diff --check
if errorlevel 1 (
    echo.
    echo VERIFICATION FAILED: Git found whitespace errors.
    exit /b 1
)

set "APK_SOURCE=%GYMLOG_BUILD_DIR%\outputs\apk\debug\app-debug.apk"
set "APK_DIR=%REPO_ROOT%\artifacts\debug"
set "APK=%APK_DIR%\app-debug.apk"
if not exist "%APK_DIR%" mkdir "%APK_DIR%"
copy /y "%APK_SOURCE%" "%APK%" >nul
if errorlevel 1 (
    echo.
    echo VERIFICATION FAILED: The APK could not be copied into the repository.
    exit /b 1
)

echo.
echo VERIFICATION SUCCESSFUL
echo APK: %APK%
echo Lint: %GYMLOG_BUILD_DIR%\reports\lint-results-debug.html
exit /b 0
