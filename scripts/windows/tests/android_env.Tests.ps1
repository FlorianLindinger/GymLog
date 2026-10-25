param([string]$HelperPath = (Join-Path $PSScriptRoot '../helpers/android_env.ps1'))
$ErrorActionPreference = 'Stop'
. $HelperPath
$script:EnvironmentAssertions = 0
function Assert-Environment($value, [string]$message) {
    $script:EnvironmentAssertions++
    if (-not $value) { throw $message }
}
$variables = @('GYMLOG_JAVA_HOME','JAVA_HOME','STUDIO_JDK','ANDROID_HOME','ANDROID_SDK_ROOT','GYMLOG_ADB','GYMLOG_BUILD_DIR')
$saved = @{}
foreach ($name in $variables) { $saved[$name] = [Environment]::GetEnvironmentVariable($name) }
$testRoot = Join-Path ([IO.Path]::GetTempPath()) ('gymlog-env-tests-' + [Guid]::NewGuid().ToString('N'))
$script:RepoRoot = $testRoot
$script:AppRoot = Join-Path $testRoot 'app'
$script:StudioRoot = Join-Path $testRoot 'registered Studio'
$sdk = Join-Path $testRoot 'sdk with [brackets]'
$wrongJava = Join-Path $testRoot 'wrong-java'
function Get-ItemProperty {
    param([string]$LiteralPath, [string]$ErrorAction)
    [pscustomobject]@{ DisplayIcon = '"' + (Join-Path $script:StudioRoot 'bin/studio64.exe') + '",0' }
}
function Get-Command {
    param([string]$Name, [string]$ErrorAction)
    return $null
}
try {
    foreach ($relative in @('app','registered Studio/jbr/bin','wrong-java/bin','sdk with [brackets]/platforms',
        'sdk with [brackets]/platform-tools','build/outputs/apk/debug')) {
        New-Item -ItemType Directory -Path (Join-Path $testRoot $relative) -Force | Out-Null
    }
    [IO.File]::WriteAllText((Join-Path $wrongJava 'release'), 'JAVA_VERSION="25.0.2"')
    [IO.File]::WriteAllText((Join-Path $wrongJava 'bin/java.exe'), '')
    [IO.File]::WriteAllText((Join-Path $script:StudioRoot 'jbr/release'), 'JAVA_VERSION="21.0.8"')
    [IO.File]::WriteAllText((Join-Path $script:StudioRoot 'jbr/bin/java.exe'), '')
    [IO.File]::WriteAllText((Join-Path $sdk 'platform-tools/adb.exe'), '')
    $env:GYMLOG_JAVA_HOME = $wrongJava
    $env:JAVA_HOME = $null
    $env:STUDIO_JDK = $null
    Assert-Environment ((Find-JavaHome) -eq (Join-Path $script:StudioRoot 'jbr')) 'Skip unsupported Java and resolve registered Android Studio on a custom path'
    $env:GYMLOG_JAVA_HOME = Join-Path $script:StudioRoot 'jbr'
    Assert-Environment ((Find-JavaHome) -eq $env:GYMLOG_JAVA_HOME) 'Respect a compatible explicit Java override'
    $env:ANDROID_HOME = $null
    $env:ANDROID_SDK_ROOT = $null
    $escaped = $sdk.Replace('\', '\\').Replace(':', '\:')
    [IO.File]::WriteAllText((Join-Path $script:AppRoot 'local.properties'), "sdk.dir=$escaped`n")
    Assert-Environment ((Find-AndroidSdk) -eq $sdk) 'Read escaped SDK paths from the nested Gradle project, including spaces and brackets'
    $env:GYMLOG_ADB = $null
    Assert-Environment ((Find-Adb) -eq (Join-Path $sdk 'platform-tools/adb.exe')) 'Install-only ADB discovery must not need a running Java executable'
    Remove-Item -LiteralPath (Join-Path $sdk 'platforms')
    Assert-Environment ((Find-Adb) -eq (Join-Path $sdk 'platform-tools/adb.exe')) 'Device tools must work with Platform-Tools alone, without SDK platforms installed'
    foreach ($address in @('192.168.1.7:37123','[fe80::1]:1234')) { Assert-WifiAddress $address }
    foreach ($address in @('192.168.1.7:0','host:65536','host;echo:1234','missing-port')) {
        $failed = $false
        try { Assert-WifiAddress $address } catch { $failed = $true }
        Assert-Environment $failed 'Reject invalid Wi-Fi addresses before starting ADB'
    }
    $env:GYMLOG_BUILD_DIR = Join-Path $testRoot 'build'
    [IO.File]::WriteAllText((Join-Path $env:GYMLOG_BUILD_DIR 'outputs/apk/debug/app-debug.apk'), 'fixture APK')
    Copy-DebugApk 6>$null
    $copy = Join-Path $script:AppRoot 'artifacts/debug/app-debug.apk'
    $hash = (Get-FileHash -LiteralPath $copy -Algorithm SHA256).Hash.ToLowerInvariant()
    Assert-Environment ([IO.File]::ReadAllText($copy) -eq 'fixture APK') 'Copy debug artifacts to the existing GymLog output location'
    Assert-Environment ([IO.File]::ReadAllText((Join-Path $script:AppRoot 'artifacts/debug/SHA256SUMS.txt')) -ceq "$hash  app-debug.apk`n") 'Write the checksum for the exact copied artifact'
} finally {
    foreach ($name in $variables) { [Environment]::SetEnvironmentVariable($name, $saved[$name]) }
    # Remove individual fixtures only; never recursively delete a computed path.
    if (Test-Path -LiteralPath $testRoot) {
        Get-ChildItem -LiteralPath $testRoot -Recurse -File | ForEach-Object { Remove-Item -LiteralPath $_.FullName }
        Get-ChildItem -LiteralPath $testRoot -Recurse -Directory | Sort-Object { $_.FullName.Length } -Descending |
            ForEach-Object { Remove-Item -LiteralPath $_.FullName }
        Remove-Item -LiteralPath $testRoot
    }
}
Write-Output "PASS: $script:EnvironmentAssertions environment/artifact assertions; only temporary fixtures used."
