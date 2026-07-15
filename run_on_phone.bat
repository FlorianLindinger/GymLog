@echo off
setlocal
cd /d "%~dp0"

echo Building, installing, and launching GymLog...
call build.bat
if errorlevel 1 exit /b 1

call install_via_usb.bat %1
exit /b %errorlevel%
