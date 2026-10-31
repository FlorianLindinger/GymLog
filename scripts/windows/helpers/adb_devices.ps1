function Invoke-Adb([string]$Adb, [string[]]$Arguments, [switch]$AllowFailure) {
    $oldPreference = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try { $lines = @(& $Adb @Arguments 2>&1 | ForEach-Object { "$_" }); $code = $LASTEXITCODE }
    finally { $ErrorActionPreference = $oldPreference }
    if ($code -ne 0 -and -not $AllowFailure) { throw "ADB failed (exit $code): $($lines -join [Environment]::NewLine)" }
    return [pscustomobject]@{ ExitCode = $code; Lines = $lines }
}

function Assert-DeviceSerial([string]$Serial) {
    if ($Serial.StartsWith('-') -or $Serial -notmatch '^[A-Za-z0-9_.:\[\]-]+$') { throw 'Invalid device serial. Use the exact serial from adb devices -l.' }
}

function Get-DeviceConnectionHelp([string[]]$Lines) {
    $help = @()
    if ($Lines -match '^\S+\s+unauthorized(?:\s|$)') {
        $help += 'Phone detected, but USB debugging is not authorized. Unlock the phone and accept its Allow USB debugging prompt. Reconnect the cable if the prompt is missing.'
    }
    if ($Lines -match '^\S+\s+offline(?:\s|$)') {
        $help += 'Phone detected, but ADB reports it offline. Unlock it, reconnect the cable, and toggle USB debugging off and on if needed.'
    }
    if (-not $help.Count) {
        $help += 'No Android device detected by ADB. The device list is empty or has no usable connection.'
    }
    $help += 'USB: enable Developer options > USB debugging, unlock the phone, and connect directly with a USB data cable. Try another cable/port. On Windows, check Device Manager for a missing phone ADB driver.'
    $help += 'Wi-Fi: use WiFi.bat -PairAddress IP:PAIRING_PORT -ConnectAddress IP:CONNECTION_PORT, using the addresses shown in Android Wireless debugging.'
    $help += 'Choose USB.bat, WiFi.bat, or Bluetooth.bat explicitly. Connection types never fall back to one another.'
    $help += 'Install an already-built APK with advanced/install_usb.bat, advanced/install_wifi.bat, or advanced/send_bluetooth.bat.'
    $help += 'More help: scripts/windows/README.md (repository root).'
    $help += "ADB reports:`n$($Lines -join "`n")"
    return $help -join "`n"
}

function Get-AdbTransport([string]$Serial) {
    if ($Serial -match ':|_adb(?:-tls-connect)?\._tcp') { return 'WiFi' }
    if ($Serial -match '^emulator-') { return 'Emulator' }
    return 'USB'
}

function Select-AndroidDevice([string]$Adb, [string]$Serial, [ValidateSet('Any','USB','WiFi')][string]$Transport = 'Any') {
    if ($Serial) {
        Assert-DeviceSerial $Serial
        if ($Transport -ne 'Any' -and (Get-AdbTransport $Serial) -ne $Transport) {
            throw "Selected serial '$Serial' is not a $Transport connection. Use the matching launcher."
        }
        $result = Invoke-Adb $Adb @('-s', $Serial, 'get-state') -AllowFailure
        if ($result.ExitCode -ne 0 -or ($result.Lines -join '').Trim() -ne 'device') {
            $devices = Invoke-Adb $Adb @('devices', '-l')
            throw "Selected device '$Serial' is not available. Check that its serial matches the current device list.`n$(Get-DeviceConnectionHelp $devices.Lines)"
        }
        return $Serial
    }
    try { $result = Invoke-Adb $Adb @('devices', '-l') }
    catch { $_.Exception.Data['NoUsableDevice']=$true; throw }
    $devices = @($result.Lines | ForEach-Object { if ($_ -match '^(\S+)\s+device(?:\s|$)') { $Matches[1] } })
    if ($Transport -ne 'Any') { $devices = @($devices | Where-Object { (Get-AdbTransport $_) -eq $Transport }) }
    if ($devices.Count -eq 0) {
        $failure = New-Object InvalidOperationException ("No authorized $Transport connection is available. No other transport will be tried.`n" + (Get-DeviceConnectionHelp $result.Lines))
        $failure.Data['NoUsableDevice'] = $true
        throw $failure
    }
    if ($devices.Count -gt 1) { throw "Multiple devices are connected; pass a serial explicitly: $($devices -join ', ')" }
    Assert-DeviceSerial $devices[0]
    return $devices[0]
}

function Resolve-InstallConnection([scriptblock]$AdbResolver, [string]$Serial, [ValidateSet('USB','WiFi')][string]$Transport = 'USB') {
    $adb = & $AdbResolver
    $selected = Select-AndroidDevice $adb $Serial -Transport $Transport
    return [pscustomobject]@{ Adb=$adb; Serial=$selected }
}

