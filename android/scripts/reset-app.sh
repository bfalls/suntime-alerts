#!/usr/bin/env bash
set -euo pipefail

PKG="com.bfalls.suntimealerts"

resolve_adb() {
  normalize_path() {
    local raw_path="$1"
    if command -v cygpath >/dev/null 2>&1; then
      cygpath -u "$raw_path"
    else
      printf '%s\n' "$raw_path"
    fi
  }

  if command -v adb >/dev/null 2>&1; then
    command -v adb
    return 0
  fi

  local candidates=()
  if [[ -n "${ANDROID_SDK_ROOT:-}" ]]; then
    local sdk_root
    sdk_root="$(normalize_path "$ANDROID_SDK_ROOT")"
    candidates+=("$sdk_root/platform-tools/adb")
    candidates+=("$sdk_root/platform-tools/adb.exe")
  fi
  if [[ -n "${ANDROID_HOME:-}" ]]; then
    local android_home
    android_home="$(normalize_path "$ANDROID_HOME")"
    candidates+=("$android_home/platform-tools/adb")
    candidates+=("$android_home/platform-tools/adb.exe")
  fi
  if [[ -n "${LOCALAPPDATA:-}" ]]; then
    local local_app_data
    local_app_data="$(normalize_path "$LOCALAPPDATA")"
    candidates+=("$local_app_data/Android/Sdk/platform-tools/adb.exe")
  fi
  if [[ -n "${USERPROFILE:-}" ]]; then
    local user_profile
    user_profile="$(normalize_path "$USERPROFILE")"
    candidates+=("$user_profile/AppData/Local/Android/Sdk/platform-tools/adb.exe")
  fi

  local adb_path
  for adb_path in "${candidates[@]}"; do
    if [[ -x "$adb_path" || -f "$adb_path" ]]; then
      printf '%s\n' "$adb_path"
      return 0
    fi
  done

  echo "Unable to find adb. Add platform-tools to PATH or set ANDROID_SDK_ROOT." >&2
  return 1
}

ADB="$(resolve_adb)"

echo "Clearing app data for $PKG"
"$ADB" shell pm clear "$PKG"

echo "Force-stopping $PKG"
"$ADB" shell am force-stop "$PKG"

echo "Done."
