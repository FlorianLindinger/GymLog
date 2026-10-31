@echo off
setlocal
call "%~dp0build_and_install_via_usb_wifi_or_bluetooth.bat" %*
exit /b %errorlevel%
