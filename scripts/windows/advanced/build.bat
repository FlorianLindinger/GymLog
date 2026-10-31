@echo off
setlocal
set "GYMLOG_ACTION=build"
set "GYMLOG_TRANSPORT=USB"
set "GYMLOG_RUNNER=android"
set "GYMLOG_BUMP="
set "GYMLOG_TITLE=Build APK"
call "%~dp0..\helpers\launch.bat" %*
exit /b %errorlevel%
