@echo off
setlocal EnableExtensions
set "SCRIPT_DIR=%~dp0"
for %%I in ("%SCRIPT_DIR%..\..\..") do set "REPO_ROOT=%%~fI"
set "APP_ROOT=%REPO_ROOT%\app"
cd /d "%APP_ROOT%"

if "%~1"=="" (
    echo Usage: scripts\windows\helpers\release.bat VERSION [--create-tag]
    echo Example: scripts\windows\helpers\release.bat 1.0
    exit /b 1
)

set "VERSION=%~1"
if /i "%VERSION:~0,1%"=="v" set "VERSION=%VERSION:~1%"
set "CREATE_TAG="
if /i "%~2"=="--create-tag" set "CREATE_TAG=y"

set "CURRENT_VERSION="
for /f "tokens=3" %%V in ('findstr /C:"versionName = " app\build.gradle.kts') do set "CURRENT_VERSION=%%~V"
if not "%CURRENT_VERSION%"=="%VERSION%" (
    echo ERROR: app\build.gradle.kts does not contain versionName = "%VERSION%".
    echo Update versionName and versionCode before releasing.
    exit /b 1
)

for /f "delims=" %%S in ('git status --porcelain') do (
    echo ERROR: The Git working tree is not clean.
    echo Commit or stash all changes before making a release.
    git status --short
    exit /b 1
)

git rev-parse "v%VERSION%" >nul 2>&1
if not errorlevel 1 (
    echo ERROR: Git tag v%VERSION% already exists.
    exit /b 1
)

if not defined GYMLOG_KEYSTORE_FILE goto signing_missing
if not defined GYMLOG_KEYSTORE_PASSWORD goto signing_missing
if not defined GYMLOG_KEY_ALIAS goto signing_missing
if not defined GYMLOG_KEY_PASSWORD goto signing_missing
if not exist "%GYMLOG_KEYSTORE_FILE%" (
    echo ERROR: GYMLOG_KEYSTORE_FILE does not exist.
    exit /b 1
)

call "%SCRIPT_DIR%android_env.bat"
if errorlevel 1 exit /b 1

call "%SCRIPT_DIR%..\verify.bat"
if errorlevel 1 exit /b 1

echo Building signed release APK and Android App Bundle...
call gradlew.bat :app:assembleRelease :app:bundleRelease --no-daemon
if errorlevel 1 (
    echo RELEASE BUILD FAILED
    exit /b 1
)

set "APK=%GYMLOG_BUILD_DIR%\outputs\apk\release\app-release.apk"
set "AAB=%GYMLOG_BUILD_DIR%\outputs\bundle\release\app-release.aab"
if not exist "%APK%" (
    echo ERROR: Signed release APK was not produced.
    exit /b 1
)
if not exist "%AAB%" (
    echo ERROR: Release App Bundle was not produced.
    exit /b 1
)

set "APKSIGNER="
for /f "delims=" %%A in ('powershell -NoProfile -Command "Get-ChildItem -Path $env:ANDROID_HOME\build-tools -Filter apksigner.bat -Recurse | Sort-Object FullName -Descending | Select-Object -First 1 -ExpandProperty FullName"') do set "APKSIGNER=%%A"
if not defined APKSIGNER (
    echo ERROR: apksigner was not found in the Android SDK build-tools.
    exit /b 1
)
call "%APKSIGNER%" verify --verbose --print-certs "%APK%"
if errorlevel 1 (
    echo ERROR: The release APK signature could not be verified.
    exit /b 1
)

set "ARTIFACT_DIR=%APP_ROOT%\artifacts\release\%VERSION%"
if not exist "%ARTIFACT_DIR%" mkdir "%ARTIFACT_DIR%"
copy /y "%APK%" "%ARTIFACT_DIR%\GymLog-%VERSION%.apk" >nul
if errorlevel 1 goto publish_failed
copy /y "%AAB%" "%ARTIFACT_DIR%\GymLog-%VERSION%.aab" >nul
if errorlevel 1 goto publish_failed
set "APK=%ARTIFACT_DIR%\GymLog-%VERSION%.apk"
set "AAB=%ARTIFACT_DIR%\GymLog-%VERSION%.aab"
set "CHECKSUMS=%ARTIFACT_DIR%\SHA256SUMS.txt"
powershell -NoProfile -Command "$files = @('%APK%', '%AAB%'); $files | ForEach-Object { $h = Get-FileHash -Algorithm SHA256 -LiteralPath $_; '{0}  {1}' -f $h.Hash, (Split-Path $_ -Leaf) } | Set-Content -Encoding ASCII -LiteralPath '%CHECKSUMS%'"
if errorlevel 1 (
    echo ERROR: Could not create SHA-256 checksums.
    exit /b 1
)

echo.
echo RELEASE BUILD SUCCESSFUL
echo APK:      %APK%
echo AAB:      %AAB%
echo Checksums: %CHECKSUMS%
echo.
if not defined CREATE_TAG set /p CREATE_TAG="Create local Git tag v%VERSION% now? [y/N] "
if /i "%CREATE_TAG%"=="y" (
    git tag -a "v%VERSION%" -m "GymLog v%VERSION%"
    if errorlevel 1 exit /b 1
    echo Created local tag v%VERSION%.
    echo Push it when ready with: git push origin v%VERSION%
)
exit /b 0

:publish_failed
echo ERROR: Release artifacts could not be copied into the repository.
exit /b 1

:signing_missing
echo ERROR: Release signing is not configured.
echo Set these environment variables without committing their values:
echo   GYMLOG_KEYSTORE_FILE
echo   GYMLOG_KEYSTORE_PASSWORD
echo   GYMLOG_KEY_ALIAS
echo   GYMLOG_KEY_PASSWORD
exit /b 1
