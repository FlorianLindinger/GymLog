@echo off
setlocal
set "GYMLOG_ACTION=install"
set "GYMLOG_TRANSPORT=USB"
set "GYMLOG_RUNNER=android"
set "GYMLOG_BUMP="
set "GYMLOG_TITLE=USB - Install existing APK"
call "%~dp0..\helpers\launch.bat" %*
exit /b %errorlevel%
