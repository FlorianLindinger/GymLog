param([string]$HelperPath = (Join-Path $PSScriptRoot '../helpers/adb_devices.ps1'))
$ErrorActionPreference = 'Stop'
. $HelperPath
$script:ConnectionAssertions = 0
function Assert-Connection($value, [string]$message) {
    $script:ConnectionAssertions++
    if (-not $value) { throw $message }
}
function Assert-ConnectionFailure([scriptblock]$action) {
    $failed = $false
    try { & $action | Out-Null } catch { $failed = $true }
    Assert-Connection $failed 'Expected a transport-selection failure; another transport must never be used'
}
$script:State = [pscustomobject]@{ ExitCode = 0; Lines = @('device') }
$script:FailListing = $false
function Invoke-Adb([string]$Adb, [string[]]$Arguments, [switch]$AllowFailure) {
    if ($Arguments -contains 'get-state') { return $script:State }
    if ($script:FailListing) { throw 'ADB server unavailable' }
    return [pscustomobject]@{ ExitCode = 0; Lines = $script:Connections }
}
$script:Connections = @('List of devices attached', '192.168.1.9:37123 device', 'usb-phone device', 'emulator-5554 device')
Assert-Connection ((Resolve-InstallConnection { 'mock-adb' } '' -Transport USB).Serial -eq 'usb-phone') 'USB must ignore Wi-Fi and emulators'
Assert-Connection ((Resolve-InstallConnection { 'mock-adb' } '' -Transport WiFi).Serial -eq '192.168.1.9:37123') 'Wi-Fi must ignore USB and emulators'
foreach ($network in @('adb-phone-123._adb-tls-connect._tcp','adb-phone-123_adb-tls-connect._tcp','phone._adb._tcp','[fe80::1]:37123')) {
    $script:Connections = @('List of devices attached', "$network device", 'usb-phone device')
    Assert-Connection ((Resolve-InstallConnection { 'mock-adb' } '' -Transport WiFi).Serial -eq $network) 'Wi-Fi must recognize mDNS and IPv6 connections'
    Assert-Connection ((Resolve-InstallConnection { 'mock-adb' } '' -Transport USB).Serial -eq 'usb-phone') 'Network/mDNS serials must not be selected for USB'
}
foreach ($transport in @('USB', 'WiFi')) {
    foreach ($lines in @(
        ,@('List of devices attached', ''),
        ,@('List of devices attached', 'phone unauthorized'),
        ,@('List of devices attached', 'phone offline'),
        ,@('List of devices attached', 'emulator-5554 device')
    )) {
        $script:Connections = $lines
        Assert-ConnectionFailure { Resolve-InstallConnection { 'mock-adb' } '' -Transport $transport }
    }
    Assert-ConnectionFailure { Resolve-InstallConnection { throw 'ADB unavailable' } '' -Transport $transport }
    $script:FailListing = $true
    Assert-ConnectionFailure { Resolve-InstallConnection { 'mock-adb' } '' -Transport $transport }
    $script:FailListing = $false
}
$script:Connections = @('List of devices attached', '192.168.1.9:37123 device')
Assert-ConnectionFailure { Resolve-InstallConnection { 'mock-adb' } '' -Transport USB }
$script:Connections = @('List of devices attached', 'usb-phone device')
Assert-ConnectionFailure { Resolve-InstallConnection { 'mock-adb' } '' -Transport WiFi }
Assert-ConnectionFailure { Resolve-InstallConnection { 'mock-adb' } '192.168.1.9:37123' -Transport USB }
Assert-ConnectionFailure { Resolve-InstallConnection { 'mock-adb' } 'usb-phone' -Transport WiFi }
$script:Connections = @('List of devices attached', 'usb-one device', 'usb-two device', '192.168.1.9:37123 device')
Assert-ConnectionFailure { Resolve-InstallConnection { 'mock-adb' } '' -Transport USB }
Assert-Connection ((Resolve-InstallConnection { 'mock-adb' } 'usb-two' -Transport USB).Serial -eq 'usb-two') 'An explicit USB serial resolves USB ambiguity'
$script:Connections = @('List of devices attached', '192.168.1.8:37123 device', '192.168.1.9:37123 device')
Assert-ConnectionFailure { Resolve-InstallConnection { 'mock-adb' } '' -Transport WiFi }
Assert-Connection ((Resolve-InstallConnection { 'mock-adb' } '192.168.1.9:37123' -Transport WiFi).Serial -eq '192.168.1.9:37123') 'An explicit Wi-Fi serial resolves Wi-Fi ambiguity'
$script:State = [pscustomobject]@{ ExitCode = 1; Lines = @('error: device not found') }
Assert-ConnectionFailure { Resolve-InstallConnection { 'mock-adb' } 'missing-phone' -Transport USB }
Write-Output "PASS: $script:ConnectionAssertions strict USB/Wi-Fi transport assertions; no device contacted."
