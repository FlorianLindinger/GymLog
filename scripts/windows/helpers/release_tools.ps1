function Get-ReleaseVersionInfo([string]$Content, [ValidateSet('none','patch','minor','major')][string]$Bump) {
    $name = [regex]::Match($Content, 'versionName\s*=\s*"(\d+)\.(\d+)(?:\.(\d+))?"')
    $code = [regex]::Match($Content, 'versionCode\s*=\s*(\d+)')
    if (-not $name.Success -or -not $code.Success) { throw 'Could not read versionName and versionCode from app/app/build.gradle.kts.' }
    $major = [int]$name.Groups[1].Value
    $minor = [int]$name.Groups[2].Value
    $hasPatch = $name.Groups[3].Success
    $patch = if ($hasPatch) { [int]$name.Groups[3].Value } else { 0 }
    switch ($Bump) {
        'major' { $major++; $minor = 0; $patch = 0 }
        'minor' { $minor++; $patch = 0 }
        'patch' { $patch++; $hasPatch = $true }
    }
    $version = if ($hasPatch) { "$major.$minor.$patch" } else { "$major.$minor" }
    $nextCode = [long]$code.Groups[1].Value + [int]($Bump -ne 'none')
    if ($nextCode -gt 2100000000) { throw 'Android versionCode limit reached.' }
    $updated = $Content
    if ($Bump -ne 'none') {
        $updated = $updated.Remove($name.Index, $name.Length).Insert($name.Index, "versionName = `"$version`"")
        $code = [regex]::Match($updated, 'versionCode\s*=\s*(\d+)')
        $updated = $updated.Remove($code.Index, $code.Length).Insert($code.Index, "versionCode = $nextCode")
    }
    [pscustomobject]@{ Version = $version; Code = $nextCode; Content = $updated }
}
