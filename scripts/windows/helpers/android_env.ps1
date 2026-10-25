Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$script:RepoRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../../..'))
$script:Package = 'com.gymlog.app'
$script:AppRoot = Join-Path $script:RepoRoot 'app'

function Find-JavaHome {
    $candidates = @($env:GYMLOG_JAVA_HOME, $env:JAVA_HOME, $env:STUDIO_JDK)
    # The Windows installer records Android Studio's actual location, including
    # custom drives. Derive jbr from its icon instead of embedding a PC's path.
    foreach ($key in @(
        'HKLM:\SOFTWARE\Microsoft\Windows\CurrentVersion\Uninstall\Android Studio',
        'HKCU:\SOFTWARE\Microsoft\Windows\CurrentVersion\Uninstall\Android Studio',
        'HKLM:\SOFTWARE\WOW6432Node\Microsoft\Windows\CurrentVersion\Uninstall\Android Studio',
        'HKLM:\SOFTWARE\Microsoft\Windows\CurrentVersion\Uninstall\AndroidStudio'
    )) {
        $entry = Get-ItemProperty -LiteralPath $key -ErrorAction SilentlyContinue
        if ($entry -and $entry.PSObject.Properties['InstallLocation'] -and $entry.InstallLocation) {
            $candidates += Join-Path $entry.InstallLocation 'jbr'
        }
        if ($entry -and $entry.PSObject.Properties['DisplayIcon'] -and $entry.DisplayIcon) {
            $icon = ([regex]::Replace([string]$entry.DisplayIcon, ',\d+$', '')).Trim('"')
            $candidates += Join-Path (Split-Path -Parent (Split-Path -Parent $icon)) 'jbr'
        }
    }
    foreach ($base in @($env:ProgramFiles, ${env:ProgramFiles(x86)}, $env:LOCALAPPDATA)) {
        if ($base) { $candidates += Join-Path $base 'Android/Android Studio/jbr' }
    }
    $java = Get-Command java -ErrorAction SilentlyContinue
    if ($java) { $candidates += Split-Path -Parent (Split-Path -Parent $java.Source) }
    foreach ($candidate in $candidates) {
        if (-not $candidate) { continue }
        $release = Join-Path $candidate 'release'
        if ((Test-Path -LiteralPath (Join-Path $candidate 'bin/java.exe')) -and (Test-Path -LiteralPath $release)) {
            $version = [regex]::Match([IO.File]::ReadAllText($release), 'JAVA_VERSION="(17|21)[.\"]')
            if ($version.Success) { return [IO.Path]::GetFullPath($candidate) }
        }
    }
    throw 'Set JAVA_HOME (or GYMLOG_JAVA_HOME) to JDK 17 or 21, or the jbr folder of Android Studio.'
}

