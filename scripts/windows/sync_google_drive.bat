@echo off
setlocal EnableExtensions EnableDelayedExpansion

rem Publishes the existing GymLog debug APK through Google Drive for desktop.
rem Optional settings:
rem   GYMLOG_GOOGLE_DRIVE_DIR
rem   GYMLOG_GOOGLE_DRIVE_SYNC_TIMEOUT_SECONDS (default 300)
rem   GYMLOG_GOOGLE_DRIVE_SETTLE_SECONDS (default 10)

set "SCRIPT_DIR=%~dp0"
for %%I in ("%SCRIPT_DIR%..\..") do set "REPO_ROOT=%%~fI"
cd /d "%REPO_ROOT%"
if not defined GYMLOG_GOOGLE_DRIVE_DIR set "GYMLOG_GOOGLE_DRIVE_DIR=%REPO_ROOT%\google_drive_synced"
call :start_drive
if errorlevel 1 exit /b 1
call :find_sync_folder
if errorlevel 1 exit /b 1

set "SOURCE=%REPO_ROOT%\artifacts\debug\app-debug.apk"
set "DEST_DIR=%GYMLOG_GOOGLE_DRIVE_DIR%"
set "DEST=%DEST_DIR%\app-debug.apk"
set "TEMP=%DEST%.uploading"
if not exist "%SOURCE%" (
    echo ERROR: Debug APK was not produced: "%SOURCE%"
    exit /b 1
)
if not exist "%DEST_DIR%\" mkdir "%DEST_DIR%"
if not exist "%DEST_DIR%\" (
    echo ERROR: Cannot access or create "%DEST_DIR%".
    exit /b 1
)

echo.
echo Copying the APK to Google Drive...
copy /y "%SOURCE%" "%TEMP%" >nul
if errorlevel 1 goto publish_failed
fc /b "%SOURCE%" "%TEMP%" >nul
if errorlevel 1 goto publish_failed
move /y "%TEMP%" "%DEST%" >nul
if errorlevel 1 goto publish_failed

call :wait_for_sync
if errorlevel 1 exit /b 1
echo.
echo GOOGLE DRIVE PUBLISH SUCCESSFUL
echo APK: "%DEST%"
exit /b 0

:publish_failed
if exist "%TEMP%" del /q "%TEMP%" >nul 2>&1
echo ERROR: The APK could not be copied and verified in "%DEST_DIR%".
exit /b 1

:start_drive
tasklist /fi "imagename eq GoogleDriveFS.exe" /nh 2>nul | find /i "GoogleDriveFS.exe" >nul
if not errorlevel 1 (
    echo Google Drive is already running.
    exit /b 0
)

set "DRIVE_EXE="
for %%F in (
    "%LOCALAPPDATA%\Google\DriveFS\GoogleDriveFS.exe"
    "%ProgramFiles%\Google\Drive File Stream\GoogleDriveFS.exe"
    "%ProgramFiles%\Google\Drive\GoogleDriveFS.exe"
) do if not defined DRIVE_EXE if exist "%%~fF" set "DRIVE_EXE=%%~fF"

rem New releases live in a version-numbered subdirectory.
if not defined DRIVE_EXE if exist "%ProgramFiles%\Google\Drive File Stream\" (
    for /f "delims=" %%F in ('dir /b /s /a-d "%ProgramFiles%\Google\Drive File Stream\GoogleDriveFS.exe" 2^>nul') do if not defined DRIVE_EXE set "DRIVE_EXE=%%~fF"
)
if not defined DRIVE_EXE if exist "%LOCALAPPDATA%\Google\DriveFS\" (
    for /f "delims=" %%F in ('dir /b /s /a-d "%LOCALAPPDATA%\Google\DriveFS\GoogleDriveFS.exe" 2^>nul') do if not defined DRIVE_EXE set "DRIVE_EXE=%%~fF"
)
if not defined DRIVE_EXE (
    echo ERROR: Google Drive is not running and GoogleDriveFS.exe was not found.
    exit /b 1
)

