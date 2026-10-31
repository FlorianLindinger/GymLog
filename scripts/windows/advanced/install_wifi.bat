@echo off
setlocal
set "GYMLOG_ACTION=install"
set "GYMLOG_TRANSPORT=WiFi"
set "GYMLOG_RUNNER=android"
set "GYMLOG_BUMP="
set "GYMLOG_TITLE=Wi-Fi - Install existing APK"
call "%~dp0..\helpers\launch.bat" %*
exit /b %errorlevel%
