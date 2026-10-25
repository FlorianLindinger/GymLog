# Windows development helpers

Run from any directory, or double-click a `.bat` file. Each launcher resolves the
project from its own location and delegates to shared PowerShell helpers.
Execution policy is set only for the child process, never system-wide.

## Commands

| Command | Result |
| --- | --- |
| `build.bat` | Build a debug APK and copy it with a SHA-256 checksum into `app/artifacts/debug/` |
| `verify.bat` | Run Windows helper tests, compile, run Android unit tests and lint, assemble a debug APK, and check Git whitespace |
| `device_check.bat [SERIAL]` | Show the selected phone, Android/API version, and installed GymLog version |
| `install_via_usb_wifi_or_bluetooth.bat [SERIAL]` | Install the existing debug APK with app data preserved, then launch GymLog |
| `build_and_install_via_usb_wifi_or_bluetooth.bat [SERIAL]` | Build, install, and launch GymLog |
| `install_via_usb_wifi_or_bluetooth.bat -Serial SERIAL -Apk "PATH.apk"` | Install a specific debug or release APK |
| `install_via_usb_wifi_or_bluetooth.bat -Bluetooth` | Open Bluetooth transfer for the existing APK; finish installation on the phone |
| `build_and_install_via_usb_wifi_or_bluetooth.bat -Bluetooth` | Build first, then open Bluetooth transfer |
| `connect_wifi.bat -PairAddress IP:PAIRING_PORT -ConnectAddress IP:CONNECTION_PORT` | Pair Android 11+ wireless debugging and connect |
| `launch_and_stream_gym_log_logs.bat [SERIAL]` | Launch GymLog and stream its process logs; Ctrl+C stops logging |
| `screenshot.bat [SERIAL]` | Save a validated PNG under `app/screenshots/` |
| `build_release.bat` | Build and verify signed APK/AAB assets, then copy them and SHA-256 checksums into `app/artifacts/release/VERSION/` |
| `release/release_patch.bat` | Prepare and verify a patch version locally, adding a patch component if necessary |
| `release/release_minor.bat` | Prepare and verify a minor version locally |
| `release/release_major.bat` | Prepare and verify a major version locally |
| `release/release_no_bump.bat -Publish` | From clean `main`, verify the current version, create its tag, and atomically push main and the tag to queue GitHub Actions |

Both primary installers keep the console open after success or failure so output
and Bluetooth instructions remain visible. Close the window or type `exit` when
done. Set `GYMLOG_NO_PAUSE=1` for automation; the original command exit code is
preserved. The old `*_via_usb_or_wifi.bat` names and dedicated Bluetooth
shortcuts remain compatible aliases.

## Java, SDK, and output discovery

Use JDK 17 or 21 with Android SDK Platform 36 and Platform-Tools. Java discovery
checks `GYMLOG_JAVA_HOME`, `JAVA_HOME`, `STUDIO_JDK`, Android Studio's Windows
install registration (including custom drives), usual Studio locations, and PATH.
Unsupported Java versions are skipped. No workstation path is embedded in the
helpers.

The SDK is found through `ANDROID_HOME`, `ANDROID_SDK_ROOT`,
`app/local.properties`, an optional `.tools/android-sdk`, or Android Studio's
usual SDK directory. ADB discovery needs no JDK and also accepts `GYMLOG_ADB` or
ADB on PATH. Device, screenshot, and install-only commands do not require a
build environment.

Disposable Gradle app output stays under
`%LOCALAPPDATA%/GymLog/build/app`, overridden by `GYMLOG_BUILD_DIR`. The existing
Gradle cache and `GRADLE_USER_HOME` behavior are preserved. Copied debug APKs
remain at `app/artifacts/debug/app-debug.apk`; screenshots and artifacts are
ignored by Git.

## USB and Wi-Fi installation

`[SERIAL]` is optional. Installers prefer an authorized USB/local target, then a
connected Wi-Fi target, then Bluetooth if no usable ADB connection is available.
Wi-Fi must already be paired and connected. If the preferred group contains
several connections, pass `-Serial` explicitly; other device helpers require a
serial whenever several authorized connections are listed. A single phone may
appear twice through IP and mDNS, so select either of its listed connections.
An explicit unavailable serial, ambiguous selection, or install failure stops
without switching to Bluetooth. Use `-NoBluetoothFallback` to require ADB.

Enable Developer options and USB debugging, unlock the phone, and accept its
Allow USB debugging prompt. For Wi-Fi, use the pairing and connection addresses
shown in Android Wireless debugging; these ports differ. Enter the pairing code
directly at ADB's prompt. The helpers do not save it, uninstall GymLog, clear app
data, or change debugging permissions.

Log streaming preserves existing logcat entries and does not force-stop GymLog.
Run it again if the app process restarts.

## Bluetooth transfer

Explicit `-Bluetooth` skips ADB and cannot be combined with a serial, Wi-Fi
address, or `-NoBluetoothFallback`. Install-only Bluetooth needs no JDK, SDK, or
USB debugging. Pair the phone with Windows first.

The helper copies the exact APK path to the clipboard, verifies it, and opens
Windows Bluetooth File Transfer. Choose **Send files**, select the phone, then
choose **Browse** and paste the path with **Ctrl+V**. If clipboard copying fails,
manually copy the printed path. Accept the transfer, open the APK on the phone,
and confirm installation. Android may ask you to allow the file manager to
install apps. Keep GymLog installed to preserve workout data.

The helper confirms only that the wizard opened; delivery and installation need
to be completed on the phone. If the phone refuses APKs over Bluetooth, use
USB/Wi-Fi or another manual transfer method. Signing-key mismatches still
prevent an update.

## Releases

Version preparation requires a clean `main` branch. Patch/minor/major launchers
update `app/app/build.gradle.kts`, increment `versionCode`, and verify locally.
Review the version and `CHANGELOG.md`, then commit. Preparation does not create
a tag or push by default. Existing two-part versions remain two-part for
minor/major changes; a patch release introduces the third component.

Use `release/release_no_bump.bat -Publish` from clean `main` to publish the
reviewed version. The explicit `-Publish` option checks local and remote tags,
verifies the app, and atomically pushes main and its annotated version tag.
GitHub Actions then verifies, signs, and publishes the release. A bump launcher
can also receive `-Publish` to commit its verified version change and publish it.

For a local signed build, set `GYMLOG_KEYSTORE_FILE`, `GYMLOG_KEYSTORE_PASSWORD`,
`GYMLOG_KEY_ALIAS`, and `GYMLOG_KEY_PASSWORD`, then run `build_release.bat`.
The build verifies the APK signature and copies the APK, AAB, and checksums;
it does not create tags or push. Keep signing values out of the repository.

## Implementation and tests

`helpers/android.ps1` dispatches commands. `helpers/android_env.ps1` handles
portable environment discovery, Gradle invocation, and artifact copying;
`adb_devices.ps1` handles connection selection, `bluetooth_apk.ps1` handles the
interactive transfer, and `release_version.ps1` / `release_tools.ps1` handle
version preparation and explicit publication. Batch files contain launch logic
only. GymLog's Gradle root remains `app/`.

Run helper tests without a build, phone, Bluetooth wizard, or clipboard access:

```powershell
Get-ChildItem scripts/windows/tests -Filter '*.Tests.ps1' | ForEach-Object {
    powershell -STA -NoProfile -ExecutionPolicy Bypass -File $_.FullName
    if ($LASTEXITCODE -ne 0) { throw "Helper test failed: $($_.Name)" }
}
```
