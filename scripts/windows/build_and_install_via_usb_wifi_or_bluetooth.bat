@echo off
setlocal
title GymLog - Build and install via USB, Wi-Fi or Bluetooth
echo GymLog - Build and install via USB, Wi-Fi or Bluetooth
call "%~dp0build.bat"
if errorlevel 1 exit /b 1
call "%~dp0install_via_usb_wifi_or_bluetooth.bat" %*
exit /b %errorlevel%
