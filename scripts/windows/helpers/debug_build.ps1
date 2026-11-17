# Cache only successful debug builds. Gradle remains responsible for incremental
# compilation when inputs change; verify/release always run their full workflow.
function Get-DebugBuildFingerprint {
    $files = @(
        Get-ChildItem -LiteralPath $script:RepoRoot -File -Force | Where-Object {
            $_.Name -match '\.(gradle|kts|properties)$|^gradlew(\.bat)?$'
        }
        foreach ($folder in @('app','gradle','buildSrc','build-logic')) {
            $path = Join-Path $script:RepoRoot $folder
            if (Test-Path -LiteralPath $path -PathType Container) {
                # Prune generated directories, including a custom build location.
                $pending = New-Object 'Collections.Generic.Stack[string]'
                $pending.Push($path)
                while ($pending.Count) {
                    foreach ($item in Get-ChildItem -LiteralPath $pending.Pop() -Force) {
                        if ($item.PSIsContainer) {
                            if ($item.Name -notin @('build','.gradle','.kotlin','.cxx','.externalNativeBuild','artifacts','captures','screenshots','.idea') -and
                                $item.FullName -ne [IO.Path]::GetFullPath($env:GYMLOG_BUILD_DIR)) {
                                $pending.Push($item.FullName)
                            }
                        } else { $item }
                    }
                }
            }
        }
        foreach ($name in @('android.ps1','android_env.ps1','debug_build.ps1')) {
            Get-Item -LiteralPath (Join-Path $script:RepoRoot "scripts/windows/helpers/$name")
        }
        $gradleHome = $env:GRADLE_USER_HOME
        if (-not $gradleHome -and $env:USERPROFILE) { $gradleHome = Join-Path $env:USERPROFILE '.gradle' }
        if ($gradleHome) {
            foreach ($name in @('gradle.properties','init.gradle','init.gradle.kts')) {
                $path = Join-Path $gradleHome $name
                if (Test-Path -LiteralPath $path -PathType Leaf) { Get-Item -LiteralPath $path }
            }
            $init = Join-Path $gradleHome 'init.d'
            if (Test-Path -LiteralPath $init) { Get-ChildItem -LiteralPath $init -Recurse -File -Force }
        }
        foreach ($path in @(
            (Join-Path $env:JAVA_HOME 'release'),
            (Join-Path $env:ANDROID_HOME 'platforms/android-36/source.properties'),
            (Join-Path $env:ANDROID_HOME 'build-tools/36.0.0/source.properties')
        )) {
            if (Test-Path -LiteralPath $path -PathType Leaf) { Get-Item -LiteralPath $path }
        }
    )
    $lines = @('debug-build-cache-v1', $script:RepoRoot)
    foreach ($file in ($files | Sort-Object FullName -Unique)) {
        $lines += $file.FullName + ':' + (Get-FileHash -LiteralPath $file.FullName -Algorithm SHA256).Hash
    }
    foreach ($entry in (Get-ChildItem Env: | Where-Object {
        $_.Name -match '^(JAVA_HOME|ANDROID_HOME|ANDROID_SDK_ROOT|GYMLOG_BUILD_DIR|GRADLE_USER_HOME|JAVA_OPTS|GRADLE_OPTS|ORG_GRADLE_PROJECT_.*)$'
    } | Sort-Object Name)) { $lines += $entry.Name + '=' + $entry.Value }
    $sha = [Security.Cryptography.SHA256]::Create()
    try { return [BitConverter]::ToString($sha.ComputeHash([Text.Encoding]::UTF8.GetBytes(($lines -join "`n")))).Replace('-', '') }
    finally { $sha.Dispose() }
}

function Invoke-DebugBuild([switch]$ForceBuild) {
    Write-Host '[BUILD] Checking Android build inputs...' -ForegroundColor Cyan
    Initialize-Build
    $directory = Join-Path $script:AppRoot 'artifacts/debug'
    $apk = Join-Path $script:RepoRoot 'GymLog-debug.apk'
    $statePath = Join-Path $directory 'build-state.json'
    $fingerprint = Get-DebugBuildFingerprint
    if (-not $ForceBuild -and (Test-Path -LiteralPath $statePath -PathType Leaf) -and (Test-Path -LiteralPath $apk -PathType Leaf)) {
        try {
            $state = Get-Content -LiteralPath $statePath -Raw | ConvertFrom-Json
            if ($state.Fingerprint -eq $fingerprint -and $state.ApkHash -eq (Get-FileHash -LiteralPath $apk -Algorithm SHA256).Hash) {
                Write-ApkChecksum $apk
                Write-Host '[BUILD] No changes; reusing the existing debug APK.' -ForegroundColor Green
                return
            }
        } catch { Write-Host '[BUILD] Cached build state is unreadable; rebuilding.' -ForegroundColor Yellow }
    }
    # A failed build must never leave a reusable success marker.
    if (Test-Path -LiteralPath $statePath) { Remove-Item -LiteralPath $statePath -Force }
    Write-Host '[BUILD] Building debug APK...' -ForegroundColor Cyan
    Invoke-Gradle @(':app:assembleDebug')
    Copy-DebugApk
    if ((Get-DebugBuildFingerprint) -eq $fingerprint) {
        New-Item -ItemType Directory -Force -Path $directory | Out-Null
        $state = @{ Fingerprint = $fingerprint; ApkHash = (Get-FileHash -LiteralPath $apk -Algorithm SHA256).Hash }
        [IO.File]::WriteAllText($statePath, ($state | ConvertTo-Json))
    } else {
        Write-Host '[BUILD] Inputs changed during the build; the next run will check again.' -ForegroundColor Yellow
    }
}
