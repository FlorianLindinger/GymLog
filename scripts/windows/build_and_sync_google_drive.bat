@echo off
setlocal EnableExtensions

rem Build a fresh debug APK, then publish it with the sync-only helper.
set "SCRIPT_DIR=%~dp0"

echo Building a fresh GymLog debug APK before Google Drive publishing...
call "%SCRIPT_DIR%build.bat"
if errorlevel 1 exit /b 1

call "%SCRIPT_DIR%sync_google_drive.bat"
exit /b %errorlevel%