function Find-AndroidSdk([switch]$ForAdb) {
    $candidates = @($env:ANDROID_HOME, $env:ANDROID_SDK_ROOT)
    $local = Join-Path $script:AppRoot 'local.properties'
    if (Test-Path -LiteralPath $local) {
        $match = [regex]::Match([IO.File]::ReadAllText($local), '(?m)^sdk\.dir\s*=\s*(.+)$')
        if ($match.Success) { $candidates += $match.Groups[1].Value.Trim().Replace('\:', ':').Replace('\\', '\') }
    }
    $candidates += Join-Path $script:RepoRoot '.tools/android-sdk'
    if ($env:LOCALAPPDATA) { $candidates += Join-Path $env:LOCALAPPDATA 'Android/Sdk' }
    $required = if ($ForAdb) { 'platform-tools/adb.exe' } else { 'platforms' }
    foreach ($candidate in $candidates) {
        if ($candidate -and (Test-Path -LiteralPath (Join-Path $candidate $required))) { return [IO.Path]::GetFullPath($candidate) }
    }
    throw 'Android SDK not found. Set ANDROID_HOME or configure sdk.dir in local.properties using Android Studio.'
}

function Initialize-Build {
    $env:JAVA_HOME = Find-JavaHome
    $env:ANDROID_HOME = Find-AndroidSdk
    $env:DEBUG = '' # Gradle's .bat launcher otherwise echoes every command when DEBUG is inherited.
    if (-not $env:GYMLOG_BUILD_DIR) {
        if ($env:LOCALAPPDATA) { $env:GYMLOG_BUILD_DIR = Join-Path $env:LOCALAPPDATA 'GymLog/build/app' }
        else { $env:GYMLOG_BUILD_DIR = Join-Path $script:RepoRoot '.tools/b' }
    }
}

function Invoke-Gradle([string[]]$Tasks) {
    Push-Location $script:AppRoot
    try {
        & (Join-Path $script:AppRoot 'gradlew.bat') @Tasks --console=plain --no-daemon
        if ($LASTEXITCODE -ne 0) { throw "Gradle failed (exit $LASTEXITCODE)." }
    } finally { Pop-Location }
}

function Find-Adb {
    if ($env:GYMLOG_ADB) {
        if (-not (Test-Path -LiteralPath $env:GYMLOG_ADB -PathType Leaf)) { throw 'GYMLOG_ADB does not point to a file.' }
        return $env:GYMLOG_ADB
    }
    try { $sdk = Find-AndroidSdk -ForAdb } catch { $sdk = $null }
    if ($sdk) {
        $path = Join-Path $sdk 'platform-tools/adb.exe'
        if (Test-Path -LiteralPath $path) { return $path }
    }
    $command = Get-Command adb -ErrorAction SilentlyContinue
    if ($command) { return $command.Source }
    throw 'ADB not found. Install Android SDK Platform-Tools or set ANDROID_HOME.'
}

. (Join-Path $PSScriptRoot 'adb_devices.ps1')

function Assert-WifiAddress([string]$Address) {
    if ($Address -notmatch '^(?:[A-Za-z0-9][A-Za-z0-9.-]*|\[[0-9A-Fa-f:]+\]):([0-9]{1,5})$') { throw 'Use the IP:port shown in Android Wireless debugging.' }
    if ([int]$Matches[1] -lt 1 -or [int]$Matches[1] -gt 65535) { throw 'Port must be between 1 and 65535.' }
}

function Copy-DebugApk {
    $source = Join-Path $env:GYMLOG_BUILD_DIR 'outputs/apk/debug/app-debug.apk'
    if (-not (Test-Path -LiteralPath $source)) { throw "APK missing: $source" }
    $directory = Join-Path $script:AppRoot 'artifacts/debug'
    New-Item -ItemType Directory -Force -Path $directory | Out-Null
    $destination = Join-Path $directory 'app-debug.apk'
    Copy-Item -LiteralPath $source -Destination $destination -Force
    $hash = (Get-FileHash -LiteralPath $destination -Algorithm SHA256).Hash.ToLowerInvariant()
    [IO.File]::WriteAllText((Join-Path $directory 'SHA256SUMS.txt'), "$hash  app-debug.apk`n")
    Write-Host "APK: $destination"
}

function Build-SignedRelease {
    foreach ($name in @('GYMLOG_KEYSTORE_FILE','GYMLOG_KEYSTORE_PASSWORD','GYMLOG_KEY_ALIAS','GYMLOG_KEY_PASSWORD')) {
        if (-not [Environment]::GetEnvironmentVariable($name)) { throw "Release signing is not configured: set $name." }
    }
    if (-not (Test-Path -LiteralPath $env:GYMLOG_KEYSTORE_FILE -PathType Leaf)) { throw 'GYMLOG_KEYSTORE_FILE does not exist.' }
    $content = [IO.File]::ReadAllText((Join-Path $script:AppRoot 'app/build.gradle.kts'))
    $version = [regex]::Match($content, 'versionName\s*=\s*"(\d+\.\d+(?:\.\d+)?)"').Groups[1].Value
    if (-not $version) { throw 'Could not read the app version.' }
    $tools = @(Get-ChildItem -LiteralPath (Join-Path $env:ANDROID_HOME 'build-tools') -Directory |
        Where-Object { $_.Name -match '^\d+\.\d+\.\d+$' } | Sort-Object { [version]$_.Name } -Descending)
    $signer = $tools | ForEach-Object { Join-Path $_.FullName 'lib/apksigner.jar' } |
        Where-Object { Test-Path -LiteralPath $_ -PathType Leaf } | Select-Object -First 1
    if (-not $signer) { throw 'apksigner.jar not found. Install Android SDK Build Tools.' }
    Invoke-Gradle @(':app:testDebugUnitTest', ':app:lintDebug', ':app:assembleRelease', ':app:bundleRelease')
    $apk = Join-Path $env:GYMLOG_BUILD_DIR 'outputs/apk/release/app-release.apk'
    $bundle = Join-Path $env:GYMLOG_BUILD_DIR 'outputs/bundle/release/app-release.aab'
    foreach ($file in @($apk, $bundle)) {
        if (-not (Test-Path -LiteralPath $file -PathType Leaf)) { throw "Release asset missing: $file" }
    }
    & (Join-Path $env:JAVA_HOME 'bin/java.exe') -jar $signer verify --verbose --print-certs $apk
    if ($LASTEXITCODE -ne 0) { throw 'Release APK signature verification failed.' }
    $directory = Join-Path $script:AppRoot "artifacts/release/$version"
    New-Item -ItemType Directory -Path $directory -Force | Out-Null
    Copy-Item -LiteralPath $apk -Destination (Join-Path $directory "GymLog-$version.apk") -Force
    Copy-Item -LiteralPath $bundle -Destination (Join-Path $directory "GymLog-$version.aab") -Force
    $checksums = @('apk', 'aab') | ForEach-Object {
        $name = "GymLog-$version.$_"
        "$((Get-FileHash -LiteralPath (Join-Path $directory $name) -Algorithm SHA256).Hash.ToLowerInvariant())  $name"
    }
    [IO.File]::WriteAllText((Join-Path $directory 'SHA256SUMS.txt'), ($checksums -join "`n") + "`n")
    Write-Host "Verified release assets and checksums: $directory"
}
