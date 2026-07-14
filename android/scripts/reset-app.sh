#!/usr/bin/env bash
set -euo pipefail

PKG="com.bfalls.suntimealerts"
ADB_SERIAL="${ADB_SERIAL:-${ANDROID_SERIAL:-}}"

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

list_device_serials() {
  "$ADB" devices | awk 'NR>1 && $2 == "device" { print $1 }'
}

resolve_target_args() {
  if [[ -n "$ADB_SERIAL" ]]; then
    printf '%s\n' "-s" "$ADB_SERIAL"
    return 0
  fi

  mapfile -t devices < <(list_device_serials)

  if [[ "${#devices[@]}" -eq 0 ]]; then
    echo "No connected adb devices were found." >&2
    return 1
  fi

  if [[ "${#devices[@]}" -gt 1 ]]; then
    echo "More than one adb device/emulator is connected:" >&2
    printf '  %s\n' "${devices[@]}" >&2
    echo "Set ADB_SERIAL or ANDROID_SERIAL, or run: ./scripts/reset-app.sh <serial>" >&2
    return 1
  fi

  printf '%s\n' "-s" "${devices[0]}"
}

ADB="$(resolve_adb)"
if [[ $# -gt 0 ]]; then
  if [[ "$1" == "devices" ]]; then
    "$ADB" devices
    exit 0
  fi
  ADB_SERIAL="$1"
fi
mapfile -t TARGET_ARGS < <(resolve_target_args)

echo "Using adb target: ${TARGET_ARGS[1]}"

echo "Clearing app data for $PKG"
"$ADB" "${TARGET_ARGS[@]}" shell pm clear "$PKG"

echo "Force-stopping $PKG"
"$ADB" "${TARGET_ARGS[@]}" shell am force-stop "$PKG"

echo "Done."
