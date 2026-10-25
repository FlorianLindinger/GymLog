# Changelog

Notable changes to GymLog are recorded here. Planned work belongs in `TODO.md`.

## Unreleased

### Changed

- Windows batch launchers now share the PowerShell helper structure used by `sleep_was_never_an_option`, with portable JDK/SDK discovery and device tools that do not require a build environment. Logging retains existing logs and does not force-stop GymLog.
- Windows release launchers prepare and verify versions locally by default; publication requires `-Publish` and atomically pushes main and its version tag. Existing installer aliases remain available.
- Body plots open their settings when tapped, with compact headers, fit rates at the top right, and measurement counts, means, and interval ranges.
- Chart dates use English month names without changing their format. X-axis ticks and gridlines follow calendar days, weeks, months, quarters, and suitably spaced years.
- Body fit rates use `/mon.`. Target estimates show 12 months before switching to years and months, and omit months from 10 years onward.
- Exercise charts place `Volume/training [a.u.]` between the left and right axis labels, with monthly trends rounded to two significant digits.
- Show more training history in the overview and up to 30 entries in exercise and training history.
- Automatic exercise ordering compares weighted body-region overlap with the current and previous distinct exercises, then prefers fewer lifetime training days when overlap ties. Manual ordering stays available.
- Body-region intensity now has Low, Medium, and High levels, with separate statistics and preserved legacy profile weights.
- Show hours per recorded training in the last 30 days, omitting rest-day zeroes and preserving fractional hours in the latest duration.
- Use roughly four to six evenly spaced, round Y-axis ticks across all charts. Axis limits stay nonnegative, and trends and target bands are clipped at the plot edges.
- Windows build helpers also find Android Studio and the Android SDK in their configured locations on drive D.

### Added

- Persistent installer consoles with `GYMLOG_NO_PAUSE=1` for automation, verified clipboard copying for Bluetooth APK paths, a Wi-Fi pairing/connection launcher, SHA-256 checksums for copied debug APKs, and signed local release builds.
- A patch-release launcher and Windows helper tests for installer exit codes, clipboard fallback, environment discovery, and version preparation.
- Global Body plot settings and independent per-plot overrides for interval, averaging, fit visibility, and targets. Global targets and acceptance ranges are stored separately for each body metric; target visibility has an explicit checkbox.
- Optional Body averaging across equal time bins in the selected interval, defaulting to 20 bins. Empty bins are skipped; fits and high/low values use original measurements.
- Per-plot Body high/low values and reference lines for all-time history, a selected history window, or both. The history window defaults to two months and supports presets and custom dates.
- Watts-based exercise tracking with recorded power and training minutes, including backup and restore support.
- Quick adjustment buttons for weight (±3/5 kg), power (±10 W), and duration (±1/2/3 minutes).
- Green/red comparisons for recorded exercise level or load and volume, with dates on a separate history row.
- An offline catalogue of 177 exercises with English/German names, aliases, equipment filters, and editable suggested muscle profiles.
- Body-region editing directly from the overview, profile selection during exercise creation, and visible estimated overlap and training counts.
- Regression tests and [profile/ranking documentation](docs/exercise-profiles.md).
- Renamed primary installer commands/window titles to include USB, Wi-Fi and Bluetooth, with automatic Bluetooth fallback after USB/Wi-Fi detection, compatibility shortcuts, and `-NoBluetoothFallback` for ADB-only automation.
- Bluetooth APK transfer via `-Bluetooth` on both Windows installers, plus dedicated Bluetooth shortcuts. Installation is confirmed manually on the phone; install-only transfer needs no Android SDK or USB debugging.
- Bluetooth helper tests with a mocked wizard launch; no phone or radio is contacted.
- A changelog for tracking future changes and the existing app baseline.
- A changelog link in the README.

### Fixed

- Exercise setting colors compare against the last recorded entry. Matching values and exercises without a recorded baseline stay gray, including after reverting a pending change or recording a previously changed setting.
- Change counts and increase statistics use recorded training transitions, excluding setup changes before the first entry and temporary adjustments that were never recorded.
- Updating the current training and next session also updates recorded entries when the next-session setting already matches the selected value. Logging a minutes-only entry updates its setting baseline.
- Exercise history no longer clips long entries or dates at the right edge; general training history rows wrap their content.

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
