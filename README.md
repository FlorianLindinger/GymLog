# GymLog

GymLog is a privacy-first Android workout tracker for fast set logging, exercise progression, training history, and body-measurement trends. It is built with Kotlin, Jetpack Compose, SQLite, and Health Connect.

## Highlights

- Fast weight-, level-, and duration-based workout logging
- Multiple independent training plans
- Automatic, mixed pinned, or fixed exercise ordering
- Editable training history and progression warnings
- Interval charts with rolling averages, linear fits, and target ranges
- Optional Health Connect import for weight, body fat, and lean body mass
- Local JSON export and restore
- No account, analytics SDK, or internet permission

## Screenshots

| Overview | Exercise details |
| --- | --- |
| <img src="docs/screenshots/overview.png" alt="Exercise overview" width="320"> | <img src="docs/screenshots/exercise-detail.png" alt="Exercise details" width="320"> |

| History | Statistics |
| --- | --- |
| <img src="docs/screenshots/history.png" alt="Training history" width="320"> | <img src="docs/screenshots/statistics.png" alt="Training statistics" width="320"> |

Screenshots contain sample data.

## Requirements

- Android Studio with Android SDK 36
- JDK 17 or newer
- Android 8.0 (API 26) or newer device/emulator

## Build

Open the repository in Android Studio, allow Gradle to sync, and run the `app` configuration. From PowerShell:

```powershell
$env:JAVA_HOME = "C:\Program Files\Java\jdk-21.0.10"
.\gradlew.bat :app:assembleDebug
```

The APK is written to `app\build\outputs\apk\debug\app-debug.apk`.

Windows development and release helpers are documented in [`scripts/windows`](scripts/windows/README.md). Run the complete local verification with:

```powershell
scripts\windows\verify.bat
```

The same compile, lint, test, and APK checks run on pushes and pull requests through GitHub Actions.

## Demo Data

Open **Menu → Training Plans → Add Demo Training Plan** to create an isolated sample plan with ten weeks of history and a halfway-completed live workout. Repeating the action creates another uniquely named demo plan.

## Data and Privacy

Workout data stays in the app's local SQLite database. Health Connect access is optional and permission-controlled. Manual JSON backups can be stored locally or in a document provider such as Google Drive.

This WIP build resets incompatible local database schemas and accepts only its current backup format. The policy required before stable releases is documented in [`docs/data-format.md`](docs/data-format.md).

Release signing is read only from `GYMLOG_KEYSTORE_FILE`, `GYMLOG_KEYSTORE_PASSWORD`, `GYMLOG_KEY_ALIAS`, and `GYMLOG_KEY_PASSWORD`. Never commit signing files or credentials.

## Project Structure

```text
app/src/main/java/com/gymlog/app/
├── data/        SQLite repository and models
├── ui/          Compose screens and Health Connect integration
└── MainActivity.kt
docs/            Screenshots and data-format policy
scripts/windows/ Development, device, and release helpers
```

## License

[MIT](LICENSE)
