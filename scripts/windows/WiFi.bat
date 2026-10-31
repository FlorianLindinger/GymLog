@echo off
setlocal
set "GYMLOG_ACTION=build-install"
set "GYMLOG_TRANSPORT=WiFi"
set "GYMLOG_RUNNER=android"
set "GYMLOG_BUMP="
set "GYMLOG_TITLE=Wi-Fi - Build and install"
call "%~dp0helpers\launch.bat" %*
exit /b %errorlevel%
