@echo off
setlocal
set "GYMLOG_ACTION=screenshot"
set "GYMLOG_TRANSPORT=USB"
set "GYMLOG_RUNNER=android"
set "GYMLOG_BUMP="
set "GYMLOG_TITLE=Screenshot"
call "%~dp0..\helpers\launch.bat" %*
exit /b %errorlevel%
