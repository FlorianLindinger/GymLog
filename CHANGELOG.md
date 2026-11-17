# Changelog

Notable changes to GymLog are recorded here. Planned work belongs in `docs/TODO.md`.

## Unreleased

### Changed

- Always offer ranked starting profiles for weak exercise-name matches, labeled as potentially weak suggestions.
- Add compact L/M/H and editable percentage controls for body regions. Scale preset Core/abs values by 50% and draw proportionally wider markers; preserve existing profiles without a hidden Core multiplier. Keep custom percentages in storage, plan files, duplication, and backups.
- Add 40% fatigue per exercise from yesterday, 20% from two days ago and 10% from three days ago, independent of order and counted once per exercise per training day. The three percentages are editable in Exercise Order and included in backups.
- Strength score is now the average Epley estimated one-rep maximum (kg) over every valid set in a training day instead of a sum of weight x reps^0.1 points, so reps count properly and set count no longer swings it; volume remains the workload measure.
- Strength-score trend warnings fit a line through every training day in the window instead of comparing only the first and latest day.
- Database writes and backup/plan file reads and writes run off the main thread. A backup restored while the activity was recreated still offers Undo; plan export no longer fails after rotation.
- Clearing every wheel in "Change Sets" now goes through the confirmed, undoable delete instead of silently removing the entry. Manual Health Connect sync reports permission-check failures instead of crashing.
- Automatic Health Connect refreshes (every minute on Body, and on resume) first ask Health Connect's change log and re-read the full body history only when something changed. Exercise Order caches its ranking between redraws.
- JSON backup code moved to GymBackupJson.kt; unused repository functions removed.
- Highlight exercise notes through color and weight without changing padding or typography size. Show only the latest entry above the chart and full recent history below it.
- Restore green/red overview history separators; use neutral colors within exercise history. Initialize new-session wheels from the latest recorded batch instead of stale saved wheel values.
- Put plus adjustment rows above minus rows for time and weight. Allow a local date when adding body weight, preserving that date in local data and Health Connect records.

- Apply 20% extra Core/abs fatigue and recency weights of 100%, 50%, 25%, 12.5%, then 10% for all earlier exercises.
- Give supporting weight sets a small, diminishing contribution to Wathan 1RM (two equal sets add 2.5%, capped below 5%) across charts, summaries, warnings, and the stats explorer.
- Show warnings at the top of exercise details and compact recent sets/minutes above the chart, with expandable history.
- Open plan choices directly from the training banner and offer a working Add another training plan action.
- Open larger Body and exercise charts with settings below in a scrolling view. Body settings preview edits before saving.
- Add independent Follow global / Force show / Force hide choices for goals, acceptance bounds, reference lines, fits, averages, raw data, and plot summaries; preserve existing saved visibility choices.

- Keep Add weight only in Body, with a wider single-line middle button and equal outer buttons. Remove colored overview history separators and minute suffixes.
- Replace watt work and level load comparisons with a best-entry Riegel-style intensity estimate at 10 minutes: intensity x (minutes / 10)^0.06. Use it in exercise charts, entry summaries and the stats explorer; document the fixed heuristic and same-machine level limitation.

- Hide the weeks-without-weight-increase warning while a higher next-session weight is configured. Keep actual recorded increase counters and 1RM trend warnings based on performed entries; restore the stale warning if the planned increase is reverted.

- Replace Delete Current Training with Delete Last Training, targeting the latest recorded training day in the active plan (06:00 boundary), with confirmation, stale-selection protection and undo.
- Include manual Body entries in full JSON backups and restore them locally without automatically exporting them to Health Connect.
- Add manual weight, optional body-fat percentage and optional muscle percentage from Add weight in Body. Weight and fat can be written to Health Connect for compatible readers such as Google Fit; muscle stays local. Support permission denial, local-only saving and duplicate-safe retries.
- Add Download plan and Load file below the training-plan selector. Plan files contain exercise configuration without history; loading adds and selects a new plan without replacing existing data.

- Format Body targets and acceptance together before their unit, such as 70±2 kg. Fix the Android backup-settings shortcut action and explain how to find Backup if only general Settings is available; show a message if Settings cannot be opened.

