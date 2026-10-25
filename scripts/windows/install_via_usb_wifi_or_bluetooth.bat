@echo off
setlocal
title GymLog - Install via USB, Wi-Fi or Bluetooth
"%SystemRoot%\System32\WindowsPowerShell\v1.0\powershell.exe" -STA -NoProfile -ExecutionPolicy Bypass -File "%~dp0helpers\android.ps1" -Command install %*
set "GYMLOG_INSTALL_RESULT=%errorlevel%"
if /i "%GYMLOG_NO_PAUSE%"=="1" exit /b %GYMLOG_INSTALL_RESULT%
echo.
echo Command finished with exit code %GYMLOG_INSTALL_RESULT%.
echo This window stays open. Finish the transfer and installation, then close it.
"%ComSpec%" /d /k
exit /b %GYMLOG_INSTALL_RESULT%
