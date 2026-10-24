@echo off
setlocal
call "%~dp0install_via_usb_wifi_or_bluetooth.bat" -Bluetooth %*
exit /b %errorlevel%
