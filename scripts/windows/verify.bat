@echo off
setlocal
set "SCRIPT_DIR=%~dp0"
for %%I in ("%SCRIPT_DIR%..\..") do set "REPO_ROOT=%%~fI"
cd /d "%REPO_ROOT%"

call "%SCRIPT_DIR%_android_env.bat"
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
    echo Lint report: app\build\reports\lint-results-debug.html
    exit /b 1
)

git diff --check
if errorlevel 1 (
    echo.
    echo VERIFICATION FAILED: Git found whitespace errors.
    exit /b 1
)

echo.
echo VERIFICATION SUCCESSFUL
echo APK: %REPO_ROOT%\app\build\outputs\apk\debug\app-debug.apk
echo Lint: %REPO_ROOT%\app\build\reports\lint-results-debug.html
exit /b 0
