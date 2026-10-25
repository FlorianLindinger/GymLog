param([string]$HelperPath = (Join-Path $PSScriptRoot '../helpers/bluetooth_apk.ps1'))
$ErrorActionPreference = 'Stop'
. $HelperPath
$script:BluetoothAssertions = 0
$script:Launches = @()
$script:ClipboardValues = @()
$script:Events = @()
$script:FailClipboard = $false
$script:FailPersistent = $false
function Assert-Bluetooth($value, [string]$message) {
    $script:BluetoothAssertions++
    if (-not $value) { throw $message }
}
function Assert-BluetoothFailure([scriptblock]$action, [string]$pattern) {
    $message = ''
    try { & $action 6>$null } catch { $message = $_.Exception.Message }
    Assert-Bluetooth ($message -match $pattern) "Expected failure matching: $pattern"
}
# No UI, Bluetooth radio, ADB process, or real transfer is used by these tests.
function Find-BluetoothFileTransfer { return 'mock-fsquirt.exe' }
function Set-PersistentClipboardText([string]$Text) {
    $script:Events += 'clipboard'
    if ($script:FailClipboard -or $script:FailPersistent) { throw 'Mock persistent clipboard unavailable' }
    $script:ClipboardValues += $Text
}
function Set-Clipboard {
    param([string]$Value, [string]$ErrorAction)
    $script:Events += 'clipboard-fallback'
    if ($script:FailClipboard) { throw 'Mock clipboard unavailable' }
    $script:ClipboardValues += $Value
}
function Get-Clipboard {
    param([switch]$Raw, [string]$ErrorAction)
    return $script:ClipboardValues[-1]
}
function Start-Process {
    param([string]$FilePath, [string]$WindowStyle, [string]$ErrorAction)
    $script:Launches += [pscustomobject]@{ FilePath=$FilePath; WindowStyle=$WindowStyle }
    $script:Events += 'wizard'
    if ($script:FailLaunch) { throw 'Mock wizard launch failed' }
}
$script:FailLaunch = $false
$testRoot = Join-Path ([IO.Path]::GetTempPath()) ('bluetooth-apk-tests-' + [Guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $testRoot | Out-Null
try {
    $apk = Join-Path $testRoot 'test app [debug].apk'
    [IO.File]::WriteAllText($apk, 'test fixture')
    $before = (Get-FileHash -LiteralPath $apk -Algorithm SHA256).Hash
    Send-ApkViaBluetooth $apk 6>$null
    Assert-Bluetooth ($script:Launches.Count -eq 1) 'The Bluetooth wizard must open once'
    Assert-Bluetooth ($script:Launches[0].FilePath -eq 'mock-fsquirt.exe') 'Only the Bluetooth wizard should be launched'
    Assert-Bluetooth ($script:Launches[0].WindowStyle -eq 'Normal') 'The interactive wizard must be visible'
    Assert-Bluetooth ($script:ClipboardValues.Count -eq 1 -and $script:ClipboardValues[0] -eq [IO.Path]::GetFullPath($apk)) 'Copy the exact unquoted APK path, including spaces and brackets'
    Assert-Bluetooth (($script:Events -join ',') -eq 'clipboard,wizard') 'Copy the path before opening the wizard'
    Assert-Bluetooth ((Get-FileHash -LiteralPath $apk -Algorithm SHA256).Hash -eq $before) 'The APK must not be modified'
    Assert-BluetoothFailure { Send-ApkViaBluetooth '' } 'APK not found'
    Assert-BluetoothFailure { Send-ApkViaBluetooth (Join-Path $testRoot 'missing.apk') } 'APK not found'
    Assert-BluetoothFailure { Send-ApkViaBluetooth $testRoot } 'APK not found'
    $bundle = Join-Path $testRoot 'test.aab'
    [IO.File]::WriteAllText($bundle, 'test bundle')
    Assert-BluetoothFailure { Send-ApkViaBluetooth $bundle } 'Select an .apk file'
    Assert-Bluetooth ($script:Launches.Count -eq 1) 'Invalid input must not open a wizard'
    Assert-Bluetooth ($script:ClipboardValues.Count -eq 1) 'Invalid input must not modify the clipboard'
    $script:FailClipboard = $true
    $output = Send-ApkViaBluetooth $apk 6>&1 | Out-String
    Assert-Bluetooth ($script:Launches.Count -eq 2) 'A clipboard failure must not prevent Bluetooth transfer'
    Assert-Bluetooth ($output.Contains('Copy the printed path below manually.') -and $output.Contains([IO.Path]::GetFullPath($apk))) 'Clipboard failure must leave the exact path available for manual copying'
    Assert-Bluetooth (-not $output.Contains('APK path copied to the clipboard.')) 'Do not claim a failed clipboard copy succeeded'
    $script:FailClipboard = $false
    $script:FailPersistent = $true
    $script:Events = @()
    Send-ApkViaBluetooth $apk 6>$null
    Assert-Bluetooth (($script:Events -join ',') -eq 'clipboard,clipboard-fallback,wizard') 'Use and verify the cmdlet fallback if the persistent clipboard API is unavailable'
    $script:FailPersistent = $false
    $script:FailLaunch = $true
    Assert-BluetoothFailure { Send-ApkViaBluetooth $apk } 'Mock wizard launch failed'
    function Find-BluetoothFileTransfer { throw 'Windows Bluetooth File Transfer (fsquirt.exe) is unavailable.' }
    Assert-BluetoothFailure { Send-ApkViaBluetooth $apk } 'fsquirt.exe.*unavailable'
} finally {
    # Delete only the explicitly named fixtures created above, never recurse.
    foreach ($name in @('test app [debug].apk', 'test.aab')) {
        $fixture = Join-Path $testRoot $name
        if (Test-Path -LiteralPath $fixture -PathType Leaf) { Remove-Item -LiteralPath $fixture }
    }
    Remove-Item -LiteralPath $testRoot
}
Write-Output "PASS: $script:BluetoothAssertions Bluetooth helper assertions; no UI or device contacted."