- Follow every newly logged or changed current-training exercise in the overview, instead of scrolling to the top only for the first entry of the day. Wait for refreshed ranking before scrolling; logging from exercise details also positions the overview on that exercise when returning. Historical entry edits and deletions do not request this scroll.

- Show Change and Delete directly on each Recent Entries row. Change opens the set wheels or minutes controls for that recorded entry, preserving its date and weight/level/watts. Confirm deletion and retain undo; clearing every set also asks for deletion confirmation. Keep larger imported set counts and rep values when opening the wheels.

- Apply Auto priority before training frequency within each overlap bin, so +1 moves an unfinished Auto exercise earlier and -1 moves it later even when training counts differ. Label the signed value Auto priority and explain its scope. Preserve current/manual/completed group ordering and the minutes-left, levels/watts-right chart layout.

- Put recorded minutes on the left axis and levels/watts on the right in exercise charts, including legends, axis colors and trend lines. Open mode-specific metric help instead of the weight-training reps/sets explanation. Explain the 10-minute intensity estimate and its same-machine level limitation.

- Add independent Body target visibility switches for goal minus acceptance and goal plus acceptance, globally and per plot. Show each enabled limit as a dashed green boundary with its side of the acceptance shading; hidden limits no longer expand the automatic Y-axis. Existing target bands retain both sides by default.

- Use bright white for the Body fixed-day moving average line and its Avg days label, separating it from blue raw data and the other colored plot overlays. Update the chart color guide to match.

- Keep exported APK checksums under `artifacts/checksums/` instead of the project root, including reused debug builds. Remove obsolete root checksum sidecars when exporting or reusing an APK.

- Windows launchers are organized around three everyday commands: USB.bat, WiFi.bat, and Bluetooth.bat. Each uses only its named transport, with no fallback. Build-only, install-only, diagnostics and release commands moved under advanced/; old combined launchers are inactive archived sources. All launchers keep result windows open and color-code progress, success, errors and pending Bluetooth actions.
- Exercise overview scrolling avoids rebuilding card history and warnings when rows re-enter the list. Single-line text sizing now estimates from measured width instead of doing twelve binary-search layouts. Current-training filtering uses numeric day boundaries instead of converting every historical timestamp to a date.
- Body plots replace time bins with independently toggleable trailing moving averages: a fixed period in days and a fraction of the displayed interval. Both can overlay independently toggleable raw data, globally or per plot. Existing bin averaging migrates to the equivalent interval fraction.
- Body reference labels name All-time or the history interval (e.g. 2mon history). History low/high default to the 5th/95th percentiles, with a configurable 0-50% cutoff shown in the labels when nonzero; 0 restores exact extrema. All-time low/high stay exact, and means/fits continue using raw data.
- Body refreshes Health Connect on entry, on foreground return, and every minute while visible. The periodic timer stops outside Body and in the background; automatic refreshes use existing permissions and skip overlapping reads and permission checks.
- Body Settings adds Order and hide plots, with saved arrow ordering, individual visibility switches, and a reset option. Default order is Weight, Muscle Mass, Fat Mass, Fat %, then Muscle Mass %.
- Time-entry buttons in the exercise overview now say `+ Time` instead of `+ Training`.
- Trend warnings always use the Wathan best-set 1RM formula. Removed the per-exercise Volume/1RM choice; custom warning thresholds remain available. The Menu metric switch now controls charts and entry summaries only, and legacy exercise metric overrides no longer affect either warnings or chart display.
- Exercise weight-axis labels follow the most common recent recorded weight increment (such as 3, 5, or 2.5 kg), using larger multiples when needed. Labels align with recorded loads; charts without an observed change retain automatic ticks.
- Body-region names stay on one line and use an ellipsis when space is limited in exercise details, profile selectors, the body guide, and statistics.
- Reduce live-training timer wakeups to displayed minute changes and the idle transition, while caching training-history summaries, exercise overlap, warnings, and chart calculations across redraws. Battery savings have not been measured on a device.
- Unfinished Manual bottom exercises stay above completed exercises. Completed exercises from every ordering group appear last, from first to last done, while the current exercise stays first.
- Exercise-detail change buttons preview the recorded sets or minutes they will edit.

- Body global and per-plot settings use wider dialogs, distinct section cards, Global/Custom badges, consistent switches, grouped low/mean/high chips, and a separate Health Connect section.

