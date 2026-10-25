<#
.SYNOPSIS
  Build the CleanTV launcher and install it on one or more TVs from Windows, keeping their settings.

.EXAMPLE
  .\install.ps1 192.168.0.222 192.168.0.221
  .\install.ps1 -NoBuild 192.168.0.222      # reuse the APK that is already built

  Needs the Android SDK platform-tools (adb) and a JDK 17+ (Android Studio's JBR works). It is found in the usual
  places or taken from ADB / JAVA_HOME. The APK is always signed with launcher/signing/cleantv.keystore, so installing
  over a previous build keeps the launcher's settings; only a build signed with another key has to uninstall first.
#>
param(
    [Parameter(Mandatory = $true, ValueFromRemainingArguments = $true)][string[]]$Hosts,
    [switch]$NoBuild
)
$ErrorActionPreference = 'Continue'   # native tools write notices to stderr; failures are checked by exit code
$here = Split-Path -Parent $MyInvocation.MyCommand.Path

function Find-First($candidates) { $candidates | Where-Object { $_ -and (Test-Path $_) } | Select-Object -First 1 }

$adb = $env:ADB
if (-not $adb) { $cmd = Get-Command adb -ErrorAction SilentlyContinue; if ($cmd) { $adb = $cmd.Source } }
if (-not $adb) { $adb = Find-First @("$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe") }
if (-not $adb) { throw 'adb not found: install the Android SDK platform-tools or set ADB.' }

if (-not $NoBuild) {
    $jdk = $env:JAVA_HOME
    if (-not $jdk) { $jdk = Find-First @('C:\Program Files\Android\Android Studio\jbr') }
    if (-not $jdk) { throw 'No JDK found: set JAVA_HOME (17 or newer).' }
    $env:JAVA_HOME = $jdk
    $props = Join-Path $here 'local.properties'
    if (-not (Test-Path $props)) {
        $sdk = Find-First @($env:ANDROID_HOME, $env:ANDROID_SDK_ROOT, "$env:LOCALAPPDATA\Android\Sdk")
        if (-not $sdk) { throw 'Android SDK not found: set ANDROID_HOME.' }
        "sdk.dir=" + ($sdk -replace '\\', '/') | Set-Content -Encoding ascii $props
    }
    Push-Location $here
    # Through cmd so Java's "Picked up JAVA_TOOL_OPTIONS" notice on stderr is not shown as a PowerShell error.
    try { cmd /c ".\gradlew.bat assembleRelease -q --console=plain 2>&1" | Out-Host; if ($LASTEXITCODE) { throw 'Gradle build failed.' } }
    finally { Pop-Location }
}
$apk = Join-Path $here 'app\build\outputs\apk\release\app-release.apk'
if (-not (Test-Path $apk)) { throw "No APK at $apk" }

foreach ($h in $Hosts) {
    $serial = if ($h -match ':') { $h } else { "${h}:5555" }
    Write-Host "== $serial"
    & $adb connect $serial | Out-Host
    $state = (& $adb -s $serial get-state) 2>$null
    if ($state -ne 'device') { Write-Warning "$serial is not authorised or not reachable ($state); skipped."; continue }
    if ((& $adb -s $serial shell getprop ro.product.manufacturer) -match 'Amazon') {
        Write-Warning "$serial is a Fire TV, which CleanTV does not support; skipped."; continue
    }
    & $adb -s $serial install -r $apk | Out-Host
    if ($LASTEXITCODE) {
        Write-Warning "Install failed on $serial. If it says signatures do not match, that TV has a build signed with another key: uninstall com.gathin.tvhome there once (the settings are lost) and run this again."
        continue
    }
    & $adb -s $serial shell cmd package set-home-activity --user 0 com.gathin.tvhome/.MainActivity | Out-Host
    & $adb -s $serial shell input keyevent KEYCODE_HOME
}
