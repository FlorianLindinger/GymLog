# GymLog

GymLog is a private, offline Android workout tracker built for one ordered exercise routine. It uses Kotlin, Jetpack Compose, and the phone's SQLite database.

## Included

- A reorderable exercise list with your own names, current weights, and today's reps
- Five 0-15 rep wheels that remember the previous set pattern; zero skips a set
- Exercise details with notes, recent sets, weight history, a progress chart, and time since the last weight change
- A saved pause-between-sets note per exercise (display only, with no timer)
- Last-trained and 30-day set-frequency indicators on the routine overview
- A calendar/history screen with daily sessions and 8-week `weight × reps` volume trends
- A tappable front/back body guide with full or half strength
- Separate upper/lower back, usage sorting, and customizable distinct colors
- A distinct color for each body area; half-strength assignments use half-width markers
- Per-exercise weight and volume charts with remembered preset time windows
- JSON download/upload containing exercises, ordering, notes, areas, sets, weights, and settings
- Offline storage with no account, analytics, or internet permission

## Run it on this computer

The simplest route is Android Studio:

1. Install the current stable **Android Studio** from the Android developer website.
2. Open Android Studio and choose **Open**, then select this `GymLog` folder.
3. Let Gradle sync. If Android Studio asks to install Android SDK 35, accept it.
4. Open **Tools > Device Manager**, choose **Create device**, select a Pixel-style phone and download a recent Android system image.
5. Start that emulator, select it in the toolbar, and click **Run** (the green triangle).

You can also build from PowerShell after Android Studio has installed the SDK:

```powershell
$env:JAVA_HOME = "C:\Program Files\Java\jdk-21.0.10"
.\gradlew.bat assembleDebug
```

The debug APK will be at `app\build\outputs\apk\debug\app-debug.apk`.

## Install it on an Android phone

### Directly from Android Studio

1. On the phone, enable **Developer options** by tapping **Build number** seven times in **Settings > About phone**.
2. Enable **USB debugging** in Developer options.
3. Connect the phone with a data-capable USB cable and approve the debugging prompt on the phone.
4. Select the phone in Android Studio and click **Run**.

### From the APK

1. Build `app-debug.apk` with the command above.
2. Copy the APK to the phone (USB, Drive, email to yourself, etc.).
3. Open it on the phone and allow **Install unknown apps** for the file-manager/browser you used.
4. Confirm installation. Android may warn that this is an app from outside the Play Store; that is expected for your own debug build.

If Android platform tools are installed and USB debugging is enabled, this command also works:

```powershell
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

## Backups

From the overview, open **Backup**. **Download backup** creates a `.json` file using Android's system file picker. **Upload and restore backup** replaces the current database after confirmation. Export before restoring if the existing phone data matters.

## Good next additions