- Body plots add independently toggleable dashed mean lines for all-time and history windows, with Low / Mean / High values above the plot. History references can follow each plot's exact interval or retain a separate selection, globally or per plot.

- 1RM chart display defaults on when no global metric choice is saved; the saved global choice is retained. Its exercise-chart legend opens an explanation. Level/watts workload labels now state their intensity-by-minute units.
- A separate `WIP stats` button beside Stats opens an extensive dashboard with equal-period comparisons, weekly goals, a training calendar, selectable trend charts, exercise progress/frequency/warning explorers, personal records, weighted body-region sets, and calculation notes. The existing Stats screen remains available.

- The global chart Volume/1RM switch is directly in Menu with an outlined help button. The exercise-chart metric legend also looks like a button and opens its explanation without a question mark. Progress Warnings explicitly names its fixed 1RM metric. Help & Explanations is scrollable and covers warning overrides, ordering, training days, Stats, Body plots and sync, history/undo, and backups.

- Exercise overview history uses compact minute labels such as `13m`. Exercise Order opens from Menu instead of the bottom navigation row.

- Global and per-exercise Progress Warnings settings configure thresholds for Wathan-based `1RM` trends in weight exercises. The Menu metric switch controls charts and entry summaries independently.

- Every exercise can follow global progress warnings or save its own settings. Blank and zero disable warnings globally or per exercise, including the volume window. Overrides persist through backup/restore, plan copies, and deletion undo.

- Body plots mirror major and minor Y-axis ticks on the right edge without adding right-side labels.

- Body uses Gym and Settings navigation buttons. Sync Health Connect and sync status are at the top of global Settings; the separate Menu and pull-to-sync gesture have been removed.

- Body interval controls include `5Y` globally and per plot, including high/low history windows, keeping both button rows equally filled.

- Exercise cards indicate overlap using seven fixed color bins: dark green, green, yellow, orange, red-orange, red, and dark red. The overview no longer shows the overlap context header.
- Progress warnings fit on one line with compact week and volume text. Volume checks use a saved, configurable training window; training-count warnings have been removed.

- Within each overlap bin, automatic exercise ordering prefers fewer distinct training days in the last two calendar months, then tie priority. Exercise Order previews the automatic ranking.
- Body-region guide visibility is remembered globally, with the guide shown by default.
- Exercise overview titles, status, and history shrink to fit, with the metric always at the top right and Level abbreviated to `Lv.`.
- Choosing an exercise profile preserves an explicitly selected tracking mode, including Watts.

- Windows batch launchers now share the PowerShell helper structure used by `sleep_was_never_an_option`, with portable JDK/SDK discovery and device tools that do not require a build environment. Logging retains existing logs and does not force-stop GymLog.
- Windows release launchers under `advanced/release/` prepare and verify versions locally by default; publication requires `-Publish` and atomically pushes main and its version tag.
- Body plots open their settings when tapped, with compact headers and fit rates at the top right. Interval count, total count, mean, and range each have a visibility switch, with global defaults and per-plot overrides.
- Body starts directly with interval controls and plots. Global Settings contains Sync Health Connect, sync status, and the last sync time. Concurrent reads are prevented, and progress clears on success or failure.
- Body has a direct Settings button for global interval, raw data and moving averages, fit visibility, summary statistics, high/low references, and target visibility.
- Body chart colors distinguish measurements (blue), fit (yellow), target (green), all-time high/low (purple), and history-window high/low (orange). Each reference label matches its lines.
- All-time high, all-time low, history-window high, and history-window low have independent switches in global and per-plot settings, including custom history windows. Existing combined settings migrate to the corresponding pair of switches.
- Y axes include short, lighter minor ticks at sensible subdivisions, such as 0.5 between major ticks spaced by 2.5.
- Body X axes mark month boundaries and center adaptive labels such as `Jun26` or `J26` between them. Narrow plots and large text use fewer labels; close views use calendar weeks with specific dates.
- Automatic Health Connect refresh on resume reuses a successful read for 15 minutes; explicit Settings sync still refreshes immediately. Body-data processing runs off the UI thread, uses binary search for nearest weight records, and caches chart sorting, fits, summaries, bins, axis ranges, and tick calculations. The training timer stops waking after the active session becomes idle. Battery savings have not been measured on a device.
- Chart dates use English month names without changing their format. X-axis ticks and gridlines follow calendar days, weeks, months, quarters, and suitably spaced years.
- Body fit rates use `/mon.`. Target estimates include remaining weeks alongside months until over a year, then show years/months and omit months from 10 years onward.
- Exercise charts place `Volume/training [a.u.]` between the left and right axis labels, with monthly trends rounded to two significant digits.
- Show more training history in the overview and up to 30 entries in exercise and training history.
- Automatic exercise ordering considers all distinct exercises in the current training, with fixed exponential weights of 100%, 50%, 25%, 12.5%, and so on. Contributions accumulate without normalization by exercise count, with each region capped at full load. Manual ordering stays available.
- Body-region intensity now has Low, Medium, and High levels, with separate statistics and preserved legacy profile weights.
- Show hours per recorded training in the last 30 days, omitting rest-day zeroes and preserving fractional hours in the latest duration.
- Body plots use compact automatic Y-axis limits with round ticks inside the frame, including 10, 12.5, 15, 17.5, and 20 for data around 10–20. Each plot can independently fix either Y limit or leave it automatic. Other charts retain round ticks and nonnegative limits; trends and target bands are clipped at plot edges.
- Exercise overview history stays on one line and truncates to fit. Cards open exercise details, with separate metric/set controls and region editing inside the exercise details.
- Unknown exercise overlap is treated as zero for automatic ordering. Unfinished, non-current exercises with unknown overlap use the green outline without a separate overlap label.
- Windows build helpers also find Android Studio and the Android SDK in their configured locations on drive D.

