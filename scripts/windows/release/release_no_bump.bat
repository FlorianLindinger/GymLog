@echo off
setlocal
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0..\helpers\release_version.ps1" -Bump none
set "RESULT=%errorlevel%"
echo.
pause
exit /b %RESULT%