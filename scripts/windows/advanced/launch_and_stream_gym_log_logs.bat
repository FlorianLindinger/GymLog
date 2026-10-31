@echo off
setlocal
set "GYMLOG_ACTION=logs"
set "GYMLOG_TRANSPORT=USB"
set "GYMLOG_RUNNER=android"
set "GYMLOG_BUMP="
set "GYMLOG_TITLE=App logs"
call "%~dp0..\helpers\launch.bat" %*
exit /b %errorlevel%
