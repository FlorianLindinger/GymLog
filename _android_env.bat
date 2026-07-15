@echo off
rem Shared Android/JDK discovery for GymLog helper scripts.

if not defined JAVA_HOME (
    if exist "C:\Program Files\Java\jdk-21.0.10\bin\java.exe" (
        set "JAVA_HOME=C:\Program Files\Java\jdk-21.0.10"
    ) else if exist "C:\Program Files\Android\Android Studio\jbr\bin\java.exe" (
        set "JAVA_HOME=C:\Program Files\Android\Android Studio\jbr"
    )
)

if not defined ANDROID_HOME (
    if exist "%LOCALAPPDATA%\Android\Sdk" set "ANDROID_HOME=%LOCALAPPDATA%\Android\Sdk"
)

if not exist "%JAVA_HOME%\bin\java.exe" (
    echo ERROR: A compatible Java installation was not found.
    echo Install Android Studio or set JAVA_HOME to JDK 17 or newer.
    exit /b 1
)

if not exist "%ANDROID_HOME%\platforms" (
    echo ERROR: The Android SDK was not found.
    echo Open Android Studio's SDK Manager or set ANDROID_HOME.
    exit /b 1
)

set "GYMLOG_ADB=%ANDROID_HOME%\platform-tools\adb.exe"
if not exist "%GYMLOG_ADB%" (
    echo ERROR: Android Debug Bridge was not found.
    echo Install Android SDK Platform-Tools through Android Studio.
    exit /b 1
)
set "PATH=%ANDROID_HOME%\platform-tools;%PATH%"

exit /b 0
