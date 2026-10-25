param([string]$HelperPath = (Join-Path $PSScriptRoot '../helpers/release_tools.ps1'))
$ErrorActionPreference = 'Stop'
. $HelperPath
$assertions = 0
function Assert-Release($value, [string]$message) {
    $script:assertions++
    if (-not $value) { throw $message }
}
$content = "// unchanged comment`nversionCode = 3`nversionName = `"3.0`"`n// keep this too`n"
$unchanged = Get-ReleaseVersionInfo $content none
Assert-Release ($unchanged.Content -ceq $content -and $unchanged.Code -eq 3) 'No-bump preparation must preserve the original file and code'
foreach ($case in @(
    @('minor', '3.1'), @('major', '4.0'), @('patch', '3.0.1')
)) {
    $result = Get-ReleaseVersionInfo $content $case[0]
    Assert-Release ($result.Version -eq $case[1] -and $result.Code -eq 4) 'Bumps must update the selected version component and Android code'
    Assert-Release ($result.Content.Contains('// unchanged comment') -and $result.Content.Contains('// keep this too')) 'Version preparation must preserve unrelated Gradle content'
}
$patchContent = $content.Replace('"3.0"', '"3.2.9"')
Assert-Release ((Get-ReleaseVersionInfo $patchContent minor).Version -eq '3.3.0') 'Minor bumps reset an existing patch component'
Assert-Release ((Get-ReleaseVersionInfo $patchContent major).Version -eq '4.0.0') 'Major bumps reset existing minor and patch components'
foreach ($bad in @('versionCode = 1', $content.Replace('versionCode = 3', 'versionCode = 2100000000'))) {
    $failed = $false
    try { Get-ReleaseVersionInfo $bad patch | Out-Null } catch { $failed = $true }
    Assert-Release $failed 'Invalid version metadata and Android code overflow must fail'
}
Write-Output "PASS: $assertions release-version assertions; no files, tags, builds or remotes changed."
