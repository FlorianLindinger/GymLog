# Windows Helper Scripts

Run these commands from the repository root. Each script resolves the root
automatically, so it can also be launched from another working directory.

## Build and verification

- `build.bat` builds a debug APK and copies it to
  `app\artifacts\debug\app-debug.apk`.
- `verify.bat` compiles, tests, lints, assembles, and checks Git whitespace.
- `release\release_minor.bat` changes `x.y` to `x.(y+1)`, commits, tags, and
  pushes a release request to GitHub Actions.
- `release\release_major.bat` changes `x.y` to `(x+1).0` and performs the same
  flow.
- `release\release_no_bump.bat` releases the version already in Gradle. All
  three require a clean `main` branch. GitHub Actions verifies, signs, and
  publishes the release.

## Phone and debugging tools

- `build_and_install_via_usb_or_wifi.bat [SERIAL]` builds, installs, and launches
  GymLog on an attached Android phone.
- `install_via_usb_or_wifi.bat [SERIAL]` installs the existing debug APK.
- `device_check.bat [SERIAL]` reports the connected device and installed
  GymLog version.
- `launch_and_stream_gym_log_logs.bat [SERIAL]` clears the phone's existing
  logcat buffer, force-stops and relaunches GymLog, then continuously displays
  messages from only the new GymLog process. Press `Ctrl+C` to stop. If several
  devices are attached, pass the serial shown by `adb devices -l`.
- `screenshot.bat [SERIAL]` writes a device screenshot under `app\screenshots\`.

## Internal helpers

The `helpers` directory contains shared implementation and is not intended to
be run directly:

- `helpers\android_env.bat` locates Java, the Android SDK, and ADB, and keeps
  disposable Gradle output outside the repository.
- helpers\release.bat builds, signs, and tags an explicit release version.
- helpers\release_version.ps1 implements the shared version bump, copy, and push flow.
- `helpers\google_drive_sync_status.ps1` performs a short, bounded query for a
  Windows per-file sync status.

Keeping these implementation files in `helpers` leaves the main scripts list
focused on commands intended for direct use. `gradlew.bat` remains in `app\`,
the Android Gradle project root.

Finished local builds are written under `app\artifacts\`. Disposable Gradle
intermediates remain under `%LOCALAPPDATA%\GymLog\build\app` by default.