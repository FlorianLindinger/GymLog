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
        $env:GYMLOG_BUILD_DIR = Join-Path $script:RepoRoot '.tools/b'
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
. (Join-Path $PSScriptRoot 'debug_build.ps1')

function Assert-WifiAddress([string]$Address) {
    if ($Address -notmatch '^(?:[A-Za-z0-9][A-Za-z0-9.-]*|\[[0-9A-Fa-f:]+\]):([0-9]{1,5})$') { throw 'Use the IP:port shown in Android Wireless debugging.' }
    if ([int]$Matches[1] -lt 1 -or [int]$Matches[1] -gt 65535) { throw 'Port must be between 1 and 65535.' }
}

function Connect-AndroidWifi([string]$Adb, [string]$PairAddress, [string]$ConnectAddress) {
    if (-not $PairAddress -and -not $ConnectAddress) { throw 'Supply the IP:port from Android Wireless debugging. See scripts/windows/README.md.' }
    # Validate both addresses before pairing or connecting.
    if ($PairAddress) { Assert-WifiAddress $PairAddress }
    if ($ConnectAddress) { Assert-WifiAddress $ConnectAddress }
    if ($PairAddress) {
        Write-Host '[WIFI] Enter the pairing code at the ADB prompt; it is not saved.' -ForegroundColor Cyan
        & $Adb pair $PairAddress
        if ($LASTEXITCODE -ne 0) { throw 'Wireless pairing failed.' }
    }
    if ($ConnectAddress) {
        $connection = Invoke-Adb $Adb @('connect', $ConnectAddress)
        $connection.Lines | ForEach-Object { Write-Host $_ }
        if (($connection.Lines -join ' ') -notmatch '(?:already )?connected to ') { throw 'ADB did not confirm a Wi-Fi connection.' }
    }
}

function Request-AndroidWifiConnection([string]$Adb) {
    Write-Host '[WIFI] Connect the PC and phone to the same Wi-Fi network, then enable Developer options > Wireless debugging on the phone.' -ForegroundColor Yellow
    Write-Host '[WIFI] For a new pairing, open Pair device with pairing code and keep that screen open. Already paired? Leave the first address blank.' -ForegroundColor Yellow
    $pair = (Read-Host 'Pairing IP:port (blank if already paired)').Trim()
    if ($pair) { Connect-AndroidWifi $Adb $pair '' }
    Write-Host '[WIFI] Return to the main Wireless debugging screen. Its IP address & port is the connection address; the pairing port is different.' -ForegroundColor Yellow
    $connect = (Read-Host 'Connection IP:port (blank to cancel)').Trim()
    if (-not $connect) { throw 'Wi-Fi connection cancelled. No build or installation was started.' }
    Connect-AndroidWifi $Adb '' $connect
}

function Write-ApkChecksum([string]$Apk) {
    $directory = Join-Path $script:RepoRoot 'artifacts/checksums'
    New-Item -ItemType Directory -Path $directory -Force | Out-Null
    $name = [IO.Path]::GetFileName($Apk)
    $hash = (Get-FileHash -LiteralPath $Apk -Algorithm SHA256).Hash.ToLowerInvariant()
    [IO.File]::WriteAllText((Join-Path $directory "$name.sha256"), "$hash  ../../$name`n")
    if (Test-Path -LiteralPath "$Apk.sha256" -PathType Leaf) { Remove-Item -LiteralPath "$Apk.sha256" -Force }
}

function Copy-AndroidApk([ValidateSet('debug','release')][string]$Variant) {
    $source = Join-Path $env:GYMLOG_BUILD_DIR "outputs/apk/$Variant/app-$Variant.apk"
    if (-not (Test-Path -LiteralPath $source)) { throw "APK missing: $source" }
    $name = "GymLog-$Variant.apk"
    $destination = Join-Path $script:RepoRoot $name
    Copy-Item -LiteralPath $source -Destination $destination -Force
    Write-ApkChecksum $destination
    Write-Host "APK: $destination"
}

function Copy-DebugApk { Copy-AndroidApk 'debug' }
function Copy-ReleaseApk { Copy-AndroidApk 'release' }

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
    Copy-ReleaseApk
    Write-Host "Verified release assets and checksums: $directory"
}
