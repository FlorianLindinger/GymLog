param([string]$WindowsPath = (Join-Path $PSScriptRoot '..'))
$ErrorActionPreference = 'Stop'
$assertions = 0
function Assert-ReleaseFlow($value, [string]$message) {
    $script:assertions++
    if (-not $value) { throw $message }
}
$root = Join-Path ([IO.Path]::GetTempPath()) ('gymlog-release-tests-' + [Guid]::NewGuid().ToString('N'))
$helpers = Join-Path $root 'scripts/windows/helpers'
$app = Join-Path $root 'app/app'
$savedFailure = $env:GYMLOG_TEST_FAIL_BUILD
try {
    New-Item -ItemType Directory -Path $helpers, $app -Force | Out-Null
    foreach ($name in @('release_version.ps1','release_tools.ps1')) {
        Copy-Item -LiteralPath (Join-Path $WindowsPath "helpers/$name") -Destination (Join-Path $helpers $name)
    }
    # Git and builds are fixture functions; these tests never invoke a real remote or Gradle.
    @'
$script:RepoRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../../..'))
$script:AppRoot = Join-Path $script:RepoRoot 'app'
function git {
    $global:LASTEXITCODE = 0
    [IO.File]::AppendAllText((Join-Path $script:RepoRoot 'git.log'), ($args -join ' ') + "`n")
    if ($args[0] -eq 'branch') { 'main' }
    if ($args[0] -eq 'remote') { 'https://example.invalid/fixture.git' }
}
function Initialize-Build {}
function Invoke-Gradle {
    param([string[]]$Tasks)
    [IO.File]::AppendAllText((Join-Path $script:RepoRoot 'build.log'), ($Tasks -join ' ') + "`n")
    if ($env:GYMLOG_TEST_FAIL_BUILD -eq '1') { throw 'Fixture build failed' }
}
function Copy-DebugApk {}
'@ | Set-Content -LiteralPath (Join-Path $helpers 'android_env.ps1') -Encoding ASCII
    foreach ($case in @(
        @{ Publish = $false; Fail = $false },
        @{ Publish = $true; Fail = $false },
        @{ Publish = $true; Fail = $true }
    )) {
        [IO.File]::WriteAllText((Join-Path $app 'build.gradle.kts'), "versionCode = 3`nversionName = `"3.0`"`n")
        [IO.File]::WriteAllText((Join-Path $root 'git.log'), '')
        $env:GYMLOG_TEST_FAIL_BUILD = if ($case.Fail) { '1' } else { $null }
        $arguments = @('-NoProfile','-ExecutionPolicy','Bypass','-File',(Join-Path $helpers 'release_version.ps1'),'-Bump','minor')
        if ($case.Publish) { $arguments += '-Publish' }
        $ErrorActionPreference = 'Continue'
        try { & (Join-Path $PSHOME 'powershell.exe') @arguments *> $null; $code = $LASTEXITCODE }
        finally { $ErrorActionPreference = 'Stop' }
        Assert-ReleaseFlow ($code -eq $(if ($case.Fail) { 1 } else { 0 })) 'Release preparation must propagate build failure'
        $log = [IO.File]::ReadAllText((Join-Path $root 'git.log'))
        if (-not $case.Publish) {
            Assert-ReleaseFlow ($log -notmatch '(?m)^(remote|ls-remote|add|commit|push|tag -a)\b') 'Local preparation must not contact remotes, commit, tag, or push'
            Assert-ReleaseFlow ([IO.File]::ReadAllText((Join-Path $app 'build.gradle.kts')).Contains('versionName = "3.1"')) 'Local preparation must leave the version ready for review'
        } elseif ($case.Fail) {
            Assert-ReleaseFlow ($log -notmatch '(?m)^(add|commit|push|tag -a)\b') 'A failed build must prevent commit, tagging, and publication'
        } else {
            Assert-ReleaseFlow ($log.Contains('push --atomic origin HEAD:refs/heads/main refs/tags/v3.1')) 'Explicit publication must push main and the version tag atomically'
        }
    }
} finally {
    $env:GYMLOG_TEST_FAIL_BUILD = $savedFailure
    if (Test-Path -LiteralPath $root) {
        Get-ChildItem -LiteralPath $root -Recurse -File | ForEach-Object { Remove-Item -LiteralPath $_.FullName }
        Get-ChildItem -LiteralPath $root -Recurse -Directory | Sort-Object { $_.FullName.Length } -Descending |
            ForEach-Object { Remove-Item -LiteralPath $_.FullName }
        Remove-Item -LiteralPath $root
    }
}
Write-Output "PASS: $assertions release-flow assertions; no actual build, tags or remotes changed."
