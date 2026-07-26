@echo off
setlocal
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0..\helpers\release_version.ps1" -Bump major
set "RESULT=%errorlevel%"
echo.
pause
exit /b %RESULT%