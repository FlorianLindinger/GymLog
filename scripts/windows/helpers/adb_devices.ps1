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
    $help += 'Wi-Fi: first pair and connect with connect_wifi.bat using the IP and ports shown in Android Wireless debugging.'
    $help += 'Bluetooth alternative: run install_via_usb_wifi_or_bluetooth.bat -Bluetooth to send the APK and finish installation on the phone without ADB.'
    $help += 'Once connected, run device_check.bat, then install_via_usb_wifi_or_bluetooth.bat to install the already-built APK without rebuilding.'
    $help += 'More help: scripts/windows/README.md (repository root).'
    $help += "ADB reports:`n$($Lines -join "`n")"
    return $help -join "`n"
}

function Select-AndroidDevice([string]$Adb, [string]$Serial, [switch]$PreferUsb) {
    if ($Serial) {
        Assert-DeviceSerial $Serial
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
    if ($devices.Count -eq 0) {
        $failure = New-Object InvalidOperationException (Get-DeviceConnectionHelp $result.Lines)
        $failure.Data['NoUsableDevice'] = $true
        throw $failure
    }
    if ($PreferUsb) {
        # Network serials are IP:port or an Android mDNS service name.
        # Local emulators stay in the local/USB group and still require an
        # explicit serial when there is more than one local target.
        $usb = @($devices | Where-Object { $_ -notmatch ':|_adb(?:-tls-connect)?\._tcp' })
        if ($usb.Count -gt 0) { $devices = $usb }
    }
    if ($devices.Count -gt 1) { throw "Multiple devices are connected; pass a serial explicitly: $($devices -join ', ')" }
    Assert-DeviceSerial $devices[0]
    return $devices[0]
}

function Resolve-InstallConnection([scriptblock]$AdbResolver, [string]$Serial, [switch]$NoBluetoothFallback) {
    try { $adb = & $AdbResolver }
    catch {
        if ($Serial -or $NoBluetoothFallback) { throw }
        return [pscustomobject]@{ Adb=$null; Serial=$null; Reason=$_.Exception.Message }
    }
    try { $selected = Select-AndroidDevice $adb $Serial -PreferUsb }
    catch {
        # An explicit device or ambiguous selection must not switch targets.
        if ($Serial -or $NoBluetoothFallback -or -not $_.Exception.Data['NoUsableDevice']) { throw }
        return [pscustomobject]@{ Adb=$null; Serial=$null; Reason=$_.Exception.Message }
    }
    return [pscustomobject]@{ Adb=$adb; Serial=$selected; Reason=$null }
}

