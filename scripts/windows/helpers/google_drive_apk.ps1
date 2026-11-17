function Get-GoogleDriveSettingsPath {
    return Join-Path ([IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../../..'))) '.tools/google-drive-folder.txt'
}

function New-GoogleDriveFolderDialog {
    Add-Type -AssemblyName System.Windows.Forms
    return New-Object System.Windows.Forms.FolderBrowserDialog
}

function Select-GoogleDriveFolder([string]$Folder) {
    if (-not $Folder) { $Folder = $env:GYMLOG_DRIVE_FOLDER }
    $settingsPath = Get-GoogleDriveSettingsPath
    if (-not $Folder) {
        $previous = ''
        if (Test-Path -LiteralPath $settingsPath -PathType Leaf) {
            try { $previous = [IO.File]::ReadAllText($settingsPath).Trim() }
            catch { Write-Warning 'The saved Drive folder could not be read. Choose a folder again.' }
        }
        $dialog = New-GoogleDriveFolderDialog
        $dialog.Description = 'Choose a Google Drive synced folder (the previous folder is preselected)'
        $dialog.ShowNewFolderButton = $false
        if ($previous -and (Test-Path -LiteralPath $previous -PathType Container)) {
            $dialog.SelectedPath = $previous
        }
        try {
            if ($dialog.ShowDialog().ToString() -ne 'OK') {
                throw 'Google Drive folder selection cancelled. No APK was sent.'
            }
            $Folder = $dialog.SelectedPath
        } finally { $dialog.Dispose() }
    }
    if (-not (Test-Path -LiteralPath $Folder -PathType Container)) {
        throw 'Google Drive folder does not exist. Choose a folder synchronized by Google Drive for desktop.'
    }
    $Folder = (Get-Item -LiteralPath $Folder).FullName
    try {
        [IO.Directory]::CreateDirectory((Split-Path -Parent $settingsPath)) | Out-Null
        [IO.File]::WriteAllText($settingsPath, $Folder, [Text.UTF8Encoding]::new($false))
    } catch { Write-Warning 'The selected folder could not be remembered; this transfer can still continue.' }
    return $Folder
}

# Copy to the user's chosen sync folder; the Drive client owns upload and authentication.
function Send-ApkViaGoogleDrive([string]$Apk, [string]$Folder) {
    if (-not (Test-Path -LiteralPath $Apk -PathType Leaf)) { throw 'APK not found.' }
    if (-not (Test-Path -LiteralPath $Folder -PathType Container)) { throw 'Google Drive folder is no longer available.' }
    $Apk = (Get-Item -LiteralPath $Apk).FullName
    $Folder = (Get-Item -LiteralPath $Folder).FullName
    $number = 1L
    Get-ChildItem -LiteralPath $Folder -File -Filter 'GymLog-*.apk' | ForEach-Object {
        $existing = 0L
        if ($_.Name -match '^GymLog-(\d+)\.apk$' -and [long]::TryParse($Matches[1], [ref]$existing)) {
            $number = [Math]::Max($number, $existing + 1L)
        }
    }
    while ($true) {
        $destination = Join-Path $Folder "GymLog-$number.apk"
        try {
            # Create without overwrite, including when another installer uses the same folder.
            [IO.File]::Copy($Apk, $destination, $false)
            break
        } catch [IO.IOException] {
            $code = $_.Exception.GetBaseException().HResult -band 0xFFFF
            if ($code -notin @(80, 183) -or -not (Test-Path -LiteralPath $destination -PathType Leaf)) { throw }
            $number++
        }
    }
    Write-Host "[COPIED] $destination" -ForegroundColor Cyan
    Write-Host '[ACTION REQUIRED] Wait for Google Drive sync. On the phone, open Drive, download this APK and confirm installation. Cloud upload and installation are not verified here.' -ForegroundColor Yellow
}
