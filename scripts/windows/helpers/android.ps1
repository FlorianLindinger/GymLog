[CmdletBinding(PositionalBinding=$false)]
param(
    [Parameter(Mandatory)][ValidateSet('build','verify','release','install','build-install','device','logs','screenshot','wifi')][string]$Command,
    [Parameter(Position=0)][string]$Serial,
    [string]$Apk,
    [switch]$Bluetooth,
    [switch]$NoBluetoothFallback,
    [string]$PairAddress,
    [string]$ConnectAddress
)
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'android_env.ps1')

try {
    if ($Bluetooth -and ($Command -notin @('install','build-install') -or $Serial -or $PairAddress -or $ConnectAddress -or $NoBluetoothFallback)) {
        throw 'Use -Bluetooth only with install/build-install and without an ADB serial or Wi-Fi address.'
    }
    $isInstall = $Command -in @('install','build-install')
    if ($isInstall) {
        $action = if ($Command -eq 'build-install') { 'Build and install' } else { 'Install' }
        Write-Host "GymLog - $action via USB, Wi-Fi or Bluetooth"
    }
    if ($Command -in @('build','verify','release','build-install')) {
        Initialize-Build
        if ($Command -eq 'release') {
            Build-SignedRelease
            exit 0
        } elseif ($Command -eq 'verify') {
            Get-ChildItem (Join-Path $script:RepoRoot 'scripts/windows/tests') -Filter '*.Tests.ps1' | ForEach-Object {
                & (Join-Path $PSHOME 'powershell.exe') -STA -NoProfile -ExecutionPolicy Bypass -File $_.FullName
                if ($LASTEXITCODE -ne 0) { throw "Helper tests failed: $($_.Name)" }
            }
            Invoke-Gradle @(':app:compileDebugKotlin', ':app:testDebugUnitTest', ':app:lintDebug', ':app:assembleDebug')
            Push-Location $script:RepoRoot
            try { git diff --check; if ($LASTEXITCODE -ne 0) { throw 'Git whitespace check failed.' } }
            finally { Pop-Location }
        } else { Invoke-Gradle @(':app:assembleDebug') }
        Copy-DebugApk
        if ($Command -ne 'build-install') { exit 0 }
    }
    if ($isInstall) {
        if (-not $Apk) { $Apk = Join-Path $script:RepoRoot 'app/artifacts/debug/app-debug.apk' }
        if (-not (Test-Path -LiteralPath $Apk -PathType Leaf)) { throw 'APK not found. Run build.bat first or pass -Apk PATH.' }
    }
    $selected = $null
    if ($isInstall -and -not $Bluetooth) {
        $connection = Resolve-InstallConnection { Find-Adb } $Serial -NoBluetoothFallback:$NoBluetoothFallback
        if ($connection.Serial) { $adb=$connection.Adb; $selected=$connection.Serial }
        else {
            Write-Host $connection.Reason
            Write-Host 'USB/Wi-Fi installation is unavailable. Falling back to Bluetooth APK transfer.'
            $Bluetooth = $true
        }
    }
    if ($Bluetooth) {
        . (Join-Path $PSScriptRoot 'bluetooth_apk.ps1')
        Send-ApkViaBluetooth $Apk
        exit 0
    }
    if (-not $isInstall) { $adb = Find-Adb }
    if ($Command -eq 'wifi') {
        if (-not $PairAddress -and -not $ConnectAddress) { throw 'Supply -PairAddress IP:PAIRING_PORT and/or -ConnectAddress IP:CONNECTION_PORT. See scripts/windows/README.md.' }
        if ($PairAddress) {
            Assert-WifiAddress $PairAddress
            Write-Host 'Enter the pairing code at the ADB prompt; it is not saved by this helper.'
            & $adb pair $PairAddress
            if ($LASTEXITCODE -ne 0) { throw 'Wireless pairing failed.' }
        }
        if ($ConnectAddress) {
            Assert-WifiAddress $ConnectAddress
            $connection = Invoke-Adb $adb @('connect', $ConnectAddress)
            $connection.Lines | ForEach-Object { Write-Host $_ }
            # adb connect can report a connection failure with exit code zero.
            if (($connection.Lines -join ' ') -notmatch '(?:already )?connected to ') { throw 'ADB did not confirm a connection.' }
        }
        (Invoke-Adb $adb @('devices','-l')).Lines | ForEach-Object { Write-Host $_ }
        exit 0
    }
    if (-not $isInstall) { $selected = Select-AndroidDevice $adb $Serial }
    Write-Host "Device: $selected"
    if ($Command -eq 'device') {
        foreach ($prop in @('ro.product.manufacturer','ro.product.model','ro.build.version.release','ro.build.version.sdk')) {
            Write-Host "$prop : $((Invoke-Adb $adb @('-s',$selected,'shell','getprop',$prop)).Lines -join ' ')"
        }
        $packageInfo = Invoke-Adb $adb @('-s',$selected,'shell','dumpsys','package',$script:Package)
        $versions = @($packageInfo.Lines | Where-Object { $_ -match 'versionName=|versionCode=' })
        if ($versions.Count) { $versions | ForEach-Object { Write-Host $_.Trim() } } else { Write-Host 'App is not installed.' }
        exit 0
    }
    if ($Command -in @('install','build-install')) {
        if (-not $Apk) { $Apk = Join-Path $script:RepoRoot 'app/artifacts/debug/app-debug.apk' }
        if (-not (Test-Path -LiteralPath $Apk -PathType Leaf)) { throw 'APK not found. Run build.bat first or pass -Apk PATH.' }
        $install = Invoke-Adb $adb @('-s',$selected,'install','-r',[IO.Path]::GetFullPath($Apk))
        $install.Lines | ForEach-Object { Write-Host $_ }
        if (($install.Lines -join "`n") -notmatch '(?m)^Success\r?$') { throw 'ADB did not confirm installation success. Existing app data was not removed.' }
        $launch = Invoke-Adb $adb @('-s',$selected,'shell','am','start','-n',"$script:Package/.MainActivity")
        $launch.Lines | ForEach-Object { Write-Host $_ }
        if (($launch.Lines -join "`n") -match '(?im)^Error(?:\s|:)|Exception') { throw 'APK installed, but Android could not launch this app. Check that you selected the correct APK.' }
        Write-Host 'Installed and launched. Existing app data was preserved.'
        exit 0
    }
    if ($Command -eq 'logs') {
        Invoke-Adb $adb @('-s',$selected,'shell','am','start','-n',"$script:Package/.MainActivity") | Out-Null
        $process = (Invoke-Adb $adb @('-s',$selected,'shell','pidof','-s',$script:Package)).Lines -join ''
        if ($process.Trim() -notmatch '^\d+$') { throw 'App process not found. Install the APK and open it first.' }
        Write-Host 'Streaming this app process only. Ctrl+C stops logging. No force-stop or global log clearing is performed.'
        & $adb -s $selected logcat "--pid=$($process.Trim())" -v threadtime
        exit $LASTEXITCODE
    }
    if ($Command -eq 'screenshot') {
        $directory = Join-Path $script:AppRoot 'screenshots'
        New-Item -ItemType Directory -Force -Path $directory | Out-Null
        $destination = Join-Path $directory ("GymLog-$(Get-Date -Format yyyyMMdd-HHmmss-fff).png")
        # Windows PowerShell 5 text redirection corrupts PNG bytes. Copy the raw stream.
        $info = New-Object Diagnostics.ProcessStartInfo
        $info.FileName = $adb
        $info.Arguments = "-s $selected exec-out screencap -p" # Serial was validated above.
        $info.UseShellExecute = $false
        $info.CreateNoWindow = $true
        $info.RedirectStandardOutput = $true
        $process = New-Object Diagnostics.Process
        $process.StartInfo = $info
        $file = [IO.File]::Create($destination)
        try {
            if (-not $process.Start()) { throw 'Could not start ADB.' }
            $process.StandardOutput.BaseStream.CopyTo($file)
            $process.WaitForExit()
            if ($process.ExitCode -ne 0) { throw 'ADB screenshot failed.' }
        } finally { $file.Dispose(); $process.Dispose() }
        $png = [IO.File]::ReadAllBytes($destination)
        if ($png.Length -lt 8 -or [BitConverter]::ToString($png[0..7]) -ne '89-50-4E-47-0D-0A-1A-0A') {
            Remove-Item -LiteralPath $destination
            throw 'Screenshot output was not a PNG.'
        }
        Write-Host "Screenshot: $destination"
    }
} catch { [Console]::Error.WriteLine("ERROR: $($_.Exception.Message)"); exit 1 }
