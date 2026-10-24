[CmdletBinding(PositionalBinding=$false)]
param(
    [Parameter(Position=0)][string]$Serial,
    [string]$Apk,
    [switch]$Bluetooth,
    [switch]$NoBluetoothFallback
)
$ErrorActionPreference='Stop'
$script:RepoRoot=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../../..'))
. (Join-Path $PSScriptRoot 'adb_devices.ps1')
. (Join-Path $PSScriptRoot 'bluetooth_apk.ps1')

function Find-GymLogAdb {
    if ($env:GYMLOG_ADB) {
        if (-not (Test-Path -LiteralPath $env:GYMLOG_ADB -PathType Leaf)) { throw 'GYMLOG_ADB does not point to a file.' }
        return $env:GYMLOG_ADB
    }
    $sdkRoots=@($env:ANDROID_HOME,$env:ANDROID_SDK_ROOT)
    foreach ($local in @('app/local.properties','local.properties')) {
        $file=Join-Path $script:RepoRoot $local
        if (Test-Path -LiteralPath $file -PathType Leaf) {
            $match=[regex]::Match([IO.File]::ReadAllText($file),'(?m)^sdk\.dir\s*=\s*(.+)$')
            if ($match.Success) { $sdkRoots+=$match.Groups[1].Value.Trim().Replace('\:',':').Replace('\\','\') }
        }
    }
    if ($env:LOCALAPPDATA) { $sdkRoots+=Join-Path $env:LOCALAPPDATA 'Android/Sdk' }
    foreach ($sdk in $sdkRoots) {
        if (-not $sdk) { continue }
        $candidate=Join-Path $sdk 'platform-tools/adb.exe'
        if (Test-Path -LiteralPath $candidate -PathType Leaf) { return $candidate }
    }
    $command=Get-Command adb -ErrorAction SilentlyContinue
    if ($command) { return $command.Source }
    throw 'ADB not found. Install Android SDK Platform-Tools, or use Bluetooth APK transfer.'
}

try {
    Write-Host 'GymLog - Install via USB, Wi-Fi or Bluetooth'
    if ($Bluetooth -and ($Serial -or $NoBluetoothFallback)) { throw 'Use -Bluetooth without a device serial or -NoBluetoothFallback.' }
    if (-not $Apk) { $Apk=Join-Path $script:RepoRoot 'app/artifacts/debug/app-debug.apk' }
    if (-not (Test-Path -LiteralPath $Apk -PathType Leaf)) { throw 'APK not found. Run build.bat first or pass -Apk PATH.' }
    if (-not $Bluetooth) {
        $connection=Resolve-InstallConnection { Find-GymLogAdb } $Serial -NoBluetoothFallback:$NoBluetoothFallback
        if (-not $connection.Serial) {
            Write-Host $connection.Reason
            Write-Host 'USB/Wi-Fi installation is unavailable. Falling back to Bluetooth APK transfer.'
            $Bluetooth=$true
        }
    }
    if ($Bluetooth) { Send-ApkViaBluetooth $Apk; exit 0 }
    $adb=$connection.Adb
    $selected=$connection.Serial
    Write-Host "Device: $selected"
    Write-Host 'Installing GymLog without deleting existing app data...'
    $install=Invoke-Adb $adb @('-s',$selected,'install','-r',[IO.Path]::GetFullPath($Apk))
    $install.Lines | ForEach-Object { Write-Host $_ }
    if (($install.Lines -join "`n") -notmatch '(?m)^Success\r?$') { throw 'ADB did not confirm installation success. Existing app data was not removed.' }
    $launch=Invoke-Adb $adb @('-s',$selected,'shell','am','start','-n','com.gymlog.app/.MainActivity')
    $launch.Lines | ForEach-Object { Write-Host $_ }
    if (($launch.Lines -join "`n") -match '(?im)^Error(?:\s|:)|Exception') { throw 'APK installed, but Android could not launch GymLog.' }
    Write-Host 'Installed and launched. Existing workout data was preserved.'
} catch { [Console]::Error.WriteLine("ERROR: $($_.Exception.Message)"); exit 1 }
