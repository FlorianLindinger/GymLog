@echo off
setlocal
title GymLog - Install via USB, Wi-Fi or Bluetooth
"%SystemRoot%\System32\WindowsPowerShell\v1.0\powershell.exe" -NoProfile -ExecutionPolicy Bypass -File "%~dp0helpers\install.ps1" %*
exit /b %errorlevel%
