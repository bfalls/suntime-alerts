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
3. Run the `app` configuration on an API 24+ emulator or device.
4. If the Gradle wrapper JAR is missing (some CI environments omit it), regenerate with `./gradlew wrapper --gradle-version 8.2.1`.

## Development principles
- Shared domain concepts: `SunEventType`, `SunEvent`, `SunAlarmConfig`, `UserSettings`.
- Pure, on-device `SunTimesCalculator` (NOAA-inspired solar position math) for offline correctness.
- Daily recomputation and rescheduling reacting to time zone, DST, and location changes.
- Testable core logic with unit tests for solar calculations and scheduling offsets on both platforms.

See [DESIGN.md](DESIGN.md) for detailed flows, algorithms, and extension notes.
