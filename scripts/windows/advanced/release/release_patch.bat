@echo off
setlocal
set "GYMLOG_ACTION=release"
set "GYMLOG_TRANSPORT=USB"
set "GYMLOG_RUNNER=release"
set "GYMLOG_BUMP=patch"
set "GYMLOG_TITLE=Release - patch"
call "%~dp0..\..\helpers\launch.bat" %*
exit /b %errorlevel%
