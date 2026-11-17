@echo off
setlocal
set "GYMLOG_ACTION=build-install"
set "GYMLOG_TRANSPORT=GoogleDrive"
set "GYMLOG_RUNNER=android"
set "GYMLOG_BUMP="
set "GYMLOG_TITLE=Google Drive - Build and send APK"
call "%~dp0scripts\windows\helpers\launch.bat" %*
exit /b %errorlevel%
