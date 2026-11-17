param([string]$WindowsPath = (Join-Path $PSScriptRoot '..'))
$ErrorActionPreference = 'Stop'
$RepoPath = [IO.Path]::GetFullPath((Join-Path $WindowsPath '../..'))
if ($env:OS -ne 'Windows_NT') { Write-Output 'SKIP: console integration checks require Windows cmd.exe.'; return }
$script:InstallerAssertions = 0
function Assert-Installer($value, [string]$message) {
    $script:InstallerAssertions++
    if (-not $value) { throw $message }
}
$testRoot = Join-Path ([IO.Path]::GetTempPath()) ('gymlog-console-tests-' + [Guid]::NewGuid().ToString('N'))
$savedPause = $env:GYMLOG_NO_PAUSE
$savedExit = $env:GYMLOG_TEST_INSTALL_EXIT
$launchers = @(
    @('build & install via USB.bat','build-install','USB'), @('build & install via WiFi.bat','build-install','WiFi'), @('build & install via Bluetooth.bat','build-install','Bluetooth'),
    @('build & install via Google Drive.bat','build-install','GoogleDrive'),
    @('scripts/windows/advanced/build.bat','build','USB'), @('scripts/windows/advanced/verify.bat','verify','USB'), @('scripts/windows/advanced/device_check.bat','device','USB'),
    @('scripts/windows/advanced/connect_wifi.bat','wifi','WiFi'), @('scripts/windows/advanced/launch_and_stream_gym_log_logs.bat','logs','USB'),
    @('scripts/windows/advanced/screenshot.bat','screenshot','USB'), @('scripts/windows/advanced/install_usb.bat','install','USB'),
    @('scripts/windows/advanced/install_wifi.bat','install','WiFi'), @('scripts/windows/advanced/send_bluetooth.bat','install','Bluetooth'),
    @('scripts/windows/advanced/release/build_release.bat','release','USB'), @('scripts/windows/advanced/release/release_patch.bat','patch','USB'),
    @('scripts/windows/advanced/release/release_minor.bat','minor','USB'), @('scripts/windows/advanced/release/release_major.bat','major','USB'),
    @('scripts/windows/advanced/release/release_no_bump.bat','none','USB')
)
function Invoke-LauncherFixture($entry, [int]$Code, [bool]$NoPause) {
    $env:GYMLOG_TEST_INSTALL_EXIT = [string]$Code
    $env:GYMLOG_NO_PAUSE = if ($NoPause) { '1' } else { $null }
    $info = New-Object Diagnostics.ProcessStartInfo
    $info.FileName = $env:ComSpec
    $info.Arguments = '/d /c call "' + (Join-Path $testRoot $entry[0]) + '" -Apk "fixture path with spaces.apk"'
    $info.WorkingDirectory = [IO.Path]::GetTempPath()
    $info.UseShellExecute = $false
    $info.CreateNoWindow = $true
    $info.RedirectStandardInput = $true
    $info.RedirectStandardOutput = $true
    $info.RedirectStandardError = $true
    $process = New-Object Diagnostics.Process
    $process.StartInfo = $info
    try {
        if (-not $process.Start()) { throw 'Could not start console fixture.' }
        $prefix = ''
        if (-not $NoPause) {
            do {
                $line = $process.StandardOutput.ReadLineAsync()
                if (-not $line.Wait(10000)) { $process.Kill(); throw 'Fixture did not reach its persistent console.' }
                if ($null -eq $line.Result) { throw 'Launcher closed before its persistent console.' }
                $prefix += $line.Result + "`n"
            } until ($line.Result.Contains('This window stays open.'))
            Assert-Installer (-not $process.WaitForExit(100)) "$($entry[0]) must remain open on both success and failure"
        }
        $process.StandardInput.WriteLine('exit')
        $process.StandardInput.Close()
        if (-not $process.WaitForExit(10000)) { $process.Kill(); throw 'Console fixture did not finish.' }
        $output = $prefix + $process.StandardOutput.ReadToEnd() + $process.StandardError.ReadToEnd()
        Assert-Installer ($process.ExitCode -eq $Code) "$($entry[0]) lost exit code $Code. Output: $output"
        Assert-Installer ($output.Contains("MOCK_COMMAND=$($entry[1])")) "$($entry[0]) must route to its intended command. Output: $output"
        Assert-Installer ($output.Contains('MOCK_APK=fixture path with spaces.apk')) "$($entry[0]) must preserve quoted arguments"
        if ($entry[1] -notin @('patch','minor','major','none')) {
            Assert-Installer ($output.Contains("MOCK_TRANSPORT=$($entry[2])")) "$($entry[0]) must select its explicit transport"
        }
        $status = if ($Code -ne 0) { '[ERROR]' } elseif ($entry[2] -in @('Bluetooth','GoogleDrive')) { '[ACTION REQUIRED]' } else { '[SUCCESS]' }
        Assert-Installer ($output.Contains($status)) "$($entry[0]) must show an accurate final status"
        Assert-Installer (($output -match 'This window stays open\.') -eq (-not $NoPause)) 'Only automation opt-out may close the console immediately'
    } finally { $process.Dispose() }
}
try {
    foreach ($folder in @('scripts/windows/helpers','scripts/windows/advanced','scripts/windows/advanced/release')) { New-Item -ItemType Directory -Path (Join-Path $testRoot $folder) -Force | Out-Null }
    Copy-Item -LiteralPath (Join-Path $WindowsPath 'helpers/launch.bat') -Destination (Join-Path $testRoot 'scripts/windows/helpers/launch.bat')
    foreach ($entry in $launchers) { Copy-Item -LiteralPath (Join-Path $RepoPath $entry[0]) -Destination (Join-Path $testRoot $entry[0]) }
    @'
param([string]$Command,[string]$Transport,[string]$Apk)
Write-Output "MOCK_COMMAND=$Command"
Write-Output "MOCK_TRANSPORT=$Transport"
Write-Output "MOCK_APK=$Apk"
exit ([int]$env:GYMLOG_TEST_INSTALL_EXIT)
'@ | Set-Content -LiteralPath (Join-Path $testRoot 'scripts/windows/helpers/android.ps1') -Encoding ASCII
    @'
param([string]$Bump,[string]$Apk)
Write-Output "MOCK_COMMAND=$Bump"
Write-Output "MOCK_APK=$Apk"
exit ([int]$env:GYMLOG_TEST_INSTALL_EXIT)
'@ | Set-Content -LiteralPath (Join-Path $testRoot 'scripts/windows/helpers/release_version.ps1') -Encoding ASCII
    $top = @(Get-ChildItem -LiteralPath $RepoPath -Filter '*.bat' | Sort-Object Name | Select-Object -ExpandProperty Name)
    Assert-Installer (($top -join ',') -eq 'build & install via Bluetooth.bat,build & install via Google Drive.bat,build & install via USB.bat,build & install via WiFi.bat') 'The four everyday transport launchers should be visible in the project root'
    Assert-Installer (@(Get-ChildItem -LiteralPath $WindowsPath -Filter '*.bat').Count -eq 0) 'Everyday launchers must be moved out of scripts/windows'
    $shared = Get-Content -LiteralPath (Join-Path $WindowsPath 'helpers/launch.bat') -Raw
    foreach ($color in @('0B','0A','0C','0E')) { Assert-Installer ($shared.Contains("color $color")) 'Progress, success, errors and pending actions need distinct colors' }
    foreach ($entry in $launchers) {
        Invoke-LauncherFixture $entry 0 $true
        Invoke-LauncherFixture $entry 7 $true
        Invoke-LauncherFixture $entry 0 $false
        Invoke-LauncherFixture $entry 7 $false
    }
} finally {
    $env:GYMLOG_NO_PAUSE = $savedPause
    $env:GYMLOG_TEST_INSTALL_EXIT = $savedExit
    $resolved = [IO.Path]::GetFullPath($testRoot)
    $temp = [IO.Path]::GetFullPath([IO.Path]::GetTempPath()).TrimEnd('\') + '\'
    if (-not $resolved.StartsWith($temp, [StringComparison]::OrdinalIgnoreCase) -or (Split-Path $resolved -Leaf) -notlike 'gymlog-console-tests-*') { throw 'Unsafe fixture cleanup path' }
    if (Test-Path -LiteralPath $resolved) { Remove-Item -LiteralPath $resolved -Recurse -Force }
}
Write-Output "PASS: $script:InstallerAssertions launcher routing, color, argument, persistence and exit-code assertions; no device/build/UI contacted."
