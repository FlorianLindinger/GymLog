@echo off
setlocal
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0release_version.ps1" -Bump minor
exit /b %errorlevel%