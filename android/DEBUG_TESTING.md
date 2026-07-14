# Android Debug Testing

These hooks are debug-only. They are available in debug builds through the `DebugAlarmTestReceiver` and are not registered in release builds.

Package name used below:

```text
com.bfalls.suntimealerts
```

Set or clear a simulated readiness or permission state:

```text
adb shell am broadcast -a com.bfalls.suntimealerts.debug.SET_OVERRIDE --es override location_permission_denied --ez enabled true
adb shell am broadcast -a com.bfalls.suntimealerts.debug.SET_OVERRIDE --es override location_unavailable --ez enabled true
adb shell am broadcast -a com.bfalls.suntimealerts.debug.SET_OVERRIDE --es override notification_permission_denied --ez enabled true
adb shell am broadcast -a com.bfalls.suntimealerts.debug.SET_OVERRIDE --es override app_notifications_disabled --ez enabled true
adb shell am broadcast -a com.bfalls.suntimealerts.debug.SET_OVERRIDE --es override channel_blocked --ez enabled true
adb shell am broadcast -a com.bfalls.suntimealerts.debug.SET_OVERRIDE --es override exact_alarm_denied --ez enabled true
adb shell am broadcast -a com.bfalls.suntimealerts.debug.SET_OVERRIDE --es override full_screen_intent_denied --ez enabled true
```

Clear all overrides:

```text
adb shell am broadcast -a com.bfalls.suntimealerts.debug.CLEAR_OVERRIDES
```

Trigger reconcile paths:

```text
adb shell am broadcast -a com.bfalls.suntimealerts.debug.RECONCILE_BOOT
adb shell am broadcast -a com.bfalls.suntimealerts.debug.RECONCILE_TIMEZONE
```

Simulate returning to the app from system settings so readiness is refreshed:

```text
adb shell am broadcast -a com.bfalls.suntimealerts.debug.SIMULATE_APP_RESUME
```

Suggested real-device loop:

```text
adb shell am broadcast -a com.bfalls.suntimealerts.debug.CLEAR_OVERRIDES
adb shell am broadcast -a com.bfalls.suntimealerts.debug.SET_OVERRIDE --es override exact_alarm_denied --ez enabled true
adb shell am broadcast -a com.bfalls.suntimealerts.debug.SIMULATE_APP_RESUME
```

Then verify:

- Home shows the repair banner
- Settings shows the matching repair action
- Scheduling is skipped when exact alarms are denied
- Reconcile behavior is deterministic when boot or timezone actions are triggered

Real-device verification checklist:

- Fresh install on API 33 or newer with notification allow and deny paths
- Exact alarm allow and deny on API 31 or newer
- Location allow, deny, approximate-only, and manual city fallback
- Blocked notification channel
- Full-screen intent enabled and disabled on Android 14 or newer
- Debug boot reconcile and debug timezone reconcile
- Debug app resume after toggling system settings

Reset script with a specific device:

```text
./scripts/reset-app.sh devices
./scripts/reset-app.sh R58NXXXXXXX
pwsh ./scripts/reset-app.ps1 -DeviceSerial devices
pwsh ./scripts/reset-app.ps1 -DeviceSerial R58NXXXXXXX
```

If more than one emulator or device is connected, the reset scripts now require an explicit serial, either by argument or by setting `ADB_SERIAL` / `ANDROID_SERIAL`.

Firebase Test Lab matrix proposal:

- Physical Pixel 5 on API 34
- Physical Pixel 4a on API 33
- Physical Pixel 4 on API 31
- Virtual API 35 emulator for smoke coverage only
