# Windows launchers

Choose one of the three everyday launchers in this folder. Each builds the latest
APK and uses exactly the connection type in its name. There is no automatic
USB/Wi-Fi/Bluetooth fallback.

| Launcher | Action |
| --- | --- |
| `USB.bat` | Check the USB connection, build, install the update, and launch GymLog |
| `WiFi.bat` | Check the Wi-Fi connection, build, install the update, and launch GymLog |
| `Bluetooth.bat` | Build and open Bluetooth File Transfer; finish installation on the phone |

Double-click a launcher, or run it from any directory. Every launcher keeps its
window open after success or failure. Review the messages, then close the window
or type `exit`. For automation, set `GYMLOG_NO_PAUSE=1`; exit codes are preserved.

Colors: **cyan** = progress, **green** = success, **red** = error, **yellow** =
action required. Bluetooth remains yellow because opening the wizard does not
confirm that the APK was delivered or installed.

## USB

Enable Developer options and USB debugging, connect a USB data cable, unlock the
phone, and accept its debugging authorization prompt. Then run `USB.bat`.
USB ignores wireless connections and emulators. If several USB phones are
connected, select one explicitly:

```powershell
.\USB.bat -Serial PHONE_SERIAL
```

An unavailable or unauthorized USB phone produces an error, even if Wi-Fi is
connected. Existing app data is preserved; the helper never uninstalls GymLog.

## Wi-Fi

Enable Android Wireless debugging on the phone. Pairing and connection ports are
different; use the addresses shown on the phone:

```powershell
.\WiFi.bat -PairAddress 192.168.1.7:PAIRING_PORT -ConnectAddress 192.168.1.7:CONNECTION_PORT
```

Replace both port placeholders with the displayed numbers. Enter the pairing
code at ADB's prompt; the helpers do not save it. For an already-paired phone,
use only `-ConnectAddress`. Once connected, `WiFi.bat` needs no arguments.
If multiple wireless connections are listed, use `-Serial IP:PORT` or the exact
mDNS serial. Wi-Fi never selects a USB phone.

For pairing/connecting without a build, double-click `advanced/connect_wifi.bat`
and follow its prompts, or supply the same address arguments.

## Bluetooth

Pair the phone with Windows, then run `Bluetooth.bat`. The helper copies the APK
path to the clipboard and opens Windows Bluetooth File Transfer. Choose **Send
files**, select the phone, click **Browse**, and paste the path with **Ctrl+V**.
If clipboard copying fails, copy the printed path manually.

Accept the transfer on the phone, open the APK, and confirm installation. Allow
the receiving file manager to install apps if Android asks. Keep GymLog installed
to preserve its data. Transfer and installation cannot be confirmed by this helper.
Bluetooth does not use ADB or USB debugging. Sending an existing APK requires no
JDK or Android SDK: use `advanced/send_bluetooth.bat`.

## Advanced commands

Less frequently needed launchers are in `advanced/`, and release tools are in
`advanced/release/`. All use the same colors and persistent result windows.

| Command | Action |
| --- | --- |
| `advanced/build.bat` | Build only; copy APK and SHA-256 checksum to `app/artifacts/debug/` |
| `advanced/install_usb.bat [-Serial SERIAL] [-Apk "PATH.apk"]` | Install an existing APK over USB |
| `advanced/install_wifi.bat [-Serial SERIAL] [-Apk "PATH.apk"]` | Install an existing APK over Wi-Fi; also accepts pairing/connection addresses |
| `advanced/send_bluetooth.bat [-Apk "PATH.apk"]` | Send an existing APK over Bluetooth |
| `advanced/connect_wifi.bat` | Pair/connect Wi-Fi without building or installing |
| `advanced/device_check.bat [SERIAL]` | Show Android/API and installed GymLog versions |
| `advanced/launch_and_stream_gym_log_logs.bat [SERIAL]` | Launch GymLog and stream its process logs; Ctrl+C stops streaming |
| `advanced/screenshot.bat [SERIAL]` | Save a validated PNG in `app/screenshots/` |
| `advanced/verify.bat` | Run helper tests, Android compilation/unit tests/lint/build, and Git whitespace checks |
| `advanced/release/build_release.bat` | Build and verify signed APK/AAB files and checksums |
| `advanced/release/release_patch.bat` | Prepare and verify a patch version locally |
| `advanced/release/release_minor.bat` | Prepare and verify a minor version locally |
| `advanced/release/release_major.bat` | Prepare and verify a major version locally |
| `advanced/release/release_no_bump.bat -Publish` | Explicitly publish a reviewed release from clean main |

Diagnostic commands can inspect either ADB transport; pass a serial if more than
one connection is available. Log streaming preserves logcat history and does not
force-stop the app. The old combined launchers are preserved as inactive `.bat.txt`
sources in `archive/`, not as executable alternatives.

## Build requirements and releases

Builds require JDK 17 or 21 and Android SDK Platform 36. Discovery supports
`GYMLOG_JAVA_HOME`, `JAVA_HOME`, `STUDIO_JDK`, Android Studio registration and
standard locations. SDK discovery supports `ANDROID_HOME`, `ANDROID_SDK_ROOT`,
`app/local.properties` and standard SDK locations. ADB-only commands require only
Platform-Tools, also discoverable via `GYMLOG_ADB` or PATH.

Temporary build output defaults to `%LOCALAPPDATA%/GymLog/build/app`, overridden
by `GYMLOG_BUILD_DIR`. The APK remains `app/artifacts/debug/app-debug.apk`.
Execution policy changes apply only to the child PowerShell process.

Release preparation requires clean `main` and verifies locally without publishing.
Review the version and changelog, commit, then explicitly use `-Publish` when ready.
Publication atomically pushes main and its annotated tag. For signed builds, set
`GYMLOG_KEYSTORE_FILE`, `GYMLOG_KEYSTORE_PASSWORD`, `GYMLOG_KEY_ALIAS`, and
`GYMLOG_KEY_PASSWORD`; keep signing values out of the repository.

## Helper tests

These tests use temporary fixtures, mocked ADB/builds and console subprocesses;
they do not contact a phone, open the Bluetooth wizard, publish, or change the
clipboard:

```powershell
Get-ChildItem scripts/windows/tests -Filter '*.Tests.ps1' | ForEach-Object {
    powershell -STA -NoProfile -ExecutionPolicy Bypass -File $_.FullName
    if ($LASTEXITCODE -ne 0) { throw "Helper test failed: $($_.Name)" }
}
```
