# Changelog

Notable changes to GymLog are recorded here. Planned work belongs in `TODO.md`.

## Unreleased

### Changed

- Windows batch launchers now share the PowerShell helper structure used by `sleep_was_never_an_option`, with portable JDK/SDK discovery and device tools that do not require a build environment. Logging retains existing logs and does not force-stop GymLog.
- Windows release launchers prepare and verify versions locally by default; publication requires `-Publish` and atomically pushes main and its version tag. Existing installer aliases remain available.
- Body plots open their settings when tapped, with compact headers and fit rates at the top right. Interval count, total count, mean, and range each have a visibility switch, with global defaults and per-plot overrides.
- Body starts directly with interval controls and plots. Pulling down past the top reveals sync status and the last sync time; releasing triggers a fresh Health Connect sync. Concurrent reads are prevented, and progress clears on success or failure.
- Body has a direct Settings button for global interval, averaging/bin count, fit visibility, summary statistics, high/low references, and target visibility.
- Body chart colors distinguish measurements (blue), fit (yellow), target (green), all-time high/low (purple), and history-window high/low (orange). Each reference label matches its lines.
- All-time high, all-time low, history-window high, and history-window low have independent switches in global and per-plot settings, including custom history windows. Existing combined settings migrate to the corresponding pair of switches.
- Y axes include short, lighter minor ticks at sensible subdivisions, such as 0.5 between major ticks spaced by 2.5.
- Body X axes mark month boundaries and center adaptive labels such as `Jun26` or `J26` between them. Narrow plots and large text use fewer labels; close views use calendar weeks with specific dates.
- Automatic Health Connect refresh on resume reuses a successful read for 15 minutes; explicit pull/menu sync still refreshes immediately. Body-data processing runs off the UI thread, uses binary search for nearest weight records, and caches chart sorting, fits, summaries, bins, axis ranges, and tick calculations. The training timer stops waking after the active session becomes idle. Battery savings have not been measured on a device.
- Chart dates use English month names without changing their format. X-axis ticks and gridlines follow calendar days, weeks, months, quarters, and suitably spaced years.
- Body fit rates use `/mon.`. Target estimates include remaining weeks alongside months until over a year, then show years/months and omit months from 10 years onward.
- Exercise charts place `Volume/training [a.u.]` between the left and right axis labels, with monthly trends rounded to two significant digits.
- Show more training history in the overview and up to 30 entries in exercise and training history.
- Automatic exercise ordering compares weighted body-region overlap with the current and previous distinct exercises, then prefers fewer lifetime training days when overlap ties. Manual ordering stays available.
- Body-region intensity now has Low, Medium, and High levels, with separate statistics and preserved legacy profile weights.
- Show hours per recorded training in the last 30 days, omitting rest-day zeroes and preserving fractional hours in the latest duration.
- Body plots use compact automatic Y-axis limits with round ticks inside the frame, including 10, 12.5, 15, 17.5, and 20 for data around 10–20. Each plot can independently fix either Y limit or leave it automatic. Other charts retain round ticks and nonnegative limits; trends and target bands are clipped at plot edges.
- Exercise overview history stays on one line and truncates to fit. Cards open exercise details, with separate metric/set controls and region editing inside the exercise details.
- Unknown exercise overlap is treated as zero for automatic ordering. Unfinished, non-current exercises with unknown overlap use the green outline while retaining the unknown label.
- Windows build helpers also find Android Studio and the Android SDK in their configured locations on drive D.

### Added

- Persistent installer consoles with `GYMLOG_NO_PAUSE=1` for automation, verified clipboard copying for Bluetooth APK paths, a Wi-Fi pairing/connection launcher, SHA-256 checksums for copied debug APKs, and signed local release builds.
- A patch-release launcher and Windows helper tests for installer exit codes, clipboard fallback, environment discovery, and version preparation.
- Global Body plot settings and independent per-plot overrides. Following the global target setting controls visibility only; target values and acceptance ranges remain per plot, with previous saved values preserved.
- Optional Body averaging across equal time bins in the selected interval, defaulting to 20 bins. Empty bins are skipped; fits and high/low values use original measurements.
- Per-plot Body high/low values and reference lines for all-time history, a selected history window, or both. The history window defaults to two months and supports presets and custom dates.
- Watts-based exercise tracking with recorded power and training minutes, including backup and restore support.
- Quick adjustment buttons for weight (±3/5 kg), power (±10 W), and duration (±1/2/3 minutes).
- Green/red comparisons for recorded exercise level or load and volume, with dates on a separate history row.
- An offline catalogue of 184 general exercises plus 421 machine/movement choices covering 170 Technogym and 101 Hammer Strength models, with [official product sources](docs/manufacturer-equipment.tsv), manufacturer/product-line browsing, and separate choices for multi-purpose machines. Search recognizes Stepmaster/Stairmaster, numbered stations, spacing variations, swapped letters, and small spelling mistakes, including “Bauchmaschine 2.” Specialised equipment without a suitable region preset requests manual regions.
- Body-region editing within exercise details, profile selection during exercise creation, and visible estimated overlap and training counts. The region selector immediately offers matching profiles and a collapsible front/back guide with 15 named, numbered regions using the current region colors.
- Removed the redundant bottom “Show muscle reference” control from the region selector.
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
