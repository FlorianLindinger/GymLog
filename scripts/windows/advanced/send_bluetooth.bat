@echo off
setlocal
set "GYMLOG_ACTION=install"
set "GYMLOG_TRANSPORT=Bluetooth"
set "GYMLOG_RUNNER=android"
set "GYMLOG_BUMP="
set "GYMLOG_TITLE=Bluetooth - Send existing APK"
call "%~dp0..\helpers\launch.bat" %*
exit /b %errorlevel%