### Added

- Persistent installer consoles with `GYMLOG_NO_PAUSE=1` for automation, verified clipboard copying for Bluetooth APK paths, a Wi-Fi pairing/connection launcher, SHA-256 checksums for copied debug APKs, and signed local release builds.
- A patch-release launcher and Windows helper tests for installer exit codes, clipboard fallback, environment discovery, and version preparation.
- Global Body plot settings and independent per-plot overrides. Following the global target setting controls visibility only; target values and acceptance ranges remain per plot, with previous saved values preserved.
- Optional Body moving averages use a fixed duration or a fraction of the selected interval, with independently toggleable raw data. Fits and references use original measurements.
- Per-plot Body high/low values and reference lines for all-time history, a selected history window, or both. The history window defaults to two months and supports presets and custom dates.
- Watts-based exercise tracking with recorded power and training minutes, including backup and restore support.
- Quick adjustment buttons for weight (±3/5 kg), power (±10 W), and duration (±1/2/3 minutes).
- Green/red comparisons for recorded exercise level or load and volume, with dates on a separate history row.
- An offline catalogue of 184 general exercises plus 421 machine/movement choices covering 170 Technogym and 101 Hammer Strength models, with [official product sources](docs/manufacturer-equipment.tsv), manufacturer/product-line browsing, and separate choices for multi-purpose machines. Search recognizes Stepmaster/Stairmaster, numbered stations, spacing variations, swapped letters, and small spelling mistakes, including “Bauchmaschine 2.” Specialised equipment without a suitable region preset requests manual regions.
- Body-region editing within exercise details, profile selection during exercise creation, and color-coded estimated overlap. The region selector immediately offers matching profiles and a collapsible front/back guide with 15 named, numbered regions using the current region colors.
- Removed the redundant bottom “Show muscle reference” control from the region selector.
- Regression tests and [profile/ranking documentation](docs/exercise-profiles.md).
- Separate USB, Wi-Fi and Bluetooth launchers use transport-specific selection without fallback; obsolete combined launchers are archived as inactive source files.
- Bluetooth APK transfer uses `Bluetooth.bat` or `advanced/send_bluetooth.bat`. Installation is confirmed manually on the phone; install-only transfer needs no Android SDK or USB debugging.
- Bluetooth helper tests with a mocked wizard launch; no phone or radio is contacted.
- A changelog for tracking future changes and the existing app baseline.
- A changelog link in the README.

### Fixed

- Showing hidden exercises scrolls to their first row without the removed overlap header shifting the target.
- Changing weight or level for the next session preserves the current training's recorded values when reps or minutes are edited afterward. The configured value applies on the next training day.
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
