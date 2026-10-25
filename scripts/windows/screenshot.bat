@echo off
setlocal
"%SystemRoot%\System32\WindowsPowerShell\v1.0\powershell.exe" -NoProfile -ExecutionPolicy Bypass -File "%~dp0helpers\android.ps1" -Command screenshot %*
exit /b %errorlevel%
