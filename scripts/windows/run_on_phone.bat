@echo off
setlocal
set "SCRIPT_DIR=%~dp0"
for %%I in ("%SCRIPT_DIR%..\..") do set "REPO_ROOT=%%~fI"
cd /d "%REPO_ROOT%"

echo Building, installing, and launching GymLog...
call "%SCRIPT_DIR%build.bat"
if errorlevel 1 exit /b 1

call "%SCRIPT_DIR%install_via_usb.bat" %1
exit /b %errorlevel%
