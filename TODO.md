# GymLog TODO

This backlog tracks release work. Keep feature exploration in [Feature Ideas](docs/feature-ideas.md), and move an item here only when its scope and acceptance criteria are understood.

## P0 — Release Readiness

- [ ] Add unit tests for exercise ordering, live-session boundaries, progression warnings, trend fitting, and backup validation.
- [ ] Add instrumented tests for backup restore, history editing, demo-plan creation, and process recreation during a live training.
- [ ] Test the release candidate on API 26, a current mid-range device, and API 36 with large text and dark mode.
- [ ] Create fixed backup fixtures and verify that malformed or outdated backups fail with a useful, non-destructive message.
- [ ] Resolve the intermittent Windows APK packaging/output lock or document a reproducible cleanup procedure.
- [ ] Finalize the privacy policy, Play Data Safety answers, and Health Connect data-type declaration.
- [ ] Configure release signing in CI without exposing credentials and test a signed internal-release artifact.
- [ ] Add a pinned-version Gitleaks check to CI and document how confirmed false positives are reviewed and allowlisted.
- [ ] Define semantic versioning, release notes, and database/backup migration gates before the first stable release.

## P1 — Quality

- [ ] Audit accessibility labels, focus order, contrast, touch targets, and dynamic type across every screen.
- [ ] Add screenshot or golden tests for the overview, body charts, dialogs, empty states, and restore errors.
- [ ] Benchmark overview rendering and chart interaction with a large history; optimize only measured bottlenecks.
- [ ] Add structured, local-only diagnostic logging that never includes notes, measurements, or credentials.
- [ ] Verify background behavior and battery use with Health Connect import, timers, and long live trainings.
- [ ] Add a release checklist covering lint, tests, backup round-trip, permissions, rotation policy, and store assets.

## P2 — Product Polish

- [ ] Add a personal-record timeline with filters and accessible explanations.
- [ ] Add plot annotations for deloads, illness, injury, travel, and equipment changes.
- [ ] Prototype goal forecasts with uncertainty and minimum-data safeguards.
- [ ] Evaluate workout templates and supersets without adding friction to fast logging.

## Done

- [x] Continuous integration for compile, lint, tests, and APK assembly
- [x] Demo training plan with historical and halfway-finished sample data
- [x] Documented Automatic, Mixed, and Fixed exercise ordering
- [x] Documented the current data-format policy
