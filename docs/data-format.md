# Data Format Policy

GymLog currently treats persisted data as disposable WIP data:

- A database schema mismatch resets the local database instead of carrying migration code.
- Backup import accepts only the current exact format version and reports older/newer files as unsupported before changing any local data.
- Backup restore is transactional, so a malformed supported-version backup does not leave a partial restore.

Before distributing builds whose user data must survive upgrades, change this policy:

1. Keep every released database migration and test upgrades from each supported version.
2. Give the JSON backup format an explicit schema/version contract and add fixture tests.
3. Add converters from supported old backup versions to the current in-memory model.
4. Export or snapshot data before destructive migration, and never silently reset a released user's database.
5. Increase database and backup versions only alongside their migration/converter tests.
