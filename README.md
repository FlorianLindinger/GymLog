# GymLog

<p align="center">
  <img src="app/app/src/main/res/drawable-nodpi/ic_launcher_foreground.png" alt="GymLog app icon" width="180">
</p>

<p align="center">
  An Android workout & weight tracker for logging, stats, plotting, and trends.
</p>

<p align="center">
  <a href="https://github.com/FlorianLindinger/GymLog/actions/workflows/verify.yml"><img src="https://github.com/FlorianLindinger/GymLog/actions/workflows/verify.yml/badge.svg" alt="Android CI"></a>
  <a href="LICENSE"><img src="https://img.shields.io/badge/license-MIT-blue.svg" alt="MIT license"></a>
  <img src="https://img.shields.io/badge/Android-8.0%2B-3DDC84" alt="Android 8.0 or newer">
</p>

## Features

- Convenient weight-, level-, and duration-based workout logging
- Multiple independent training plans
- Per-exercise Manual top, Auto sorted, and Manual bottom ordering
- Editable history, progression warnings, and live-training status
- Interval charts with gridlines, rolling averages, linear fits, and targets
- Body trends for weight, body fat, and lean body mass
- Optional, permission-controlled Health Connect import
- Local JSON export and restore

## Screenshots

| Overview | Exercise Details |
| --- | --- |
| <img src="docs/screenshots/overview.png" alt="Exercise overview" width="320"> | <img src="docs/screenshots/exercise-detail.png" alt="Exercise details" width="320"> |

| History | Statistics |
| --- | --- |
| <img src="docs/screenshots/history.png" alt="Training history" width="320"> | <img src="docs/screenshots/statistics.png" alt="Training statistics" width="320"> |

## How to install

1) WIP: how to downlaod relase

2) WIP how to build self:

    Requirements:

    - Android Studio and Android SDK 36
    - JDK 17 or newer
    - Android 8.0 (API 26) or newer device or emulator

    Open the project in Android Studio, allow Gradle to sync, and run the `app` configuration. To build from PowerShell:

    ```powershell
    scripts\windows\build.bat
    ```

    The finished debug APK is published inside the Android project at `app\app\artifacts\debug\app-debug.apk`, where Google Drive can synchronize it. Disposable Gradle intermediates stay outside the repository to prevent cloud-sync file locks. Run the complete local verification with:

    ```powershell
    scripts\windows\verify.bat
    ```

    Windows development and release helpers are described in [scripts/windows](scripts/windows/README.md). GitHub Actions runs the same compile, lint, test, and APK checks on pushes and pull requests.

## License

[MIT](LICENSE.txt)

## Backend

GymLog is built with Kotlin, Jetpack Compose, SQLite, and Health Connect. Workout data remains local by default; the app has no account system, analytics SDK, or internet permission.
