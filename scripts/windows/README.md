# Windows launchers

Choose one of the four everyday launchers in the project root. Each builds the latest
APK (reusing it when build inputs are unchanged) and uses exactly the connection type in its name. There is no automatic
USB/Wi-Fi/Bluetooth fallback.

| Launcher | Action |
| --- | --- |
| `build & install via USB.bat` | Check the USB connection, build, install the update, and launch GymLog |
| `build & install via WiFi.bat` | Check the Wi-Fi connection, build, install the update, and launch GymLog |
| `build & install via Bluetooth.bat` | Build and open Bluetooth File Transfer; finish installation on the phone |
| `build & install via Google Drive.bat` | Build and copy the APK to a chosen Drive sync folder; download/install on the phone |

Double-click a root launcher, or run its quoted path from any directory. In PowerShell, use `& "PATH.bat"` for names containing spaces and `&`. Every launcher keeps its
window open after success or failure. Review the messages, then close the window
or type `exit`. For automation, set `GYMLOG_NO_PAUSE=1`; exit codes are preserved.

Colors: **cyan** = progress, **green** = success, **red** = error, **yellow** =
action required. Bluetooth and Google Drive remain yellow because local dispatch
does not confirm that the APK was delivered or installed.

## Google Drive

Run `build & install via Google Drive.bat` and select a folder synchronized by Google Drive for desktop.
Your last folder is remembered: the script asks whether to use it again. Press Enter to keep it,
or type n and Enter to pick another folder (the picker then preselects the old one).
The choice is remembered locally in the Git-ignored `.tools/google-drive-folder.txt`.
The destination is checked before building. Exports are numbered `GymLog-1.apk`,
`GymLog-2.apk`, and so on, continuing after the highest existing number without
overwriting previous versions. Older timestamp-named exports are also preserved.
To skip the folder picker, pass a folder or set `GYMLOG_DRIVE_FOLDER`:

```powershell
& '.\build & install via Google Drive.bat' -DriveFolder 'G:\My Drive\GymLog'
```

Wait for the Drive client to finish uploading. On the phone, open Google Drive,
download the APK and confirm installation. The helper copies locally; it does not
verify cloud upload, create public links, or remotely install the app. Choose a
folder already synchronized by your Drive client. No ADB connection is needed.

## USB

If Android reports a conflict with an existing GymLog package, check that the new
APK uses the same signing key as the installed app. Debug keys normally differ
between Windows accounts. To reuse the original debug key, add its path to the
Git-ignored `app/local.properties` (relative paths start at `app`):

```properties
gymlog.debug.keystore=C\:/Users/USERNAME/.android/debug.keystore
```

Use the existing key; a newly generated one cannot update the old installation.
Keep the key outside Git. Uninstalling the app deletes local training data, so
prefer rebuilding with the matching key. This setting applies to debug APKs;
release signing uses the separate release configuration.

Enable Developer options and USB debugging, connect a USB data cable, unlock the
phone, and accept its debugging authorization prompt. Then run `build & install via USB.bat`.
USB ignores wireless connections and emulators. If several USB phones are
connected, select one explicitly:

```powershell
& ".\build & install via USB.bat" -Serial PHONE_SERIAL
```

An unavailable or unauthorized USB phone produces an error, even if Wi-Fi is
connected. Existing app data is preserved; the helper never uninstalls GymLog.

## Wi-Fi

Enable Android Wireless debugging on the phone. Pairing and connection ports are
different. Connect the PC and phone to the same Wi-Fi network, then double-click
`build & install via WiFi.bat`. If no authorized wireless device is connected,
the launcher guides you through pairing and connecting before building. Keep
the pairing screen open while entering its address and code, then use the
connection address from the main Wireless debugging screen. For an already-paired
phone, leave the pairing address blank. Leave the connection address blank to cancel.

You can also supply the addresses shown on the phone:

```powershell
& ".\build & install via WiFi.bat" -PairAddress 192.168.1.7:PAIRING_PORT -ConnectAddress 192.168.1.7:CONNECTION_PORT
```

Replace both port placeholders with the displayed numbers. Enter the pairing
code at ADB's prompt; the helpers do not save it. For an already-paired phone,
use only `-ConnectAddress`. Once connected, `build & install via WiFi.bat` needs no arguments.
If multiple wireless connections are listed, use `-Serial IP:PORT` or the exact
mDNS serial. Wi-Fi never selects a USB phone.
Explicit addresses/serials, redirected input, and `GYMLOG_NO_PAUSE=1` do not trigger
the automatic setup prompt; automation must supply a connection or use an existing one.

For pairing/connecting without a build, double-click `advanced/connect_wifi.bat`
and follow its prompts, or supply the same address arguments.

## Bluetooth

Pair the phone with Windows, then run `build & install via Bluetooth.bat`. The helper copies the APK
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
| `advanced/build.bat` | Build only; export APK to the root and checksum to artifacts/checksums |
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

Temporary build output defaults to the ignored `.tools/b/` folder on the project drive (required by Android resource-backed unit tests), overridden
by `GYMLOG_BUILD_DIR`. All Windows build modes export their APK to the project
root: `GymLog-debug.apk` for debug/verification/version preparation and
`GymLog-release.apk` for signed release builds, with `.apk.sha256` checksums under
`artifacts/checksums/`. USB, Wi-Fi, Bluetooth and install-only commands use the root debug APK by
default; `-Apk` still selects a different file. Versioned signed APK/AAB packages
remain under `app/artifacts/release/`.

Debug builds compare source/configuration contents and toolchain settings with the
last successful build and validate the copied APK's SHA-256 before reusing it.
Added, removed, renamed or edited inputs trigger Gradle; documentation and generated
artifacts do not. Missing/modified APKs or unreadable cache state also rebuild.
Use `-ForceBuild` with `build & install via USB.bat`, `build & install via WiFi.bat`, `build & install via Bluetooth.bat` or
`advanced/build.bat` to invoke Gradle even when unchanged. Installation/transfer
still runs when a build is skipped. A reused APK retains its original build time.
Verification and release commands always run their full workflow.
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
