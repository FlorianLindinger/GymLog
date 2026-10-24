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

- `build_and_install_via_usb_wifi_or_bluetooth.bat [SERIAL]` builds, installs, and launches
  GymLog on an attached Android phone.
- `install_via_usb_wifi_or_bluetooth.bat [SERIAL]` installs the existing debug APK.
- Add `-Bluetooth` instead of a serial to either installer for a guided Bluetooth APK transfer. `install_via_bluetooth.bat` and `build_and_install_via_bluetooth.bat` are shortcuts for the same option.
- `device_check.bat [SERIAL]` reports the connected device and installed
  GymLog version.
- `launch_and_stream_gym_log_logs.bat [SERIAL]` clears the phone's existing
  logcat buffer, force-stops and relaunches GymLog, then continuously displays
  messages from only the new GymLog process. Press `Ctrl+C` to stop. If several
  devices are attached, pass the serial shown by `adb devices -l`.
- `screenshot.bat [SERIAL]` writes a device screenshot under `app\screenshots\`.

## Bluetooth transfer and phone installation

The primary commands and window titles now include USB, Wi-Fi and Bluetooth. They prefer an authorized USB/local device, then a connected Wi-Fi device, then automatically open Bluetooth transfer when neither is usable. Wi-Fi must already be paired/connected. Old `install_via_usb_or_wifi.bat` and `build_and_install_via_usb_or_wifi.bat` names remain compatible shortcuts. An explicit serial, ambiguous device selection, or APK install failure stops without switching targets. Use `-NoBluetoothFallback` to require ADB.

From `scripts\windows`, use either:

```bat
install_via_usb_wifi_or_bluetooth.bat -Bluetooth
build_and_install_via_usb_wifi_or_bluetooth.bat -Bluetooth
```

The first sends the existing `app\artifacts\debug\app-debug.apk`; the second builds it first. Omit `-Bluetooth` to try USB/Wi-Fi first and use Bluetooth as the third fallback. Install-only Bluetooth transfer needs no JDK, Android SDK or USB debugging. Keep Bluetooth enabled and pair the phone with the PC in Windows Settings.

The helper opens Windows **Bluetooth File Transfer**. Select **Send files**, select the phone, then **Browse** and paste the APK path printed by the helper. Accept the transfer on the phone, open the received APK and confirm **Install**. Android may ask you to allow that file manager to install apps.

Bluetooth is file transfer followed by manual installation on the phone, not an ADB connection. The helper reports that the wizard opened; it cannot confirm delivery, installation or launching. USB/Wi-Fi keep the existing automatic install/launch behavior. If the phone refuses APK files over Bluetooth, use USB/Wi-Fi or another manual transfer method. A signing-key mismatch still prevents an update; keep the existing app installed to preserve workout data.

See [Microsoft's Bluetooth file-transfer instructions](https://support.microsoft.com/en-us/windows/hardware/bluetooth/send-and-receive-files-over-bluetooth-in-windows) and [Android ADB connection methods](https://developer.android.com/tools/adb).

Test without opening a window or contacting a phone:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\windows\tests\bluetooth_apk.Tests.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\windows\tests\install_connection.Tests.ps1
```

## Internal helpers

The `helpers` directory contains shared implementation and is not intended to
be run directly:

- `helpers\android_env.bat` locates Java, the Android SDK, and ADB, and keeps
  disposable Gradle output outside the repository.
- `helpers\bluetooth_apk.ps1` validates the APK and opens the visible Windows Bluetooth file-transfer wizard.
- `helpers\install.ps1` implements installation and `helpers\adb_devices.ps1` shares device selection and USB/Wi-Fi/Bluetooth fallback policy.
- helpers\release.bat builds, signs, and tags an explicit release version.
- helpers\release_version.ps1 implements the shared version bump, copy, and push flow.
- `helpers\google_drive_sync_status.ps1` performs a short, bounded query for a
  Windows per-file sync status.

Keeping these implementation files in `helpers` leaves the main scripts list
focused on commands intended for direct use. `gradlew.bat` remains in `app\`,
the Android Gradle project root.

Finished local builds are written under `app\artifacts\`. Disposable Gradle
intermediates remain under `%LOCALAPPDATA%\GymLog\build\app` by default.
