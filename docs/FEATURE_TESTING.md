# Training actions, plan files and manual Body entries

Automated tests use isolated Robolectric databases, Compose button clicks and Android's FakeHealthConnectClient. They do not modify a connected phone or Google Fit account.

- `PlanAndTrainingActionsTest`: latest-day deletion across the 06:00 boundary, other-plan isolation, undo, empty/stale requests, daylight saving, and standalone plan round trips without history.
- `NewActionsUiTest`: Delete Last Training confirmation/cancel/undo, plan file buttons, Body-only Add weight navigation, and Body input with sync errors.
- `ManualBodyEntryTest`: weight/fat Health Connect round trip, duplicate-safe retries, revoked write permission, persisted local percentages and invalid inputs.

Run `app/gradlew -p app :app:testDebugUnitTest` (or the Windows verification helper). Windows Android-resource tests require build output on the project drive; the default ignored `.tools/b/` satisfies this.

## Google Fit check on a phone

This requires a connected phone with Health Connect and Google Fit. A simulated Health Connect round trip cannot prove a Google Fit dashboard update.

1. In Google Fit, open Profile > Settings > Sync Fit with Health Connect and allow access to weight and body-fat data. Names can vary by app version.
2. In GymLog, open Body and choose Add weight, enter a real measurement you intend to keep, optionally add body-fat percentage, leave Health Connect sync on and grant the requested write permissions.
3. In Health Connect's data browser, check the weight and body-fat records, their time and GymLog as source.
4. Open Google Fit and check the same values. If they are in Health Connect but absent in Fit, check Fit's permissions/sync status.
5. Muscle percentage is stored only in GymLog. It is not exported as lean body mass, which is a different measurement.

Plan files contain routine configuration rather than workout history, body measurements or other plans. Load file creates and selects a new plan; importing the same file again creates another uniquely named copy. Full-device JSON restore remains a separate action.

## Ten-minute intensity comparison

Watts and level metrics use the best individual estimate per training: `intensity * (recordedMinutes / 10)^0.06`. The reference is exactly 10 minutes. The fatigue exponent is a fixed Riegel-style heuristic, not an athlete-specific fit or a validated conversion from machine levels to power. Compare similar hard efforts near 10 minutes on the same machine. Raw recorded values are preserved; the weight-exercise Volume/strength-score switch does not affect this metric.

`TrainingPerformanceTest` checks the 10-minute anchor, shorter and longer efforts, invalid inputs, and best-entry selection without adding separate efforts. `StatsDashboardTest` checks that the explorer uses the same metric. `ExerciseTrendTest` checks mode-specific help and plain overview history with no minute suffix.

## Fatigue, exercise details, and display overrides

- `ExerciseRankingTest` checks the 10% recency floor, 20% Core/abs factor, saturation, and ranking behavior.
- `TrainingPerformanceTest` checks that every valid set contributes fully to the strength score, invalid sets contribute zero, normal load/rep trade-offs can improve the score despite lower volume, and substantially fewer sets can reduce it. Warning tests verify that display switches do not change the session metric. See [the score definition and limitations](strength-score.md). `ExerciseTrendTest` checks that the primary measure is on the left, recorded load on the right, and reps use an independent unlabelled scale without a fit.
- `BodyChartMathTest` checks independent Follow global, Force show, and Force hide choices, including a hidden goal with a visible acceptance boundary.
- `NewActionsUiTest` creates a plan from the banner and switches back, opens the enlarged Body chart and persists an individual visibility choice, and checks exercise warnings, expandable compact history, and enlarged exercise charts.

## Region percentages and recent training context

Preset Core/abs percentages are 1.5 times the base L/M/H value. Saved region percentages are not automatically changed. All region calculations, markers, backup/restore, plan files, and duplication use the explicit intensity value. The region editor accepts 0?1000%; zero removes a region. Profiles are starting estimates, not muscle activation measurements. Weak text matches still return ranked suggestions without applying one automatically.

Previous-day fatigue uses the local 06:00 training-day boundary: 30% for each exercise yesterday, 15% two days ago and 7.5% three days ago. Multiple sets do not multiply this contribution. Today's exercise order retains 100/50/25/12.5/10/10? weights.

Tests cover custom percentage persistence, weak preset matches, prior-day weighting, colored overview separators, stale saved wheel values, dated Body entries including daylight saving, and the compact percentage editor.

Fatigue color boundaries are shared by Auto grouping and outline colors. Four bands default to switches at 25/50/75%; the Exercise Order editor validates strictly increasing starts from 1 to 100. FatigueBandsTest checks boundaries, validation and distinct colors; ExerciseRankingTest checks no region dilution, cumulative prior-day work and no bias crossing color groups; repository/UI tests cover persistence, backup compatibility and editing.
