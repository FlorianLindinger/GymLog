# Changelog

Notable changes to GymLog are recorded here. Planned work belongs in `TODO.md`.

## Unreleased

### Changed

- Show hours per recorded training in the last 30 days, omitting rest-day zeroes and preserving fractional hours in the latest duration.
- Use roughly four to six evenly spaced, round Y-axis ticks across all charts. Axis limits stay nonnegative, and trends and target bands are clipped at the plot edges.
- Windows build helpers also find Android Studio and the Android SDK in their configured locations on drive D.

### Added

- Renamed primary installer commands/window titles to include USB, Wi-Fi and Bluetooth, with automatic Bluetooth fallback after USB/Wi-Fi detection, compatibility shortcuts, and `-NoBluetoothFallback` for ADB-only automation.
- Bluetooth APK transfer via `-Bluetooth` on both Windows installers, plus dedicated Bluetooth shortcuts. Installation is confirmed manually on the phone; install-only transfer needs no Android SDK or USB debugging.
- Bluetooth helper tests with a mocked wizard launch; no phone or radio is contacted.
- A changelog for tracking future changes and the existing app baseline.
- A changelog link in the README.

## 3.0 — Existing feature baseline

The current app version is 3.0 (Android version code 3). This section summarizes existing capabilities; it does not claim they were all introduced in 3.0.

- Weight-, level-, and duration-based workout logging with multiple independent training plans.
- Manual and automatic exercise ordering, editable history, progression warnings, and live-training status.
- Training statistics and interval charts with rolling averages, linear fits, and targets.
- Body measurements and trends for weight, body fat, and lean body mass.
- Optional, permission-controlled Health Connect synchronization.
- Portable JSON backup and restore, with local data storage and no account, analytics, or internet permission.
- Windows helpers for building, verification, USB/Wi-Fi installation, device diagnostics, screenshots, and app logs.
- GitHub verification and tagged release workflows, including signed APK/AAB artifacts and SHA-256 checksums.

Earlier per-version changes have not been reconstructed.
