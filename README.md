# Suntime Alerts

Suntime Alerts is a dual-native mobile app (iOS + Android) that schedules alarms relative to daily sunrise and sunset times. The repo houses two independent projects that share the same domain concepts and scheduling principles, keeping platform implementations idiomatic while ensuring consistent behaviour.

## Repository layout
- `README.md`: Overview and quickstart for both platforms.
- `DESIGN.md`: Architecture and product design reference.
- `ios/`: Native Swift/SwiftUI app using MVVM and clean layering.
- `android/`: Native Kotlin/Jetpack Compose app using MVVM, coroutines, and WorkManager/AlarmManager.
- `.github/workflows/`: Continuous integration skeletons.

## Platform stacks
### iOS
- Swift 5+, SwiftUI UI layer, MVVM + clean separation (Domain/Data/Presentation/Services).
- CoreLocation for location, UserNotifications for alarms, UserDefaults-based settings store.
- Async/await for async flows.

### Android
- Kotlin with Jetpack Compose and Navigation.
- Coroutines + Flow, ViewModel, DataStore for settings, Fused Location Provider for location.
- WorkManager for daily recomputation, AlarmManager + NotificationCompat for delivery.

## Getting started
### iOS
1. Open `ios/SuntimeAlerts.xcodeproj` in Xcode (project scaffolded for SwiftUI).
2. Build and run on iOS 16+ simulator or device.
3. Grant location and notification permissions during onboarding.

### Android
1. Open `android/` in Android Studio Giraffe+.
2. Sync Gradle to download dependencies.
3. Run the `app` configuration on an API 26+ emulator or device.
4. The Gradle wrapper JAR is checked in. If you need to regenerate it, use `./gradlew wrapper --gradle-version 8.13`.
5. For permission and readiness verification, prefer real devices over emulators. See `android/DEBUG_TESTING.md`.
6. For manual sky-banner preview on Windows, run `tools/run-sky-banner-lab.bat` or `.\gradlew.bat :sky-banner-lab:run` from `android/`. This launches a desktop utility that reuses the Android app's existing sun/moon calculation code and banner assets so you can inspect future dates, times, time zones, and coordinates without rebuilding the mobile UI.

## Development principles
- Shared domain concepts: `SunEventType`, `SunEvent`, `SunAlarmConfig`, `UserSettings`.
- Pure, on-device `SunTimesCalculator` (NOAA-inspired solar position math) for offline correctness.
- Daily recomputation and rescheduling reacting to time zone, DST, and location changes.
- Testable core logic with unit tests for solar calculations and scheduling offsets on both platforms.

## Android: verifying alarm state via ADB
Use these commands to confirm that the system AlarmManager state matches in-app alarm toggles:

1. Clear app state (optional): `adb shell pm clear com.bfalls.suntimealerts`
2. Inspect future alarms (should be empty after disabling/deleting all alarms):
   ```bash
   PKG=com.bfalls.suntimealerts
   adb shell dumpsys alarm | grep "$PKG" | grep "OW=" | sed -n 's/.*OW=\([0-9-]* [0-9:]*\).*/\1 &/p' | awk -v now="$(adb shell date '+%Y-%m-%d %H:%M:%S')" '$1 " " $2 > now'
   ```
3. Trigger a manual reconcile if needed (debug-only broadcast):
   ```bash
   adb shell am broadcast -a com.bfalls.suntimealerts.DEBUG_RECONCILE_ALARMS
   ```

## Android: real-device verification plan
Use physical devices for permission-sensitive verification. Emulators are useful for smoke tests, but they are not the source of truth for location, notifications, exact alarms, reboot recovery, or full-screen intent behavior.

Recommended manual matrix:

- API 33 or newer fresh install: allow notifications, allow location, allow exact alarms, confirm onboarding completes and Home has no repair banner
- API 33 or newer fresh install: deny notifications, confirm onboarding can still finish and Home plus Settings show notification repair actions
- API 31 or newer fresh install: deny exact alarms, confirm onboarding can still finish, scheduling is skipped, and Home plus Settings show exact alarm repair actions
- Device location mode: allow approximate-only location, confirm nearest city resolves and readiness remains healthy
- Device location mode: deny location, confirm manual city fallback is available and readiness degrades clearly
- Manual city mode: pick a city without device location and confirm alerts can still be ready
- Block the alarm notification channel, confirm Home plus Settings expose channel repair
- Disable full-screen alarm permission on Android 14 or newer, confirm alarm notifications still appear and Home plus Settings expose full-screen repair
- Reboot device with enabled alarms, confirm boot reconcile restores alarms
- Change timezone with enabled alarms, confirm timezone reconcile reschedules alarms correctly

Firebase Test Lab proposal:

- Physical Pixel 5 or newer on API 34
- Physical Pixel 4a or equivalent on API 33
- Physical Pixel 4 on API 31
- Virtual API 35 for broad smoke coverage only

Suggested split:

- Instrumentation UI tests: deterministic Compose state assertions and debug-hook driven flows
- Unit tests: readiness evaluator, reconcile logic, scheduling gates
- Manual physical-device pass before release: every permission and settings regression path
- Desktop banner preview: use `android/sky-banner-lab` for manual inspection of sun/moon banner behavior across arbitrary future timestamps and coordinates on Windows

See `android/DEBUG_TESTING.md` for the debug broadcast commands used during real-device runs.

Note on platform separation:

- The desktop sky-banner lab reuses the Android Kotlin calculation code directly and is intended for Android-side manual verification.
- iOS should keep its own native implementation and any equivalent preview tooling separate, even if it follows the same astronomical rules and visual behavior.

See [DESIGN.md](DESIGN.md) for detailed flows, algorithms, and extension notes.
