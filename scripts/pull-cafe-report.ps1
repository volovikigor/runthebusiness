param(
    [string]$OutputDir = (Join-Path (Get-Location) "reports"),
    [string]$Package = "com.example.runthebusiness" 
)
$ErrorActionPreference = "Stop"
New-Item -ItemType Directory -Force -Path $OutputDir | Out-Null
$adb = (Get-Command adb -ErrorAction SilentlyContinue).Source
if (-not $adb) {
    $sdk = Join-Path $env:LOCALAPPDATA "Android\Sdk\platform-tools\adb.exe"
    if (Test-Path $sdk) { $adb = $sdk } else { throw "adb not found. Start Android Studio and install Android SDK Platform-Tools." }
}
$devices = & $adb devices
if (@($devices | Where-Object { $_ -match "\sdevice$" }).Count -ne 1) { throw "Connect exactly one unlocked Android device with USB debugging enabled." }
$name = "cafe_diagnostic_" + (Get-Date -Format "yyyyMMdd_HHmmss") + ".csv"
$target = Join-Path $OutputDir $name
# ADB stdout must be redirected as raw bytes. cmd.exe redirection preserves UTF-8
# unlike Windows PowerShell 5.1 text redirection which may recode the file.
$quotedAdb = '"' + $adb + '"'
$quotedTarget = '"' + $target + '"'
$command = "$quotedAdb exec-out run-as $Package cat files/cafe_diagnostic_latest.csv > $quotedTarget"
cmd.exe /d /s /c $command
if ($LASTEXITCODE -ne 0 -or -not (Test-Path $target) -or (Get-Item $target).Length -lt 50) {
    Remove-Item $target -ErrorAction SilentlyContinue
    throw "Could not pull report. Install the debug build and run +7 days at least once."
}
Write-Host "Saved: $target"
