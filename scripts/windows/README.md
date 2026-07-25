# Windows Helper Scripts

Run these commands from the repository root. Each script resolves the root
automatically, so it can also be launched from another working directory.

## Build and verification

- `build.bat` builds a debug APK and copies it to
  `artifacts\debug\app-debug.apk`.
- `verify.bat` compiles, tests, lints, assembles, and checks Git whitespace.
- `release.bat VERSION` verifies the project and produces signed APK/AAB
  artifacts and checksums.

## Google Drive publishing

- `build_and_sync_google_drive.bat` builds a fresh debug APK and then runs the
  sync-only script.
- `sync_google_drive.bat` does not compile. It copies the existing
  `artifacts\debug\app-debug.apk` to
  `google_drive_synced\app-debug.apk`, verifies the copied bytes, and waits for
  a sync result when Google Drive exposes one.

Both Drive scripts start Google Drive for desktop when it is not already
running. If no per-file sync state is available from Windows, the sync script
waits briefly for pickup and directs you to the Drive tray app for final cloud
confirmation.

## Phone and debugging tools

- `build_and_install_via_usb.bat [SERIAL]` builds, installs, and launches
  GymLog on an attached Android phone.
- `install_via_usb.bat [SERIAL]` installs the existing debug APK.
- `device_check.bat [SERIAL]` reports the connected device and installed
  GymLog version.
- `launch_and_stream_gym_log_logs.bat [SERIAL]` clears the phone's existing
  logcat buffer, force-stops and relaunches GymLog, then continuously displays
  messages from only the new GymLog process. Press `Ctrl+C` to stop. If several
  devices are attached, pass the serial shown by `adb devices -l`.
- `screenshot.bat [SERIAL]` writes a device screenshot under `screenshots\`.

## Internal helpers

The `helpers` directory contains shared implementation and is not intended to
be run directly:

- `helpers\android_env.bat` locates Java, the Android SDK, and ADB, and keeps
  disposable Gradle output outside the repository.
- `helpers\google_drive_sync_status.ps1` performs a short, bounded query for a
  Windows per-file sync status.

Keeping these implementation files in `helpers` leaves the main scripts list
focused on commands intended for direct use. `gradlew.bat` remains in the
repository root because that is the standard Gradle wrapper layout.

Finished local builds are written under `artifacts\`. Disposable Gradle
intermediates remain under `%LOCALAPPDATA%\GymLog\build\app` by default.
