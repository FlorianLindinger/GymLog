# GymLog

GymLog is a private, offline Android workout tracker built with Kotlin, Jetpack Compose, and SQLite. It supports multiple training plans, weight- and level-based exercises, detailed history management, progression warnings, and local JSON backups.

## Screenshots

| Exercise overview | Exercise details |
| --- | --- |
| <img src="docs/screenshots/overview.png" alt="GymLog exercise overview" width="320"> | <img src="docs/screenshots/exercise-detail.png" alt="GymLog exercise details" width="320"> |

| Calendar and history | Training statistics |
| --- | --- |
| <img src="docs/screenshots/history.png" alt="GymLog calendar and training history" width="320"> | <img src="docs/screenshots/statistics.png" alt="GymLog training statistics" width="320"> |

Screenshots use sample workout data.

## Requirements

- Android Studio with Android SDK 35
- JDK 21
- An Android emulator or physical Android phone

## Run it on this computer

The simplest route is Android Studio:

1. Install the current stable **Android Studio** from the Android developer website.
2. Open Android Studio and choose **Open**, then select this `GymLog` folder.
3. Let Gradle sync. If Android Studio asks to install Android SDK 35, accept it.
4. Open **Tools > Device Manager**, create a Pixel-style device, and download a recent Android system image.
5. Start the emulator, select it in the toolbar, and click **Run**.

You can also build from PowerShell after Android Studio has installed the SDK:

```powershell
$env:JAVA_HOME = "C:\Program Files\Java\jdk-21.0.10"
.\gradlew.bat :app:assembleDebug
```

The debug APK will be written to `app\build\outputs\apk\debug\app-debug.apk`.

## Development helper scripts

- `run_on_phone.bat` builds, installs, and launches GymLog on an authorized USB device.
- `install_via_usb.bat` installs and launches the existing debug APK without rebuilding it.
- `verify.bat` compiles the app, runs tests and Android lint, builds the debug APK, and checks Git whitespace.
- `device_check.bat` lists connected devices and prints Android and installed-app versions.
- `logcat.bat` restarts GymLog and follows only its current process logs.
- `screenshot.bat` saves a timestamped device screenshot under `screenshots\`.
- `release.bat VERSION` verifies and builds a signed APK and AAB, writes SHA-256 checksums, and can create a local version tag.

Device scripts accept an optional ADB serial, for example `device_check.bat R3CT...`, when multiple phones or emulators are connected.

Release signing is read only from the environment. Set `GYMLOG_KEYSTORE_FILE`, `GYMLOG_KEYSTORE_PASSWORD`, `GYMLOG_KEY_ALIAS`, and `GYMLOG_KEY_PASSWORD`; never commit a keystore or its passwords. Official verification also runs automatically on pushes and pull requests through GitHub Actions.

## Install it on an Android phone

### Directly from Android Studio

1. Enable **Developer options** by tapping **Build number** seven times in **Settings > About phone**.
2. Enable **USB debugging** in Developer options.
3. Connect the phone with a data-capable USB cable and approve the debugging prompt.
4. Select the phone in Android Studio and click **Run**.

### From the APK

1. Build `app-debug.apk` with the command above.
2. Copy the APK to the phone.
3. Open it and allow **Install unknown apps** for the file manager or browser you used.
4. Confirm installation.

If Android platform tools are installed and USB debugging is enabled, you can also run:

```powershell
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

## Backups

From the overview, open **Menu** and choose the backup action. **Download backup** creates a readable JSON file through Android's system file picker. **Upload and restore backup** replaces the app's current data after confirmation.

Backups include every training plan, exercise, order, note, body-area assignment, recorded set, metric change, color, and progression-warning setting.

## Privacy

GymLog stores its data locally on the device. It does not require an account, analytics service, or internet permission. Exported backup files remain under the user's control.

## Features

- Multiple independent training plans
  - Create, open, rename, and delete plans from the overview menu
  - Separate exercises, ordering, history, statistics, timers, and progression data per plan
- Reorderable exercise overview (long-press and drag) with custom exercise names and optional title notes
- Two exercise tracking modes
  - Weight in kilograms
  - Machine or resistance level, with each training recorded in minutes
- Quick workout logging
  - Log up to five weight-based sets at once with 0–15 rep wheels
  - Reuse the previous rep pattern
  - Log Level trainings directly by duration in minutes
  - Quick-repeat recent rep amounts
- Compact overview cards
  - Current kg or level value
  - Tap the value to change it
  - Choose whether a metric change applies from the next session or also rewrites the current six-hour session
  - Recent rep batches such as `Last: 10,10,10 | 12,10,10`
  - Color-coded session trend borders
  - Configurable progression warnings
- Compact exercise detail screens
  - Notes shown at the top
  - Tap the current metric card to change its value
  - Started, current, and highest metric statistics
  - Configurable chart windows and trailing averages
  - Combined weight, volume, and session-rep charts
- Grouped exercise history entries such as `80 kg - 12,12,10,10 - Date`
  - Expand a batch to edit or delete an individual set
  - Delete a complete logged batch
- Full history management
  - Edit reps, recorded kg, or training minutes
  - Delete individual entries with confirmation
  - Delete every entry for one exercise on a selected day
  - Delete an entire training day
- Calendar and session history
  - Daily activity intensity
  - Recent session summaries
  - Per-day exercise totals
  - First-entry-to-last-entry training duration
- Training-duration tracking
  - Today's duration displayed on the overview in `h:mm`
  - First and last entry times
  - Duration recorded in history and used in statistics
- Statistics with selectable 1-week, 1-month, 3-month, 6-month, 1-year, 2-year, 3-year, and all-time windows
  - Gym days per week, session count, and set count
  - Average and longest training duration
  - Session-frequency and daily-duration plots
  - Weight increases and increases per week
  - Exercises progressed
  - Average upgrades per gym day and per month
  - Weekly, per-gym-day, and monthly upgrade plots
  - Per-exercise weight-increase frequency
- Progression warnings for weight-based exercises
  - Warn after a configurable number of weeks without an increase
  - Warn after a configurable number of training days without an increase
  - Set either threshold to zero to disable it
- Simplified front/back body-area picker designed around gym machines
  - Chest, upper back, lower back, shoulders, biceps, triceps, forearms, core, glutes, quads, hamstrings, and calves
  - Half- or full-strength assignments
  - Custom colors and most-used-area sorting
- JSON backup and restore through Android's file picker
  - All training plans included in one backup
  - Can be saved to local storage or a cloud provider exposed by the Android file picker
- Fully offline operation with no account, analytics, or internet permission
- Freely selectable UI accent color
