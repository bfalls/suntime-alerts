# Suntime Alerts — Design

## Goal
Provide reliable sunrise/sunset-relative alarms that reschedule automatically using on-device solar calculations, robust time handling, and idiomatic native stacks for iOS and Android.

## User flows
- **Onboarding**: Welcome → location permission → notification permission → initial alarm toggles and offsets (sunrise & sunset).
- **Main screen**: Shows today’s sunrise/sunset, alarm toggles, offsets, next scheduled alarm times, and a manual “Recompute & reschedule” action.
- **Settings**: Location mode (device vs fixed lat/lon), default offsets, 12h/24h, about/version.
- **Background**: Daily recomputation (at launch, post-midnight, or via OS notifications/time-change hooks) plus reactions to location/timezone/DST changes.

## Cross-platform UI design

### Visual system
- **Color palette**: Deep navy background (#0B1D33), warm accent for sunrise (#F9A826), cool accent for sunset (#5AD1FF), and neutral surfaces (#121E2A/#182435) to balance readability. Text uses high-contrast off-white (#F4F7FB) with muted secondary text (#9BB1CC). Buttons adopt accent colors with rounded (12 pt / 12 dp) corners.
- **Typography**: Large, calm headings (SF Pro/Roboto, semi-bold), with mono-like digits for times to reduce jitter when animating. Consistent spacing scale (8 pt grid).
- **Iconography**: Simple sun/moon, location pin, calendar, and bell icons with outlined style for inactive states and filled accent for active.
- **Layout rhythm**: Stacked cards on scrollable surfaces; key actions pinned to the bottom (primary CTA: “Save & Schedule” on onboarding, “Recompute” on home). All cards share consistent padding and corner radius.

### Onboarding
- **Screen 1 — Welcome**: Brief value statement, illustration, CTA “Get started”. Secondary link to privacy/terms.
- **Screen 2 — Location choice**: Binary picker: “Use current location (GPS)” vs “Set a place manually”. Manual choice reveals search field plus lat/lon entry with map preview pin; GPS choice explains permission rationale. Continue button remains disabled until a choice is made.
- **Screen 3 — Notifications**: Explains alarms; “Enable alerts” primary, “Skip for now” secondary. If skipped, alarms default to off.
- **Screen 4 — Initial alarms**: Two stacked cards (Sunrise, Sunset) each with toggle, wheel/stepper for offset (± minutes), and preview of next trigger time. Optional recurrence chip row: “Every day” (default), “Weekdays”, “Weekends”, “Custom…” (opens day-of-week picker).
- **Screen 5 — Finish**: Summary of selections with “Save & Schedule” button.

### Home (Today view)
- **Header**: Current date, location label (“Auto • City, Country” or custom name/coords), and quick action to change location. Tap reveals bottom sheet to switch mode or edit manual coordinates.
- **Sun timeline**: Horizontal pill showing sunrise → solar noon → sunset with times; animated gradient across accent colors.
- **Alarm cards (sunrise & sunset)**:
  - Toggle to enable/disable.
  - Offset control: pill buttons (-30, -15, 0, +15, +30) plus fine-grain stepper (+/− 1 min) opening number entry.
  - Recurrence row: chips for day-of-week; “Once” selection opens date picker for one-off scheduling.
  - Next trigger label (“Next: Tue 6:42 AM • 18m before sunrise”).
  - “Skip next” inline link to cancel only the upcoming occurrence.
- **Actions**: Primary button “Recompute now” (uses latest location/timezone). Secondary “View schedule” navigates to a list of upcoming events for the next 7 days.

### Schedule view
- List grouped by date with sunrise/sunset entries. Each item shows offset and recurrence origin (e.g., “From sunrise rule: weekdays”). Swipe/delete to cancel single instance (Android), context menu on iOS.

### Settings
- **Location**: Auto vs manual. Manual includes coordinate entry, place name label, and “Use device time zone” toggle for travelers.
- **Time format**: 12h/24h switch.
- **Alarms**:
  - Default offsets for sunrise and sunset.
  - Recurrence defaults (Every day/Weekdays/Weekends/Custom).
  - Quiet hours toggle (mute notifications between times; skips scheduling within window with warning badge on cards).
- **Accessibility**: Larger text toggle (ties into Dynamic Type / fontScale), high-contrast theme toggle (reduces gradients, increases contrast), and haptic feedback toggle for controls.
- **Maintenance**: “Rebuild schedule” button, “View log” for recent scheduling actions (debug), “About” with version/build.

### Location input UX
- **Auto-discover**: Prompts for permission and displays detected place name; shows last refreshed time and a retry button. Errors surface inline with retry and manual entry option.
- **Manual entry**: Search bar (forward-compatible with geocoding); for now, accept latitude/longitude fields with validation and preview map image placeholder. “Save location” requires valid numbers.
- **Fallback**: If GPS is denied, app defaults to manual entry prompt and disables “auto” until permission granted.

### Alert configuration UX
- **Offsets**: Steppers + preset chips for speed; show computed wall-clock time inline for clarity.
- **Recurrence**: Day-of-week chips (M–S) with “Every day/Weekdays/Weekends/Once” quick presets. One-off scheduling uses date picker.
- **Per-event labels**: Optional text field for custom note shown in notification (shared pattern on both platforms).
- **Multiple alerts**: The user can configure multiple alerts based on either sunrise or sunset. Example: alert on Friday 30mins before sunset and another alert on Friday 2hrs before sunset. If it helps, make a limit of four alerts per day per sun event, thus for the same day a max of 4 alerts based on sunrise and 4 alerts based on sunset, for a total of 8.

### Interaction patterns (shared iOS/Android)
- Bottom sheets/modal sheets for edits (location, recurrence, offset number entry).
- Pull-to-refresh on Home recomputes schedule.
- Toast/snackbar for background updates (“Schedule refreshed”).
- Empty states: If no upcoming event (polar day/night or all disabled), show informative illustration and CTA to adjust settings.

## Implementation steps
1. **Design system tokens**: Add shared color/typography constants on each platform matching the palette and spacing above. Update themes (SwiftUI Color assets, Compose Theme.kt) without altering project structure.
2. **Onboarding flow**: Build SwiftUI/Compose screens and navigation, persisting choices to `SettingsStore`. Gate main content until onboarding complete flag is set.
3. **Location sheet**: Reuse existing `LocationService` for auto; add manual entry UI backed by validation and storage of fixed coordinates. Provide retry and error states.
4. **Alarm cards**: Implement offset chips + steppers, recurrence chips, and one-off date picker. Wire to existing `SunAlarmConfig` and scheduling service; support “skip next” by canceling the next scheduled trigger only.
5. **Schedule list**: Render upcoming events derived from `SunScheduleService`; add swipe/context delete for single instance and recompute afterward.
6. **Settings**: Add new fields (recurrence defaults, quiet hours, accessibility toggles) and ensure `SettingsStore` persistence. Guard quiet hours behavior in scheduler.
7. **Feedback and accessibility**: Hook haptics on toggles/buttons where appropriate; respect system font scales and high-contrast mode. Ensure color contrast meets WCAG AA.
8. **Testing**: Add UI tests for onboarding, location selection, and alarm card interactions. Extend unit tests for quiet hours and recurrence logic.
9. **Parity review**: Visual QA on both platforms to align padding, typography, and interaction flows; capture screenshots for release notes.

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
