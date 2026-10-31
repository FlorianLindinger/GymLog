@echo off
setlocal
set "GYMLOG_ACTION=device"
set "GYMLOG_TRANSPORT=USB"
set "GYMLOG_RUNNER=android"
set "GYMLOG_BUMP="
set "GYMLOG_TITLE=Device details"
call "%~dp0..\helpers\launch.bat" %*
exit /b %errorlevel%
