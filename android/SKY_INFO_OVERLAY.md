# Sky information overlay

The Android Home banner keeps its 200 dp height and existing terrain geometry.
A 48 dp row overlays the foreground with one white text line and a colored
outlined category icon in a 28 dp dark circle. `showIconCircle` can disable the
circle without changing the layout.

Messages rotate every eight seconds, with a short fade-out followed by a
fade-in. There is no scrolling or size animation. Clock updates change the
current message in place, preserving the rotation timer. Both clock updates
and rotation stop when Home is in the background.

## Context and messages

Periods follow the selected location's solar events rather than fixed hours:

| Period | Available messages, up to five |
| --- | --- |
| Dawn: rising Sun, between astronomical dawn and sunrise | Sunrise, photo window, next dawn boundary, daylight duration, daylight change |
| Daylight: sunrise to sunset | Sunset, nearby photo window, daylight duration, daylight change, Sun position |
| Evening twilight: sunset to astronomical dusk | Next dusk boundary, photo window, time since sunset, astronomical darkness, Moon phase |
| Night: below astronomical twilight | Next sunrise, next dawn boundary, astronomical darkness, Moon phase, next Moon event |

Unavailable events are omitted. Live Moon phase and Sun position supply
fallbacks to retain two useful messages when solar events are absent. Large
text remains one line and may ellipsize; accessibility retains the full text.

`SkyInfoCalculator` owns the metric snapshot and caches daily event scans by
date, time zone, and coordinates. Moon event windows refresh every 15 minutes
or at a horizon crossing. Live positions and phase refresh with the clock.
`SkyInfoMessages` selects and formats the snapshot; it does no astronomy.

The desktop `sky-banner-lab` compiles that same message source and calculator.
`SkyInfoPresentation` supplies both renderers' palette and transition timing;
the lab paints native Swing outlines and lets you toggle icon circles or run
the selected preview clock forward. No Android UI dependencies are needed.

Twilight uses solar-center altitudes of -6°, -12°, and -18° from the
[US Naval Observatory definitions](https://aa.usno.navy.mil/faq/RST_defs).
Photography uses the [PhotoPills convention](https://www.photopills.com/articles/mastering-golden-hour-blue-hour-magic-hours-and-twilights):
golden light from -4° to +6°, blue light from -6° to -4°. Only active windows
or windows starting within two hours are displayed. Darkness duration is
astronomical dusk to the following astronomical dawn. Solar rise/set times
reuse the existing calculator so the banner and alarm times agree.

## Advanced dashboard

Home opens `SkyDashboardDialog` when the banner information row is tapped.
A small chevron and the normal tap ripple indicate that more information is
available. The banner and terrain retain their original dimensions.

The floating panel is limited to 85% of available screen height. Its header
and Close button remain fixed while the dashboard scrolls. Tapping outside,
Close, or Android Back dismisses it; tapping inside does not. Expanded groups
and the selected timeline period survive ordinary metric updates. Expanding
a group smoothly scrolls its bottom to the bottom of the viewport, with a
small inset. Clock updates do not repeat that scroll.

- Today's light: daily duration/change, sunrise/sunset, live event countdown,
  and a tappable 24-hour light timeline with civil, nautical, astronomical,
  daylight, and darkness segments.
- Sun/Moon tiles: current elevation and compass bearing; the Moon uses the
  shared upright phase orientation calculation.
- Photography: morning/evening golden and blue windows, durations, and the
  current or next opportunity.
- Twilight: all six boundaries, each complete twilight interval's duration,
  sunset-to-sunrise night, and astronomical darkness as separate quantities.
- Moon: current phase/illumination and the current or next above-horizon
  passage's rise/set times, bearings, and peak altitude.
- Where to look right now: north-up Sun/Moon compass, solar noon/midnight,
  altitude at noon, and sunrise/sunset bearings.

The dashboard follows the app's light/dark theme and the phone's system
12/24-hour time format, matching the sunrise/sunset headers.
The shared Compose state observes `Settings.System.TIME_12_24` and refreshes
on resume, so changing the phone setting updates the headers and an open
dashboard without waiting for a clock tick or restarting the app. The
observer is unregistered when the screen leaves composition.
Events outside today's date include a date; unavailable events have explicit
labels. Coordinates and the snapshot's timezone appear in the header.

`SkyInfoCalculator` caches the added daily timeline, twilight intervals,
and solar transits. Solar noon/midnight find zero/180-degree hour-angle
crossings from the existing ephemeris, rather than assuming clock noon or
the midpoint between rise and set. See the
[USNO transit definition](https://aa.usno.navy.mil/faq/RST_defs).
Timeline widths use elapsed time, including 23/25-hour DST days.

The dashboard is Android-only. The desktop lab continues to share the
astronomy and banner messages without a dashboard UI. Lunar age, upcoming
phase forecasts, and annual/seasonal charts are reserved for the next stage.

The optional `onOpenAdvancedInfo: (SkyInfoMetrics) -> Unit` callback remains
available for callers that want to replace Home's default dashboard action.

## Verification

`SkyInfoCalculatorTest` covers solar periods, altitude crossings, cache
invalidation, midnight, DST, and polar locations. `SkyInfoMessagesTest` covers
countdowns, relevant message counts, expired windows, and missing events.
`SkyInfoOverlayTest` checks rotation, live text updates, the navigation hook,
and unchanged banner height; it saves banner PNGs for the four periods in
the test app's external files directory.

`SkyDashboardDialogTest` checks opening from Home, all dismissal paths,
inside taps, live updates with expanded groups, large text, and light/dark
and polar rendering. It saves dashboard PNGs alongside the banner previews.
Unit tests cover transit, full-day/DST timeline coverage, both photographic
passes, adjoining nights, polar omissions, and dated 12/24-hour formatting.
