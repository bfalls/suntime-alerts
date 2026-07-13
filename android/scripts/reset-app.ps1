[CmdletBinding(SupportsShouldProcess)]
param(
    [string]$PackageName = "com.bfalls.suntimealerts"
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

function Resolve-AdbPath {
    $command = Get-Command adb.exe -ErrorAction SilentlyContinue
    if ($command) {
        return $command.Source
    }

    $candidates = @()

    if ($env:ANDROID_SDK_ROOT) {
        $candidates += (Join-Path $env:ANDROID_SDK_ROOT "platform-tools\adb.exe")
    }
    if ($env:ANDROID_HOME) {
        $candidates += (Join-Path $env:ANDROID_HOME "platform-tools\adb.exe")
    }
    if ($env:LOCALAPPDATA) {
        $candidates += (Join-Path $env:LOCALAPPDATA "Android\Sdk\platform-tools\adb.exe")
    }
    if ($env:USERPROFILE) {
        $candidates += (Join-Path $env:USERPROFILE "AppData\Local\Android\Sdk\platform-tools\adb.exe")
    }

    foreach ($candidate in $candidates) {
        if (Test-Path -LiteralPath $candidate) {
            return $candidate
        }
    }

    throw "Unable to find adb.exe. Add platform-tools to PATH or set ANDROID_SDK_ROOT."
}

$adbPath = Resolve-AdbPath

Write-Host "Using adb: $adbPath"

if ($PSCmdlet.ShouldProcess($PackageName, "Clear app data")) {
    Write-Host "Clearing app data for $PackageName"
    & $adbPath shell pm clear $PackageName
}

if ($PSCmdlet.ShouldProcess($PackageName, "Force-stop app")) {
    Write-Host "Force-stopping $PackageName"
    & $adbPath shell am force-stop $PackageName
}

Write-Host "Done."
