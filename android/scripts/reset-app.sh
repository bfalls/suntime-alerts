#!/usr/bin/env bash
set -euo pipefail

PKG="com.bfalls.suntimealerts"

echo "Clearing app data for $PKG"
adb shell pm clear "$PKG"

echo "Force-stopping $PKG"
adb shell am force-stop "$PKG"

echo "Done."
