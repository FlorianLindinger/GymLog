@echo off
setlocal
set "GYMLOG_ACTION=build-install"
set "GYMLOG_TRANSPORT=Bluetooth"
set "GYMLOG_RUNNER=android"
set "GYMLOG_BUMP="
set "GYMLOG_TITLE=Bluetooth - Build and send"
call "%~dp0helpers\launch.bat" %*
exit /b %errorlevel%
