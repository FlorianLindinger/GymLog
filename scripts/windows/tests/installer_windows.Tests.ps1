param([string]$WindowsPath = (Join-Path $PSScriptRoot '..'))
$ErrorActionPreference = 'Stop'
if ($env:OS -ne 'Windows_NT') {
    Write-Output 'SKIP: installer console integration checks require Windows cmd.exe.'
    return
}
$script:InstallerAssertions = 0
function Assert-Installer($value, [string]$message) {
    $script:InstallerAssertions++
    if (-not $value) { throw $message }
}
$names = @(
    'install_via_usb_wifi_or_bluetooth.bat', 'build_and_install_via_usb_wifi_or_bluetooth.bat'
)
$testRoot = Join-Path ([IO.Path]::GetTempPath()) ('installer-window-tests-' + [Guid]::NewGuid().ToString('N'))
$helpers = Join-Path $testRoot 'helpers'
New-Item -ItemType Directory -Path $helpers -Force | Out-Null
$previousPause = $env:GYMLOG_NO_PAUSE
$previousExit = $env:GYMLOG_TEST_INSTALL_EXIT
function Invoke-InstallerFixture([string]$Name, [int]$Code, [bool]$NoPause) {
    $env:GYMLOG_TEST_INSTALL_EXIT = [string]$Code
    $env:GYMLOG_NO_PAUSE = if ($NoPause) { '1' } else { $null }
    $info = New-Object Diagnostics.ProcessStartInfo
    $info.FileName = $env:ComSpec
    $info.Arguments = '/d /c call "' + (Join-Path $testRoot $Name) + '"'
    $info.UseShellExecute = $false
    $info.CreateNoWindow = $true
    $info.RedirectStandardInput = $true
    $info.RedirectStandardOutput = $true
    $info.RedirectStandardError = $true
    $process = New-Object Diagnostics.Process
    $process.StartInfo = $info
    try {
        if (-not $process.Start()) { throw 'Could not start installer fixture.' }
        $prefix = ''
        if (-not $NoPause) {
            # Observe the actual command prompt waiting before sending its exit command.
            do {
                $line = $process.StandardOutput.ReadLineAsync()
                if (-not $line.Wait(10000)) { $process.Kill();throw 'Installer fixture did not reach its persistent console.' }
                if ($null -eq $line.Result) { throw 'Installer closed before reaching its persistent console.' }
                $prefix += $line.Result + "`n"
            } until ($line.Result.Contains('This window stays open.'))
            Assert-Installer (-not $process.WaitForExit(300)) "$Name must remain open until the user closes the command prompt"
        }
        $process.StandardInput.WriteLine('exit')
        $process.StandardInput.Close()
        if (-not $process.WaitForExit(10000)) { $process.Kill();throw 'Installer fixture did not finish within 10 seconds.' }
        $output = $prefix + $process.StandardOutput.ReadToEnd() + $process.StandardError.ReadToEnd()
        Assert-Installer ($process.ExitCode -eq $Code) "$Name must preserve exit code $Code. Output: $output"
        $expectedCommand = if ($Name.StartsWith('build_')) { 'build-install' } else { 'install' }
        Assert-Installer ($output.Contains("MOCK_INSTALL_COMMAND=$expectedCommand")) "$Name must still invoke the correct command"
        Assert-Installer (($output -match 'This window stays open\.') -eq (-not $NoPause)) "$Name must retain the final console unless automation opts out"
    } finally { $process.Dispose() }
}
try {
    foreach ($name in $names) { Copy-Item -LiteralPath (Join-Path $WindowsPath $name) -Destination (Join-Path $testRoot $name) }
    @'
param([string]$Command, [switch]$Bluetooth)
Write-Output "MOCK_INSTALL_COMMAND=$Command"
exit ([int]$env:GYMLOG_TEST_INSTALL_EXIT)
'@ | Set-Content -LiteralPath (Join-Path $helpers 'android.ps1') -Encoding ASCII
    foreach ($name in $names) {
        Invoke-InstallerFixture $name 0 $true
        Invoke-InstallerFixture $name 7 $true
    }
    foreach ($name in $names[0..1]) {
        Invoke-InstallerFixture $name 0 $false
        Invoke-InstallerFixture $name 7 $false
    }
} finally {
    $env:GYMLOG_NO_PAUSE = $previousPause
    $env:GYMLOG_TEST_INSTALL_EXIT = $previousExit
    foreach ($name in $names) {
        $file = Join-Path $testRoot $name
        if (Test-Path -LiteralPath $file -PathType Leaf) { Remove-Item -LiteralPath $file }
    }
    foreach ($name in @('android.ps1')) {
        $file = Join-Path $helpers $name
        if (Test-Path -LiteralPath $file -PathType Leaf) { Remove-Item -LiteralPath $file }
    }
    Remove-Item -LiteralPath $helpers
    Remove-Item -LiteralPath $testRoot
}
Write-Output "PASS: $script:InstallerAssertions installer console assertions; no build, clipboard, UI or device contacted."
