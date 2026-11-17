param([string]$HelperPath = (Join-Path $PSScriptRoot '../helpers/android_env.ps1'))
$ErrorActionPreference = 'Stop'
. $HelperPath
$script:Assertions = 0
function Assert-Wifi($Value, [string]$Message) {
    $script:Assertions++
    if (-not $Value) { throw $Message }
}
function Read-Host([string]$Prompt) {
    $script:Events.Add("PROMPT $Prompt")
    return $script:Answers.Dequeue()
}
function Connect-AndroidWifi([string]$Adb, [string]$PairAddress, [string]$ConnectAddress) {
    if ($PairAddress) { Assert-WifiAddress $PairAddress }
    if ($ConnectAddress) { Assert-WifiAddress $ConnectAddress }
    $script:Events.Add("CONNECT pair=$PairAddress connect=$ConnectAddress")
}
function Reset-Wifi([string[]]$Answers) {
    $script:Answers = New-Object 'Collections.Generic.Queue[string]'
    foreach ($answer in $Answers) { $script:Answers.Enqueue($answer) }
    $script:Events = New-Object 'Collections.Generic.List[string]'
}
Reset-Wifi @(' 192.168.1.7:12345 ', ' 192.168.1.7:54321 ')
Request-AndroidWifiConnection 'mock-adb'
Assert-Wifi ($script:Events.Count -eq 4) 'Setup must pair and connect using the two prompts'
Assert-Wifi ($script:Events[1] -eq 'CONNECT pair=192.168.1.7:12345 connect=') 'Pair before asking the user to leave the pairing screen'
Assert-Wifi ($script:Events[3] -eq 'CONNECT pair= connect=192.168.1.7:54321') 'Use the separate connection port and trim input'
Reset-Wifi @('', '192.168.1.7:54321')
Request-AndroidWifiConnection 'mock-adb'
Assert-Wifi ($script:Events.Count -eq 3 -and $script:Events[2] -eq 'CONNECT pair= connect=192.168.1.7:54321') 'An already-paired phone must skip pairing'
Reset-Wifi @('', '')
$failed = $false
try { Request-AndroidWifiConnection 'mock-adb' } catch { $failed = $_.Exception.Message -match 'cancelled' }
Assert-Wifi $failed 'Blank connection address must cancel'
Assert-Wifi ($script:Events.Count -eq 2) 'Cancellation must not connect'
Reset-Wifi @('invalid', '192.168.1.7:54321')
$failed = $false
try { Request-AndroidWifiConnection 'mock-adb' } catch { $failed = $true }
Assert-Wifi $failed 'Invalid pairing addresses must fail'
Assert-Wifi ($script:Events.Count -eq 1) 'Pairing failure must stop before the connection prompt'
Write-Output "PASS: $script:Assertions Wi-Fi setup assertions; no device contacted."
