@echo off
setlocal
set "GYMLOG_ACTION=release"
set "GYMLOG_TRANSPORT=USB"
set "GYMLOG_RUNNER=release"
set "GYMLOG_BUMP=major"
set "GYMLOG_TITLE=Release - major"
call "%~dp0..\..\helpers\launch.bat" %*
exit /b %errorlevel%
