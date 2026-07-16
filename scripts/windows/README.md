# Windows Helper Scripts

Run these commands from the repository root. Every script resolves the root automatically, so invoking one from another working directory also works.

- `scripts\windows\build.bat` builds the debug APK.
- `scripts\windows\verify.bat` compiles, tests, lints, assembles, and checks Git whitespace.
- `scripts\windows\run_on_phone.bat [SERIAL]` builds, installs, and launches GymLog.
- `scripts\windows\install_via_usb.bat [SERIAL]` installs the existing debug APK.
- `scripts\windows\device_check.bat [SERIAL]` reports device and installed-app versions.
- `scripts\windows\logcat.bat [SERIAL]` follows logs for the GymLog process.
- `scripts\windows\screenshot.bat [SERIAL]` writes a device screenshot under `screenshots\`.
- `scripts\windows\release.bat VERSION` verifies and produces signed APK/AAB artifacts and checksums.

`_android_env.bat` is shared implementation and is not intended to be run directly. `gradlew.bat` remains in the repository root because that is the standard Gradle wrapper layout.
