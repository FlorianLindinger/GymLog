[CmdletBinding(PositionalBinding=$false)]
param(
    [Parameter(Mandatory)][ValidateSet('build','verify','release','install','build-install','device','logs','screenshot','wifi')][string]$Command,
    [Parameter(Position=0)][string]$Serial,
    [string]$Apk,
    [ValidateSet('USB','WiFi','Bluetooth','GoogleDrive')][string]$Transport = 'USB',
    [string]$DriveFolder,
    [string]$PairAddress,
    [string]$ConnectAddress,
    [switch]$ForceBuild
)
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'android_env.ps1')

try {
    $isInstall = $Command -in @('install','build-install')
    if ($DriveFolder -and (-not $isInstall -or $Transport -ne 'GoogleDrive')) {
        throw '-DriveFolder is only supported by the GoogleDrive installer.'
    }
    if ($isInstall -and $Transport -eq 'GoogleDrive') {
        if ($Serial -or $PairAddress -or $ConnectAddress) { throw 'Google Drive transfer does not use ADB or Wi-Fi addresses.' }
        . (Join-Path $PSScriptRoot 'google_drive_apk.ps1')
        $DriveFolder = Select-GoogleDriveFolder $DriveFolder
    }
    if ($isInstall -and $Transport -eq 'Bluetooth' -and ($Serial -or $PairAddress -or $ConnectAddress)) {
        throw 'Bluetooth transfer does not use an ADB serial or a Wi-Fi address.'
    }
    if ($isInstall -and $Transport -ne 'WiFi' -and ($PairAddress -or $ConnectAddress)) {
        throw 'Wi-Fi addresses are only supported by the WiFi launcher.'
    }
    $selected = $null
    if ($isInstall) {
        $action = if ($Transport -eq 'GoogleDrive') {
            if ($Command -eq 'build-install') { 'Build and copy APK' } else { 'Copy APK' }
        } elseif ($Command -eq 'build-install') { 'Build and install' } else { 'Install' }
        Write-Host "GymLog - $action via $Transport" -ForegroundColor Cyan
        if ($Transport -notin @('Bluetooth','GoogleDrive')) {
            $adb = Find-Adb
            if ($Transport -eq 'WiFi' -and ($PairAddress -or $ConnectAddress)) {
                Connect-AndroidWifi $adb $PairAddress $ConnectAddress
            }
            try {
                $connection = Resolve-InstallConnection { Find-Adb } $Serial -Transport $Transport
            } catch {
                # Only the argument-free, interactive Wi-Fi flow offers setup.
                # Explicit targets, ADB errors and ambiguous lists must still fail.
                if ($Transport -ne 'WiFi' -or $Serial -or $PairAddress -or $ConnectAddress -or
                    -not $_.Exception.Data['NoUsableDevice'] -or
                    $_.Exception.Data['AdbListingFailed'] -or
                    $env:GYMLOG_NO_PAUSE -eq '1' -or [Console]::IsInputRedirected) { throw }
                Request-AndroidWifiConnection $adb
                $connection = Resolve-InstallConnection { Find-Adb } '' -Transport WiFi
            }
            $adb = $connection.Adb
            $selected = $connection.Serial
            Write-Host "[$Transport] Connected: $selected" -ForegroundColor Green
        }
    }
    if ($Command -in @('build','build-install')) {
        Invoke-DebugBuild -ForceBuild:$ForceBuild
        if ($Command -eq 'build') { exit 0 }
    }
    if ($Command -in @('verify','release')) {
        Write-Host '[BUILD] Preparing the Android build tools...' -ForegroundColor Cyan
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
        }
        Copy-DebugApk
        exit 0
    }
    if ($isInstall) {
        if (-not $Apk) { $Apk = Join-Path $script:RepoRoot 'GymLog-debug.apk' }
        if (-not (Test-Path -LiteralPath $Apk -PathType Leaf)) { throw 'APK not found. Run advanced/build.bat first or pass -Apk PATH.' }
    }
    if ($isInstall -and $Transport -eq 'Bluetooth') {
        . (Join-Path $PSScriptRoot 'bluetooth_apk.ps1')
        Send-ApkViaBluetooth $Apk
        exit 0
    }
    if ($isInstall -and $Transport -eq 'GoogleDrive') {
        Send-ApkViaGoogleDrive $Apk $DriveFolder
        exit 0
    }
    if (-not $isInstall) { $adb = Find-Adb }
    if ($Command -eq 'wifi') {
        if (-not $PairAddress -and -not $ConnectAddress) {
            Request-AndroidWifiConnection $adb
        } else { Connect-AndroidWifi $adb $PairAddress $ConnectAddress }
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
        if (-not $Apk) { $Apk = Join-Path $script:RepoRoot 'GymLog-debug.apk' }
        if (-not (Test-Path -LiteralPath $Apk -PathType Leaf)) { throw 'APK not found. Run advanced/build.bat first or pass -Apk PATH.' }
        Write-Host "[INSTALL] Updating GymLog via $Transport..." -ForegroundColor Cyan
        $install = Invoke-Adb $adb @('-s',$selected,'install','-r',[IO.Path]::GetFullPath($Apk))
        $install.Lines | ForEach-Object { Write-Host $_ }
        if (($install.Lines -join "`n") -notmatch '(?m)^Success\r?$') { throw 'ADB did not confirm installation success. Existing app data was not removed.' }
        $launch = Invoke-Adb $adb @('-s',$selected,'shell','am','start','-n',"$script:Package/.MainActivity")
        $launch.Lines | ForEach-Object { Write-Host $_ }
        if (($launch.Lines -join "`n") -match '(?im)^Error(?:\s|:)|Exception') { throw 'APK installed, but Android could not launch this app. Check that you selected the correct APK.' }
        Write-Host '[SUCCESS] Installed and launched. Existing app data was preserved.' -ForegroundColor Green
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
} catch { Write-Host "[ERROR] $($_.Exception.Message)" -ForegroundColor Red; exit 1 }
