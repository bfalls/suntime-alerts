[CmdletBinding(SupportsShouldProcess)]
param(
    [string]$PackageName = "com.bfalls.suntimealerts",
    [string]$DeviceSerial = ""
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

function Get-ConnectedDeviceSerials {
    param(
        [string]$AdbPath
    )

    $lines = & $AdbPath devices
    foreach ($line in $lines) {
        if ($line -match '^(?<serial>\S+)\s+device$') {
            $matches['serial']
        }
    }
}

$adbPath = Resolve-AdbPath

if ($DeviceSerial -eq "devices") {
    & $adbPath devices
    exit 0
}

$resolvedSerial = if ($DeviceSerial) { $DeviceSerial } elseif ($env:ADB_SERIAL) { $env:ADB_SERIAL } else { $env:ANDROID_SERIAL }

if (-not $resolvedSerial) {
    $devices = @(Get-ConnectedDeviceSerials -AdbPath $adbPath)
    if ($devices.Count -eq 0) {
        throw "No connected adb devices were found."
    }
    if ($devices.Count -gt 1) {
        $deviceList = ($devices | ForEach-Object { "  $_" }) -join [Environment]::NewLine
        throw "More than one adb device/emulator is connected:`n$deviceList`nSpecify -DeviceSerial, or set ADB_SERIAL / ANDROID_SERIAL."
    }
    $resolvedSerial = $devices[0]
}

Write-Host "Using adb: $adbPath"
Write-Host "Using adb target: $resolvedSerial"

if ($PSCmdlet.ShouldProcess($PackageName, "Clear app data")) {
    Write-Host "Clearing app data for $PackageName"
    & $adbPath -s $resolvedSerial shell pm clear $PackageName
}

if ($PSCmdlet.ShouldProcess($PackageName, "Force-stop app")) {
    Write-Host "Force-stopping $PackageName"
    & $adbPath -s $resolvedSerial shell am force-stop $PackageName
}

Write-Host "Done."
