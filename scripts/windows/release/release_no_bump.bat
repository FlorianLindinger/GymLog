@echo off
setlocal
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0release_version.ps1" -Bump none
exit /b %errorlevel%