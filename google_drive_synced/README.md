# GymLog Build Outputs

Double-click the launchers in this folder to open the generated output directories:

- `Open Debug Output.cmd` opens `artifacts\debug`.
- `Open Release Output.cmd` opens `artifacts\release`.

The launchers use paths relative to the repository, so they continue to work if the GymLog folder is moved.

## Build a Debug APK

From the repository root, run:

```bat
scripts\windows\build.bat
```

Output: `artifacts\debug\app-debug.apk`

The debug APK is automatically signed with Android's development key. It is suitable for local testing, not store distribution.

## Build a Signed Release

1. Update `versionName` and increment `versionCode` in `app\build.gradle.kts`.
2. Commit the intended release so the Git working tree is clean.
3. Set the four signing variables in the same terminal without storing their values in the repository:

```bat
set "GYMLOG_KEYSTORE_FILE=C:\secure\gymlog-release.jks"
set "GYMLOG_KEYSTORE_PASSWORD=your-keystore-password"
set "GYMLOG_KEY_ALIAS=your-key-alias"
set "GYMLOG_KEY_PASSWORD=your-key-password"
```

4. Run the release helper with the exact `versionName`:

```bat
scripts\windows\release.bat 1.0.0
```

Outputs:

- `artifacts\release\1.0.0\GymLog-1.0.0.apk`
- `artifacts\release\1.0.0\GymLog-1.0.0.aab`
- `artifacts\release\1.0.0\SHA256SUMS.txt`

Use the APK for direct installation and the AAB for Google Play distribution. Keep the keystore and its passwords outside Google Drive and Git; losing the release key can prevent future updates to the same published app.
