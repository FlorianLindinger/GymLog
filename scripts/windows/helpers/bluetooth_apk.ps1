[CmdletBinding()]
param([string]$ApkPath)

function Find-BluetoothFileTransfer {
    if (-not $env:SystemRoot) { throw 'Bluetooth APK transfer requires Windows.' }
    $wizard = Join-Path $env:SystemRoot 'System32/fsquirt.exe'
    if (-not (Test-Path -LiteralPath $wizard -PathType Leaf)) {
        throw 'Windows Bluetooth File Transfer (fsquirt.exe) is unavailable. Use USB/Wi-Fi installation or transfer the APK manually.'
    }
    return $wizard
}

function Send-ApkViaBluetooth([string]$File) {
    if (-not $File -or -not (Test-Path -LiteralPath $File -PathType Leaf)) {
        throw 'APK not found. Build the app first, then retry Bluetooth transfer.'
    }
    $apk = Get-Item -LiteralPath $File
    if ($apk.Extension -ine '.apk') { throw 'Select an .apk file. An Android App Bundle (.aab) cannot be installed on the phone directly.' }
    $wizard = Find-BluetoothFileTransfer
    Write-Host 'Bluetooth APK transfer (installation is completed on the phone)'
    Write-Host '1. Turn on Bluetooth and pair the phone with this PC in Windows Settings.'
    Write-Host '2. In Bluetooth File Transfer, choose Send files and select your phone.'
    Write-Host '3. Click Browse and paste this exact APK path into the file-name field:'
    Write-Host $apk.FullName
    Write-Host '4. Accept the transfer on the phone, open the received APK, and confirm Install.'
    Write-Host '   Allow this file manager to install apps if Android asks. Keep the existing app installed to preserve its data.'
    Write-Host 'If the phone rejects APK files over Bluetooth, use USB/Wi-Fi or manual file transfer.'
    # The native wizard handles device selection, pairing and transfer consent.
    # Do not assume undocumented fsquirt arguments can preselect an APK.
    Start-Process -FilePath $wizard -WindowStyle Normal -ErrorAction Stop | Out-Null
    Write-Host 'Bluetooth file-transfer wizard opened. Transfer and installation are pending; their success cannot be checked by this helper.'
}

if ($MyInvocation.InvocationName -ne '.') {
    $ErrorActionPreference = 'Stop'
    try { Send-ApkViaBluetooth $ApkPath; exit 0 }
    catch { [Console]::Error.WriteLine("ERROR: $($_.Exception.Message)"); exit 1 }
}
