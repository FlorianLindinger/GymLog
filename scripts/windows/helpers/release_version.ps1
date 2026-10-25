[CmdletBinding()]
param(
    [ValidateSet('none','patch','minor','major')][string]$Bump='none',
    [switch]$Publish
)
$ErrorActionPreference='Stop'
. (Join-Path $PSScriptRoot 'android_env.ps1')
. (Join-Path $PSScriptRoot 'release_tools.ps1')
try {
    Set-Location $script:RepoRoot
    $status = git status --porcelain
    if ($LASTEXITCODE -ne 0) { throw 'Could not read Git status.' }
    if ($status) { throw 'Commit or stash your changes before preparing a release.' }
    if ((git branch --show-current) -ne 'main') { throw 'Prepare releases from main.' }
    $versionFile = Join-Path $script:AppRoot 'app/build.gradle.kts'
    $info = Get-ReleaseVersionInfo ([IO.File]::ReadAllText($versionFile)) $Bump
    $version = $info.Version
    $existing = git tag --list "v$version"
    if ($LASTEXITCODE -ne 0) { throw 'Could not read local tags.' }
    if ($existing) { throw "Tag v$version already exists." }
    if ($Publish) {
        git remote get-url origin | Out-Null
        if ($LASTEXITCODE -ne 0) { throw 'Set the origin remote before publishing.' }
        $existing = git ls-remote --tags origin "refs/tags/v$version"
        if ($LASTEXITCODE -ne 0) { throw 'Could not check remote tags.' }
        if ($existing) { throw "Remote tag v$version already exists." }
    }
    if ($Bump -ne 'none') {
        [IO.File]::WriteAllText($versionFile, $info.Content, (New-Object Text.UTF8Encoding $false))
    }
    Initialize-Build
    Invoke-Gradle @(':app:testDebugUnitTest', ':app:lintDebug', ':app:assembleDebug')
    Copy-DebugApk
    if (-not $Publish) {
        Write-Host "Prepared v$version. Review app/app/build.gradle.kts and CHANGELOG.md, then commit your changes. No tag or push was performed."
        Write-Host 'When ready, run release_no_bump.bat -Publish from a clean main branch.'
        exit 0
    }
    if ($Bump -ne 'none') {
        git add -- app/app/build.gradle.kts
        if ($LASTEXITCODE -ne 0) { throw 'Could not stage the version bump.' }
        git commit -m "Prepare release v$version"
        if ($LASTEXITCODE -ne 0) { throw 'Could not commit app/app/build.gradle.kts.' }
    }
    $status = git status --porcelain
    if ($LASTEXITCODE -ne 0 -or $status) { throw 'Unexpected file changes during verification; no tag was created.' }
    git tag -a "v$version" -m "GymLog v$version"
    if ($LASTEXITCODE -ne 0) { throw 'Could not create the local tag.' }
    git push --atomic origin HEAD:refs/heads/main "refs/tags/v$version"
    if ($LASTEXITCODE -ne 0) { throw "Push failed; the local v$version tag remains. Check origin and retry git push --atomic origin HEAD:refs/heads/main refs/tags/v$version." }
    Write-Host "Pushed v$version. GitHub Actions will verify, sign and publish the release."
} catch { Write-Error $_ -ErrorAction Continue; exit 1 }
