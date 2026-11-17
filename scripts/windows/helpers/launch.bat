@echo off
setlocal
title GymLog - %GYMLOG_TITLE%
color 0B
echo.
echo [START] GymLog - %GYMLOG_TITLE%
echo.
if /i "%GYMLOG_RUNNER%"=="release" (
    "%SystemRoot%\System32\WindowsPowerShell\v1.0\powershell.exe" -STA -NoProfile -ExecutionPolicy Bypass -File "%~dp0release_version.ps1" -Bump "%GYMLOG_BUMP%" %*
) else (
    "%SystemRoot%\System32\WindowsPowerShell\v1.0\powershell.exe" -STA -NoProfile -ExecutionPolicy Bypass -File "%~dp0android.ps1" -Command "%GYMLOG_ACTION%" -Transport "%GYMLOG_TRANSPORT%" %*
)
set "GYMLOG_RESULT=%errorlevel%"
echo.
if not "%GYMLOG_RESULT%"=="0" (
    color 0C
    echo [ERROR] Command failed with exit code %GYMLOG_RESULT%. See the message above.
) else if /i "%GYMLOG_TRANSPORT%"=="Bluetooth" (
    color 0E
    echo [ACTION REQUIRED] APK ready. Complete Bluetooth transfer and installation on the phone.
) else if /i "%GYMLOG_TRANSPORT%"=="GoogleDrive" (
    color 0E
    echo [ACTION REQUIRED] APK copied. Wait for Google Drive sync, then open and install it on the phone.
) else (
    color 0A
    echo [SUCCESS] Command completed successfully.
)
if /i "%GYMLOG_NO_PAUSE%"=="1" exit /b %GYMLOG_RESULT%
echo.
echo This window stays open. Review the messages, then type exit or close it.
"%ComSpec%" /d /k
exit /b %GYMLOG_RESULT%
