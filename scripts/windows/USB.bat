@echo off
setlocal
set "GYMLOG_ACTION=build-install"
set "GYMLOG_TRANSPORT=USB"
set "GYMLOG_RUNNER=android"
set "GYMLOG_BUMP="
set "GYMLOG_TITLE=USB - Build and install"
call "%~dp0helpers\launch.bat" %*
exit /b %errorlevel%
