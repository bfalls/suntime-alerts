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

See [DESIGN.md](DESIGN.md) for detailed flows, algorithms, and extension notes.
