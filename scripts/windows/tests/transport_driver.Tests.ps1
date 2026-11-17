param([string]$WindowsPath = (Join-Path $PSScriptRoot '..'))
$ErrorActionPreference = 'Stop'
$script:DriverAssertions = 0
function Assert-Driver($value, [string]$message) {
    $script:DriverAssertions++
    if (-not $value) { throw $message }
}
$root = Join-Path ([IO.Path]::GetTempPath()) ('gymlog-driver-tests-' + [Guid]::NewGuid().ToString('N'))
$savedFailure = $env:GYMLOG_TEST_CONNECTION_FAIL
$savedReuse = $env:GYMLOG_TEST_REUSE_BUILD
try {
    New-Item -ItemType Directory -Path (Join-Path $root 'helpers') -Force | Out-Null
    Copy-Item -LiteralPath (Join-Path $WindowsPath 'helpers/android.ps1') -Destination (Join-Path $root 'helpers/android.ps1')
    [IO.File]::WriteAllText((Join-Path $root 'GymLog-debug.apk'), 'fixture only')
    @'
$script:RepoRoot = Split-Path -Parent $PSScriptRoot
$script:AppRoot = Join-Path $script:RepoRoot 'app'
$script:Package = 'com.gymlog.app'
function Trace-Driver([string]$Text) { [IO.File]::AppendAllText((Join-Path $script:RepoRoot 'calls.txt'), $Text + "`n") }
function Initialize-Build { Trace-Driver 'BUILD' }
function Invoke-Gradle([string[]]$Tasks) { Trace-Driver 'GRADLE' }
function Copy-DebugApk { Trace-Driver 'COPY' }
function Invoke-DebugBuild([switch]$ForceBuild) {
    if ($env:GYMLOG_TEST_REUSE_BUILD -eq '1' -and -not $ForceBuild) { Trace-Driver 'REUSE'; return }
    Initialize-Build; Invoke-Gradle @(':app:assembleDebug'); Copy-DebugApk
}
function Find-Adb { Trace-Driver 'ADB'; return 'mock-adb' }
function Connect-AndroidWifi($Adb, $PairAddress, $ConnectAddress) { Trace-Driver 'WIFI-CONNECT' }
function Resolve-InstallConnection($AdbResolver, $Serial, $Transport) {
    Trace-Driver "SELECT-$Transport"
    if ($env:GYMLOG_TEST_CONNECTION_FAIL -eq '1') { throw 'Fixture connection unavailable' }
    return [pscustomobject]@{ Adb='mock-adb'; Serial='mock-phone' }
}
function Invoke-Adb($Adb, [string[]]$Arguments) {
    Trace-Driver ('INVOKE ' + ($Arguments -join ' '))
    return [pscustomobject]@{ ExitCode=0; Lines=@('Success') }
}
'@ | Set-Content -LiteralPath (Join-Path $root 'helpers/android_env.ps1') -Encoding ASCII
    @'
function Send-ApkViaBluetooth($Apk) { Trace-Driver ('BLUETOOTH ' + $Apk) }
'@ | Set-Content -LiteralPath (Join-Path $root 'helpers/bluetooth_apk.ps1') -Encoding ASCII
    @'
function Select-GoogleDriveFolder($Folder) { Trace-Driver 'DRIVE-FOLDER'; return 'mock-drive' }
function Send-ApkViaGoogleDrive($Apk, $Folder) { Trace-Driver ('DRIVE-COPY ' + $Apk) }
'@ | Set-Content -LiteralPath (Join-Path $root 'helpers/google_drive_apk.ps1') -Encoding ASCII
    foreach ($case in @(
        @{ Transport='USB'; Fail=$false; Extra=@() },
        @{ Transport='WiFi'; Fail=$false; Extra=@() },
        @{ Transport='Bluetooth'; Fail=$false; Extra=@() },
        @{ Transport='GoogleDrive'; Fail=$false; Extra=@('-DriveFolder','mock-drive') },
        @{ Transport='GoogleDrive'; Fail=$true; Extra=@('-Serial','phone') },
        @{ Transport='USB'; Fail=$true; Extra=@('-DriveFolder','mock-drive') },
        @{ Transport='USB'; Fail=$true; Extra=@() },
        @{ Transport='WiFi'; Fail=$true; Extra=@() },
        @{ Transport='Bluetooth'; Fail=$true; Extra=@('-Serial','phone') },
        @{ Transport='USB'; Fail=$true; Extra=@('-ConnectAddress','192.168.1.9:1234') },
        @{ Transport='WiFi'; Fail=$false; Extra=@('-ConnectAddress','192.168.1.9:1234') },
        @{ Transport='USB'; Fail=$false; Reuse=$true; Extra=@() },
        @{ Transport='WiFi'; Fail=$false; Reuse=$true; Extra=@() },
        @{ Transport='Bluetooth'; Fail=$false; Reuse=$true; Extra=@() },
        @{ Transport='USB'; Fail=$false; Reuse=$true; Extra=@('-ForceBuild') }
    )) {
        [IO.File]::WriteAllText((Join-Path $root 'calls.txt'), '')
        $env:GYMLOG_TEST_CONNECTION_FAIL = if ($case.Fail) { '1' } else { '0' }
        $env:GYMLOG_TEST_REUSE_BUILD = if ($case.Reuse) { '1' } else { '0' }
        $reused = $case.Reuse -and $case.Extra -notcontains '-ForceBuild'
        $arguments = @('-NoProfile','-ExecutionPolicy','Bypass','-File',(Join-Path $root 'helpers/android.ps1'),
            '-Command','build-install','-Transport',$case.Transport) + $case.Extra
        $output = & (Join-Path $PSHOME 'powershell.exe') @arguments 2>&1 | Out-String
        Assert-Driver ($LASTEXITCODE -eq $(if ($case.Fail) { 1 } else { 0 })) "Wrong driver result: $output"
        $calls = [IO.File]::ReadAllText((Join-Path $root 'calls.txt'))
        if ($case.Fail) {
            Assert-Driver ($calls -notmatch 'BUILD|GRADLE|BLUETOOTH|INVOKE') 'Connection/argument failures must stop before builds, installs, or Bluetooth fallback'
        } elseif ($case.Transport -eq 'GoogleDrive') {
            Assert-Driver ($calls -match 'BUILD' -and $calls -match 'DRIVE-COPY') 'Google Drive must build and copy to the chosen sync folder'
            Assert-Driver ($calls -notmatch 'ADB|SELECT-|INVOKE|BLUETOOTH') 'Google Drive must never inspect or use ADB or Bluetooth'
            Assert-Driver ($calls.IndexOf('DRIVE-FOLDER') -lt $calls.IndexOf('BUILD')) 'Choose a valid sync folder before building'
        } elseif ($case.Transport -eq 'Bluetooth') {
            Assert-Driver ($calls -match 'BUILD|REUSE' -and $calls -match 'BLUETOOTH') 'Bluetooth must prepare an APK and dispatch its transfer'
            Assert-Driver ($calls -notmatch 'ADB|SELECT-|INVOKE') 'Bluetooth must never inspect or use ADB'
        } else {
            Assert-Driver ($calls -match "SELECT-$($case.Transport)") 'ADB installers must explicitly select their requested transport'
            Assert-Driver ($calls -notmatch 'BLUETOOTH') 'ADB installers must never dispatch Bluetooth'
            $buildStep = if ($reused) { 'REUSE' } else { 'BUILD' }
            Assert-Driver ($calls.IndexOf('SELECT-') -lt $calls.IndexOf($buildStep)) 'Check the selected connection before spending time on a build'
            Assert-Driver ($calls -match 'install -r' -and $calls -match 'shell am start') 'Successful ADB updates must preserve app data and launch the app'
        }
        if ($case.Extra -contains '-ConnectAddress' -and -not $case.Fail) {
            Assert-Driver ($calls.IndexOf('WIFI-CONNECT') -lt $calls.IndexOf('SELECT-WiFi')) 'An explicit Wi-Fi address must connect before selecting a wireless target'
        }
        if (-not $case.Fail) {
            Assert-Driver (($calls -match 'REUSE') -eq [bool]$reused) 'Reuse/ForceBuild must be forwarded while installation or transfer continues'
            Assert-Driver ($calls.Contains((Join-Path $root 'GymLog-debug.apk'))) 'Every transport must use the APK in the project root'
        }
    }
} finally {
    $env:GYMLOG_TEST_CONNECTION_FAIL = $savedFailure
    $env:GYMLOG_TEST_REUSE_BUILD = $savedReuse
    $resolved = [IO.Path]::GetFullPath($root)
    $temp = [IO.Path]::GetFullPath([IO.Path]::GetTempPath()).TrimEnd('\') + '\'
    if (-not $resolved.StartsWith($temp, [StringComparison]::OrdinalIgnoreCase) -or (Split-Path $resolved -Leaf) -notlike 'gymlog-driver-tests-*') { throw 'Unsafe fixture cleanup path' }
    if (Test-Path -LiteralPath $resolved) { Remove-Item -LiteralPath $resolved -Recurse -Force }
}
Write-Output "PASS: $script:DriverAssertions driver dispatch/failure assertions; only mocked connections, builds, installs and transfers."
