@echo off
setlocal
set "GYMLOG_ACTION=wifi"
set "GYMLOG_TRANSPORT=WiFi"
set "GYMLOG_RUNNER=android"
set "GYMLOG_BUMP="
set "GYMLOG_TITLE=Pair or connect Wi-Fi"
call "%~dp0..\helpers\launch.bat" %*
exit /b %errorlevel%
