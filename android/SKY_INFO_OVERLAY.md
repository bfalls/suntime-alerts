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

## Future details navigation

`HomeScreen`, `HomeScreenContent`, and `SkyInfoOverlay` accept an optional
`onOpenAdvancedInfo: (SkyInfoMetrics) -> Unit` callback. Once connected, the
entire row is a tap target and passes the current full snapshot. Until then,
the row is informational and exposes no inactive action.

## Verification

`SkyInfoCalculatorTest` covers solar periods, altitude crossings, cache
invalidation, midnight, DST, and polar locations. `SkyInfoMessagesTest` covers
countdowns, relevant message counts, expired windows, and missing events.
`SkyInfoOverlayTest` checks rotation, live text updates, the navigation hook,
and unchanged banner height; it saves banner PNGs for the four periods in
the test app's external files directory.
