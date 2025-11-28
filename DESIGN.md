# Suntime Alerts — Design

## Goal
Provide reliable sunrise/sunset-relative alarms that reschedule automatically using on-device solar calculations, robust time handling, and idiomatic native stacks for iOS and Android.

## User flows
- **Onboarding**: Welcome → location permission → notification permission → initial alarm toggles and offsets (sunrise & sunset).
- **Main screen**: Shows today’s sunrise/sunset, alarm toggles, offsets, next scheduled alarm times, and a manual “Recompute & reschedule” action.
- **Settings**: Location mode (device vs fixed lat/lon), default offsets, 12h/24h, about/version.
- **Background**: Daily recomputation (at launch, post-midnight, or via OS notifications/time-change hooks) plus reactions to location/timezone/DST changes.

## Domain model (shared concepts)
- `SunEventType`: `sunrise` | `sunset`.
- `SunEvent`: `{ date, type, dateTime, locationUsed }`.
- `SunAlarmConfig`: `{ enabled: Bool, eventType: SunEventType, offsetMinutes: Int }`.
- `UserSettings`: `{ locationMode, fixedLocation(lat, lon), sunriseConfig, sunsetConfig, timeFormat24h, onboardingComplete }`.

## Core services
- `LocationService`: acquires device location (with permissions) or returns fixed location from settings.
- `SunTimesCalculator`: pure astronomical calculation returning sunrise and sunset `ZonedDateTime`/`Date` for a given date/location/timezone using NOAA-style solar position formulas (Julian day, solar mean anomaly, ecliptic longitude, solar transit, hour angle, altitude correction).
- `SunScheduleService`: combines calculator + alarm configs to produce trigger times for today/tomorrow, applying offsets and skipping cases without events (e.g., polar day/night). Handles DST/timezone by always working in the user’s current zone and reacting to time-change notifications.
- `NotificationScheduler`: platform wrapper for scheduling/canceling local notifications/alarms.
- `SettingsStore`: persistence for `UserSettings` via UserDefaults (iOS) or DataStore (Android).

## Scheduling strategy
1. On app launch and once daily (post-midnight), recompute sunrise/sunset for the current location and timezone.
2. When location changes significantly or time zone/DST changes occur, recompute immediately.
3. For each enabled alarm config, compute `eventTime + offsetMinutes` and schedule an exact notification for that day; also schedule the next day’s alarms to cover background scenarios.
4. If a day lacks sunrise/sunset (extreme latitudes), skip scheduling and surface a warning in UI/logging.

## Time handling & edge cases
- Work in timezone-aware dates; never add offsets to naive timestamps.
- Use calendar-based triggers (iOS) or exact alarms (Android) to avoid drift.
- Observe system notifications/broadcasts for `significantTimeChange` (iOS) and `TIMEZONE_CHANGED`/`TIME_SET` (Android).
- DST: recomputation uses the current zone rules per day, so offsets remain aligned to wall-clock sunrise/sunset.

## Extensibility notes
- Additional alarms (golden hour, civil dawn) can reuse `SunTimesCalculator` extensions.
- Weather-aware skips or quiet hours can layer atop `SunScheduleService` without altering domain models.
- DataSync/backup can be added by swapping `SettingsStore` implementations.

## Platform architecture
### iOS (SwiftUI + MVVM)
Layers:
- **Domain**: models and `SunTimesCalculator` (pure Swift).
- **Data**: `SettingsStore`, `LocationService`, `SunScheduleService` orchestrating scheduling.
- **Services**: `NotificationScheduler` wrapper for `UNUserNotificationCenter`.
- **Presentation**: SwiftUI views and view models using async/await.

### Android (Compose + MVVM)
Layers:
- **Domain**: models and `SunTimesCalculator` (pure Kotlin).
- **Data**: `SettingsStore` (DataStore), `LocationService` (Fused Location Provider), `SunScheduleService` for scheduling logic.
- **Services**: `NotificationScheduler` built on AlarmManager/NotificationCompat; WorkManager for daily recompute.
- **Presentation**: Compose navigation, screens, and ViewModels powered by coroutines/Flow.

## Solar calculation outline (NOAA-inspired)
1. Convert date to Julian Day; compute Julian centuries since J2000.
2. Solar mean anomaly → equation of center → ecliptic longitude.
3. Solar transit and declination from ecliptic longitude.
4. Hour angle for target altitude (-0.833° for sunrise/set) using latitude and declination.
5. Calculate sunrise/sunset UTC by combining transit and hour angle, then convert to local timezone.
6. Apply small atmospheric refraction correction; clamp results when no event exists (polar day/night).

## Testing approach
- Unit tests: `SunTimesCalculator` against known city/date pairs (allow tolerance), offset math (event time ± minutes), schedule generation for enabled/disabled configs.
- UI/Instrumentation: basic onboarding and main screen presence checks; snapshot tests optional.
- Time edge cases: tests around DST transitions and timezone changes to ensure recomputation path is invoked.

## CI skeleton
- GitHub Actions workflow to lint/build and run unit tests for both platforms (where runners permit).
- Cache dependencies to keep runs fast; allow iOS job to be opt-in (macOS runners).
