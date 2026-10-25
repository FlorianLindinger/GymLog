@echo off
setlocal
"%SystemRoot%\System32\WindowsPowerShell\v1.0\powershell.exe" -NoProfile -ExecutionPolicy Bypass -File "%~dp0..\helpers\release_version.ps1" -Bump patch %*
exit /b %errorlevel%
