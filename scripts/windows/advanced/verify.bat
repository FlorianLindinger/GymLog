@echo off
setlocal
set "GYMLOG_ACTION=verify"
set "GYMLOG_TRANSPORT=USB"
set "GYMLOG_RUNNER=android"
set "GYMLOG_BUMP="
set "GYMLOG_TITLE=Verify project"
call "%~dp0..\helpers\launch.bat" %*
exit /b %errorlevel%
