param([string]$HelperPath = (Join-Path $PSScriptRoot '../helpers/adb_devices.ps1'))
$ErrorActionPreference='Stop'
. $HelperPath
$script:ConnectionAssertions=0
function Assert-Connection($value,[string]$message) {
    $script:ConnectionAssertions++
    if (-not $value) { throw $message }
}
function Assert-ConnectionFailure([scriptblock]$action) {
    $failed=$false
    try { & $action | Out-Null } catch { $failed=$true }
    Assert-Connection $failed 'Expected a device-selection error instead of Bluetooth fallback'
}
$script:State=[pscustomobject]@{ExitCode=0;Lines=@('device')}
$script:FailListing=$false
function Invoke-Adb([string]$Adb,[string[]]$Arguments,[switch]$AllowFailure) {
    if ($Arguments -contains 'get-state') { return $script:State }
    if ($script:FailListing) { throw 'ADB server unavailable' }
    return [pscustomobject]@{ExitCode=0;Lines=$script:Connections}
}
$script:Connections=@('List of devices attached','192.168.1.9:37123 device','usb-phone device')
$result=Resolve-InstallConnection { 'mock-adb' } ''
Assert-Connection ($result.Serial -eq 'usb-phone') 'USB must take priority over connected Wi-Fi'
Assert-Connection ($result.Adb -eq 'mock-adb' -and -not $result.Reason) 'Successful connection must preserve ADB and have no fallback reason'
$script:Connections=@('List of devices attached','192.168.1.9:37123 device','usb-phone unauthorized')
Assert-Connection ((Resolve-InstallConnection { 'mock-adb' } '').Serial -eq '192.168.1.9:37123') 'Wi-Fi must be used when USB is not authorized'
foreach ($network in @('adb-phone-123._adb-tls-connect._tcp','adb-phone-123_adb-tls-connect._tcp','phone._adb._tcp','[fe80::1]:37123')) {
    $script:Connections=@('List of devices attached',"$network device",'usb-phone device')
    Assert-Connection ((Resolve-InstallConnection { 'mock-adb' } '').Serial -eq 'usb-phone') 'Network/mDNS serials must not displace USB'
    $script:Connections=@('List of devices attached',"$network device")
    Assert-Connection ((Resolve-InstallConnection { 'mock-adb' } '').Serial -eq $network) 'A single Wi-Fi connection must be usable'
}
foreach ($lines in @(
    ,@('List of devices attached',''),
    ,@('List of devices attached','phone unauthorized'),
    ,@('List of devices attached','phone offline')
)) {
    $script:Connections=$lines
    $result=Resolve-InstallConnection { 'mock-adb' } ''
    Assert-Connection (-not $result.Serial -and $result.Reason) 'No usable device must produce a Bluetooth fallback reason'
}
$result=Resolve-InstallConnection { throw 'ADB unavailable' } ''
Assert-Connection ($result.Reason -eq 'ADB unavailable') 'Missing ADB must allow Bluetooth fallback'
$script:FailListing=$true
Assert-Connection ((Resolve-InstallConnection { 'mock-adb' } '').Reason -eq 'ADB server unavailable') 'Unavailable ADB server must allow Bluetooth fallback'
$script:FailListing=$false
Assert-ConnectionFailure { Resolve-InstallConnection { throw 'ADB unavailable' } '' -NoBluetoothFallback }
Assert-ConnectionFailure { Resolve-InstallConnection { 'mock-adb' } '' -NoBluetoothFallback }
$script:Connections=@('List of devices attached','usb-one device','usb-two device','192.168.1.9:37123 device')
Assert-ConnectionFailure { Resolve-InstallConnection { 'mock-adb' } '' }
$script:Connections=@('List of devices attached','192.168.1.8:37123 device','192.168.1.9:37123 device')
Assert-ConnectionFailure { Resolve-InstallConnection { 'mock-adb' } '' }
Assert-Connection ((Resolve-InstallConnection { 'mock-adb' } '192.168.1.9:37123').Serial -eq '192.168.1.9:37123') 'Explicit serial must override transport priority'
$script:State=[pscustomobject]@{ExitCode=1;Lines=@('error: device not found')}
Assert-ConnectionFailure { Resolve-InstallConnection { 'mock-adb' } 'missing-phone' }
Write-Output "PASS: $script:ConnectionAssertions USB/Wi-Fi/Bluetooth fallback assertions; no device contacted."
