[CmdletBinding()]
param(
    [Parameter(Mandatory)]
    [ValidateSet('minor', 'major', 'none')]
    [string]$Bump
)

$ErrorActionPreference = 'Stop'
$scriptDirectory = Split-Path -Parent $PSCommandPath
$repoRoot = (Resolve-Path (Join-Path $scriptDirectory '..\..\..')).Path
$gradleFile = Join-Path $repoRoot 'app\app\build.gradle.kts'

Set-Location $repoRoot
if (git status --porcelain) { throw 'The Git working tree must be clean before starting a release.' }
if ((git branch --show-current) -ne 'main') { throw 'Releases must be created from the main branch.' }

$content = Get-Content -Raw -LiteralPath $gradleFile
$nameMatch = [regex]::Match($content, 'versionName\s*=\s*"(?<version>\d+\.\d+)"')
$codeMatch = [regex]::Match($content, 'versionCode\s*=\s*(?<code>\d+)')
if (-not $nameMatch.Success -or -not $codeMatch.Success) { throw 'Could not read an x.y versionName and integer versionCode.' }

$version = $nameMatch.Groups['version'].Value
if ($Bump -ne 'none') {
    $parts = $version.Split('.')
    $major = [int]$parts[0]
    $minor = [int]$parts[1]
    if ($Bump -eq 'major') { $major++; $minor = 0 } else { $minor++ }
    $version = "$major.$minor"
    $nextCode = [int]$codeMatch.Groups['code'].Value + 1
    $content = [regex]::Replace($content, 'versionName\s*=\s*"\d+\.\d+"', "versionName = `"$version`"", 1)
    $content = [regex]::Replace($content, 'versionCode\s*=\s*\d+', "versionCode = $nextCode", 1)
    Set-Content -LiteralPath $gradleFile -Value $content -NoNewline
    git add app/app/build.gradle.kts
    git commit -m "Prepare release v$version"
    if ($LASTEXITCODE -ne 0) { throw 'Could not commit the version bump.' }
}

git rev-parse "v$version" *> $null
if ($LASTEXITCODE -eq 0) { throw "Tag v$version already exists." }

git push origin main
if ($LASTEXITCODE -ne 0) { throw 'Pushing main failed. No tag was created.' }
git tag -a "v$version" -m "GymLog v$version"
if ($LASTEXITCODE -ne 0) { throw "Could not create tag v$version." }
git push origin "v$version"
if ($LASTEXITCODE -ne 0) { throw "Main was pushed, but v$version was not. Push it with: git push origin v$version" }

Write-Host "Release v$version queued. GitHub Actions will verify, sign, and publish it."