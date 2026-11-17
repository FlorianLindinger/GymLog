$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot '../helpers/android_env.ps1')
$originalRepo = $script:RepoRoot
$originalApp = $script:AppRoot
$root = Join-Path ([IO.Path]::GetTempPath()) ('gymlog-build-tests-' + [Guid]::NewGuid().ToString('N'))
$saved = @{}
foreach ($name in @('JAVA_HOME','ANDROID_HOME','GYMLOG_BUILD_DIR','GRADLE_USER_HOME')) { $saved[$name] = [Environment]::GetEnvironmentVariable($name) }
$script:Assertions = 0
function Check($value, [string]$message) { $script:Assertions++; if (-not $value) { throw $message } }
function Initialize-Build { }
$script:Builds = 0
$script:FailBuild = $false
$script:EditDuringBuild = $false
function Invoke-Gradle([string[]]$Tasks) {
    $script:Builds++
    if ($script:FailBuild) { throw 'Fixture build failure' }
    $output = Join-Path $env:GYMLOG_BUILD_DIR 'outputs/apk/debug'
    New-Item -ItemType Directory -Path $output -Force | Out-Null
    [IO.File]::WriteAllText((Join-Path $output 'app-debug.apk'), "fixture APK $script:Builds")
    if ($script:EditDuringBuild) { [IO.File]::AppendAllText((Join-Path $script:RepoRoot 'app/app/src/Main.java'), 'during build') }
}
try {
    foreach ($folder in @('app/app/src','app/app/build','app/gradle/wrapper','scripts/windows/helpers','jdk','sdk','gradle-home')) {
        New-Item -ItemType Directory -Path (Join-Path $root $folder) -Force | Out-Null
    }
    foreach ($name in @('android.ps1','android_env.ps1','debug_build.ps1')) {
        Copy-Item -LiteralPath (Join-Path $originalRepo "scripts/windows/helpers/$name") -Destination (Join-Path $root "scripts/windows/helpers/$name")
    }
    $script:RepoRoot = $root
    $script:AppRoot = Join-Path $root 'app'
    $env:JAVA_HOME = Join-Path $root 'jdk'
    $env:ANDROID_HOME = Join-Path $root 'sdk'
    $env:GYMLOG_BUILD_DIR = Join-Path $root 'app/app/build'
    $env:GRADLE_USER_HOME = Join-Path $root 'gradle-home'
    foreach ($name in @('app/app/src/Main.java','version.properties','app/build.gradle.kts','app/gradle/wrapper/gradle-wrapper.properties')) {
        [IO.File]::WriteAllText((Join-Path $root $name), 'original')
    }
    Invoke-DebugBuild
    Check ($script:Builds -eq 1) 'First run must build'
    Check (Test-Path -LiteralPath (Join-Path $root 'GymLog-debug.apk')) 'Debug APK must be in the project root'
    Check (Test-Path -LiteralPath (Join-Path $root 'artifacts/checksums/GymLog-debug.apk.sha256')) 'Debug checksum must be under artifacts/checksums'
    $releaseOutput = Join-Path $env:GYMLOG_BUILD_DIR 'outputs/apk/release'
    New-Item -ItemType Directory -Path $releaseOutput -Force | Out-Null
    [IO.File]::WriteAllText((Join-Path $releaseOutput 'app-release.apk'), 'release fixture')
    Copy-ReleaseApk
    Check ([IO.File]::ReadAllText((Join-Path $root 'GymLog-release.apk')) -eq 'release fixture') 'Release APK must be in the project root'
    Check (Test-Path -LiteralPath (Join-Path $root 'artifacts/checksums/GymLog-release.apk.sha256')) 'Release checksum must be under artifacts/checksums'
    [IO.File]::WriteAllText((Join-Path $script:AppRoot 'artifacts/debug/old.apk'), 'ignored old artifact')
    [IO.File]::WriteAllText((Join-Path $root 'GymLog-debug.apk.sha256'), 'obsolete')
    Remove-Item -LiteralPath (Join-Path $root 'artifacts/checksums/GymLog-debug.apk.sha256')
    Invoke-DebugBuild
    Check (-not (Test-Path -LiteralPath (Join-Path $root 'GymLog-debug.apk.sha256'))) 'Cached builds remove obsolete root checksums'
    Check (Test-Path -LiteralPath (Join-Path $root 'artifacts/checksums/GymLog-debug.apk.sha256')) 'Cached builds restore the nested checksum'
    Check ($script:Builds -eq 1) 'Unchanged run must skip Gradle, including generated build outputs'
    $source = Join-Path $root 'app/app/src/Main.java'
    $timestamp = (Get-Item -LiteralPath $source).LastWriteTimeUtc
    [IO.File]::WriteAllText($source, 'modified')
    (Get-Item -LiteralPath $source).LastWriteTimeUtc = $timestamp
    Invoke-DebugBuild
    Check ($script:Builds -eq 2) 'Content changes must rebuild even with unchanged timestamps'
    (Get-Item -LiteralPath $source).LastWriteTimeUtc = [DateTime]::UtcNow.AddDays(1)
    Invoke-DebugBuild
    Check ($script:Builds -eq 2) 'Timestamp-only changes must reuse the APK'
    foreach ($action in @(
        { [IO.File]::WriteAllText((Join-Path $root 'app/app/src/New.java'), 'new') },
        { Remove-Item -LiteralPath (Join-Path $root 'app/app/src/New.java') },
        { Rename-Item -LiteralPath (Join-Path $root 'app/app/src/Main.java') -NewName 'Renamed.java' },
        { [IO.File]::AppendAllText((Join-Path $root 'version.properties'), 'version change') },
        { [IO.File]::AppendAllText((Join-Path $root 'app/build.gradle.kts'), 'config change') },
        { [IO.File]::AppendAllText((Join-Path $root 'app/gradle/wrapper/gradle-wrapper.properties'), 'wrapper change') },
        { [IO.File]::WriteAllText((Join-Path $env:GRADLE_USER_HOME 'gradle.properties'), 'user setting') },
        { $env:GYMLOG_BUILD_DIR = Join-Path $root 'other-build' },
        { [IO.File]::WriteAllText((Join-Path $root 'GymLog-debug.apk'), 'tampered') },
        { Remove-Item -LiteralPath (Join-Path $root 'GymLog-debug.apk') },
        { [IO.File]::WriteAllText((Join-Path $root 'app/artifacts/debug/build-state.json'), 'invalid JSON') }
    )) {
        $before = $script:Builds
        & $action
        Invoke-DebugBuild
        Check ($script:Builds -eq $before + 1) 'Changed inputs, missing/corrupt APK or invalid state must rebuild'
        Invoke-DebugBuild
        Check ($script:Builds -eq $before + 1) 'Successful rebuild must become reusable'
    }
    [IO.File]::WriteAllText((Join-Path $root 'README.md'), 'documentation only')
    $before = $script:Builds
    Invoke-DebugBuild
    Check ($script:Builds -eq $before) 'Documentation changes must not rebuild'
    Invoke-DebugBuild -ForceBuild
    Check ($script:Builds -eq $before + 1) 'ForceBuild must invoke Gradle'
    $script:FailBuild = $true
    $failed = $false
    try { Invoke-DebugBuild -ForceBuild } catch { $failed = $true }
    Check $failed 'Build failure must propagate'
    Check (-not (Test-Path -LiteralPath (Join-Path $root 'app/artifacts/debug/build-state.json'))) 'Failed builds must invalidate cached success'
    $script:FailBuild = $false
    $before = $script:Builds
    Invoke-DebugBuild
    Check ($script:Builds -eq $before + 1) 'A failed build must be retried'
    $script:EditDuringBuild = $true
    Invoke-DebugBuild -ForceBuild
    Check (-not (Test-Path -LiteralPath (Join-Path $root 'app/artifacts/debug/build-state.json'))) 'Edits during a build must not cache an ambiguous APK'
} finally {
    $script:RepoRoot = $originalRepo
    $script:AppRoot = $originalApp
    foreach ($name in $saved.Keys) { [Environment]::SetEnvironmentVariable($name, $saved[$name]) }
    $resolved = [IO.Path]::GetFullPath($root)
    $temp = [IO.Path]::GetFullPath([IO.Path]::GetTempPath()).TrimEnd('\') + '\'
    if (-not $resolved.StartsWith($temp, [StringComparison]::OrdinalIgnoreCase) -or (Split-Path $resolved -Leaf) -notlike 'gymlog-build-tests-*') { throw 'Unsafe fixture cleanup path' }
    if (Test-Path -LiteralPath $resolved) { Remove-Item -LiteralPath $resolved -Recurse -Force }
}
Write-Output "PASS: $script:Assertions debug build cache assertions; only temporary fixtures and mocked Gradle."