echo Starting Google Drive for desktop...
start "" "%DRIVE_EXE%"
for /l %%S in (1,1,30) do (
    powershell.exe -NoProfile -Command "Start-Sleep -Seconds 1"
    tasklist /fi "imagename eq GoogleDriveFS.exe" /nh 2>nul | find /i "GoogleDriveFS.exe" >nul
    if not errorlevel 1 (
        echo Google Drive is running.
        exit /b 0
    )
)
echo ERROR: Google Drive did not start within 30 seconds.
exit /b 1

:find_sync_folder
if defined GYMLOG_GOOGLE_DRIVE_DIR goto sync_folder_found

rem This repository folder is configured in Drive on the GymLog workstation.
if exist "%CD%\google_drive_synced\" set "GYMLOG_GOOGLE_DRIVE_DIR=%CD%\google_drive_synced"

rem Also support Drive's streamed disk and common mirrored locations.
if not defined GYMLOG_GOOGLE_DRIVE_DIR (
    echo Waiting for Google Drive to expose its sync folder...
    for /l %%S in (1,1,30) do (
        for %%D in (A B C D E F G H I J K L M N O P Q R S T U V W X Y Z) do if not defined GYMLOG_GOOGLE_DRIVE_DIR if exist "%%D:\My Drive\" set "GYMLOG_GOOGLE_DRIVE_DIR=%%D:\My Drive"
        for %%F in (
            "%USERPROFILE%\Google Drive\My Drive"
            "%USERPROFILE%\Google Drive"
            "%USERPROFILE%\My Drive"
        ) do if not defined GYMLOG_GOOGLE_DRIVE_DIR if exist "%%~fF\" set "GYMLOG_GOOGLE_DRIVE_DIR=%%~fF"
        if defined GYMLOG_GOOGLE_DRIVE_DIR goto sync_folder_found
        powershell.exe -NoProfile -Command "Start-Sleep -Seconds 1"
    )
)
echo ERROR: No Google Drive sync folder was found.
echo Set GYMLOG_GOOGLE_DRIVE_DIR to the folder Drive synchronizes.
exit /b 1

:sync_folder_found
if not exist "%GYMLOG_GOOGLE_DRIVE_DIR%\" (
    echo ERROR: Google Drive folder is not accessible: "%GYMLOG_GOOGLE_DRIVE_DIR%"
    exit /b 1
)
echo Google Drive folder: "%GYMLOG_GOOGLE_DRIVE_DIR%"
exit /b 0

:wait_for_sync
set "SYNC_FILE=%DEST%"
set "SYNC_STATUS="
call :get_sync_status
if not defined SYNC_STATUS (
    set "SETTLE=%GYMLOG_GOOGLE_DRIVE_SETTLE_SECONDS%"
    if not defined SETTLE set "SETTLE=10"
    echo Google Drive does not expose this file's sync state to Windows.
    if not "!SETTLE!"=="0" (
        echo Waiting !SETTLE! seconds for Drive to pick up the verified file...
        powershell.exe -NoProfile -Command "Start-Sleep -Seconds !SETTLE!"
    )
    echo Local copy verified; cloud status is available in the Google Drive tray app.
    exit /b 0
)

set "SYNC_TIMEOUT=%GYMLOG_GOOGLE_DRIVE_SYNC_TIMEOUT_SECONDS%"
if not defined SYNC_TIMEOUT set "SYNC_TIMEOUT=300"
echo Waiting for Google Drive to report that the APK is synced...
for /l %%S in (0,2,!SYNC_TIMEOUT!) do (
    call :get_sync_status
    echo !SYNC_STATUS! | findstr /i /x /c:"synced" /c:"up to date" /c:"available" /c:"synchronisiert" /c:"aktuell" /c:"verfuegbar" /c:"verfügbar" >nul
    if not errorlevel 1 (
        echo Google Drive reports: !SYNC_STATUS!
        exit /b 0
    )
    powershell.exe -NoProfile -Command "Start-Sleep -Seconds 2"
)
echo ERROR: Google Drive did not report a completed sync within !SYNC_TIMEOUT! seconds.
echo Last reported status: !SYNC_STATUS!
exit /b 1

:get_sync_status
set "SYNC_STATUS="
for /f "usebackq delims=" %%S in (`powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%SCRIPT_DIR%helpers\google_drive_sync_status.ps1" -Path "%SYNC_FILE%" 2^>nul`) do if not defined SYNC_STATUS set "SYNC_STATUS=%%S"
exit /b 0
