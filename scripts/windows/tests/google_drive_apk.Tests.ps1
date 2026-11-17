param([string]$WindowsPath = (Join-Path $PSScriptRoot '..'))
$ErrorActionPreference = 'Stop'
. (Join-Path $WindowsPath 'helpers/google_drive_apk.ps1')
$root = Join-Path ([IO.Path]::GetTempPath()) ('gymlog-drive-tests-' + [Guid]::NewGuid().ToString('N'))
$savedFolder = $env:GYMLOG_DRIVE_FOLDER
function Get-GoogleDriveSettingsPath { return Join-Path $root 'settings/drive-folder.txt' }
$script:DialogResult = 'OK'
$script:DialogChoice = ''
$script:InitialFolder = ''
$script:Disposed = 0
function New-GoogleDriveFolderDialog {
    $dialog = [pscustomobject]@{ SelectedPath=''; Description=''; ShowNewFolderButton=$false }
    $dialog | Add-Member ScriptMethod ShowDialog {
        $script:InitialFolder = $this.SelectedPath
        if ($script:DialogChoice) { $this.SelectedPath = $script:DialogChoice }
        return $script:DialogResult
    }
    $dialog | Add-Member ScriptMethod Dispose { $script:Disposed++ }
    return $dialog
}
try {
    $folder = Join-Path $root 'sync folder with spaces'
    New-Item -ItemType Directory -Path $folder -Force | Out-Null
    $apk = Join-Path $root 'source with spaces.apk'
    [IO.File]::WriteAllBytes($apk, [byte[]](0, 1, 127, 128, 255))
    if ((Select-GoogleDriveFolder $folder) -ne $folder) { throw 'Explicit sync folder was not preserved.' }
    if ([IO.File]::ReadAllText((Get-GoogleDriveSettingsPath)) -ne $folder) { throw 'Selected folder was not saved.' }
    $env:GYMLOG_DRIVE_FOLDER = ''
    if ((Select-GoogleDriveFolder) -ne $folder -or $script:InitialFolder -ne $folder) {
        throw 'Ordinary GUI launch must offer the saved folder as its initial selection.'
    }
    $changedFolder = Join-Path $root 'another sync folder'
    New-Item -ItemType Directory -Path $changedFolder | Out-Null
    $script:DialogChoice = $changedFolder
    if ((Select-GoogleDriveFolder) -ne $changedFolder) { throw 'GUI folder change was not honored.' }
    if ([IO.File]::ReadAllText((Get-GoogleDriveSettingsPath)) -ne $changedFolder) { throw 'Changed folder was not saved.' }
    $script:DialogResult = 'Cancel'
    $failed = $false
    try { Select-GoogleDriveFolder } catch { $failed = $true }
    if (-not $failed -or [IO.File]::ReadAllText((Get-GoogleDriveSettingsPath)) -ne $changedFolder) {
        throw 'Cancellation must fail without changing the saved choice.'
    }
    $script:DialogResult = 'OK'
    [IO.File]::WriteAllText((Get-GoogleDriveSettingsPath), (Join-Path $root 'missing'))
    $script:DialogChoice = $folder
    Select-GoogleDriveFolder | Out-Null
    if ($script:InitialFolder) { throw 'A missing saved folder must not be preselected.' }
    if ($script:Disposed -ne 4) { throw 'Every GUI dialog must be disposed, including cancellation.' }
    $env:GYMLOG_DRIVE_FOLDER = $folder
    if ((Select-GoogleDriveFolder) -ne $folder) { throw 'Environment sync folder was not honored.' }
    Send-ApkViaGoogleDrive $apk $folder
    Send-ApkViaGoogleDrive $apk $folder
    $copies = @(Get-ChildItem -LiteralPath $folder -Filter '*.apk')
    if ($copies.Count -ne 2) { throw 'Repeated sends must create separate APKs, without overwriting an earlier copy.' }
    if (-not (Test-Path -LiteralPath (Join-Path $folder 'GymLog-1.apk')) -or
        -not (Test-Path -LiteralPath (Join-Path $folder 'GymLog-2.apk'))) { throw 'APK copies must use sequential numeric suffixes.' }
    foreach ($copy in $copies) {
        if ((Get-FileHash -LiteralPath $copy.FullName).Hash -ne (Get-FileHash -LiteralPath $apk).Hash) {
            throw 'APK copy changed the binary contents.'
        }
    }
    [IO.File]::WriteAllText((Join-Path $folder 'GymLog-9.apk'), 'existing version')
    [IO.File]::WriteAllText((Join-Path $folder 'GymLog-20261117-older.apk'), 'legacy export')
    Send-ApkViaGoogleDrive $apk $folder
    if (-not (Test-Path -LiteralPath (Join-Path $folder 'GymLog-10.apk'))) { throw 'Numbering must continue after the highest saved version.' }
    if ([IO.File]::ReadAllText((Join-Path $folder 'GymLog-9.apk')) -ne 'existing version') { throw 'An existing version was overwritten.' }
    Push-Location $root
    try { Send-ApkViaGoogleDrive 'source with spaces.apk' 'sync folder with spaces' }
    finally { Pop-Location }
    if ((Get-FileHash -LiteralPath (Join-Path $folder 'GymLog-11.apk')).Hash -ne (Get-FileHash -LiteralPath $apk).Hash) {
        throw 'Relative source and destination paths must preserve the APK contents.'
    }
    $failed = $false
    try { Select-GoogleDriveFolder (Join-Path $root 'missing') } catch { $failed = $true }
    if (-not $failed) { throw 'Missing destination must fail.' }
    $failed = $false
    try { Send-ApkViaGoogleDrive (Join-Path $root 'missing.apk') $folder } catch { $failed = $true }
    if (-not $failed) { throw 'Missing source must fail.' }
} finally {
    $env:GYMLOG_DRIVE_FOLDER = $savedFolder
    $resolved = [IO.Path]::GetFullPath($root)
    $temp = [IO.Path]::GetFullPath([IO.Path]::GetTempPath()).TrimEnd('\') + '\'
    if (-not $resolved.StartsWith($temp, [StringComparison]::OrdinalIgnoreCase) -or (Split-Path $resolved -Leaf) -notlike 'gymlog-drive-tests-*') { throw 'Unsafe fixture cleanup path' }
    if (Test-Path -LiteralPath $resolved) { Remove-Item -LiteralPath $resolved -Recurse -Force }
}
Write-Output 'PASS: remembered Drive folders, GUI preselection/change/cancel, sequential APK versions, binary copies and missing-path failures; only temporary files and mocked dialogs used.'
