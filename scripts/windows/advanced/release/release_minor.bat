@echo off
setlocal
set "GYMLOG_ACTION=release"
set "GYMLOG_TRANSPORT=USB"
set "GYMLOG_RUNNER=release"
set "GYMLOG_BUMP=minor"
set "GYMLOG_TITLE=Release - minor"
call "%~dp0..\..\helpers\launch.bat" %*
exit /b %errorlevel%
