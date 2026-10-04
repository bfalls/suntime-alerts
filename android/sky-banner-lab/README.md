Sky Banner Lab

Run this desktop preview on Windows with:

`..\tools\run-sky-banner-lab.bat`

Or from the `android` directory:

`.\gradlew.bat :sky-banner-lab:run`

This utility reuses the app's existing sky calculation code from:

- `com.bfalls.suntimealerts.alarm.domain.model`
- `com.bfalls.suntimealerts.alarm.domain.service`
- `SkyInfoMessages.kt` (platform-neutral message selection, wording, colors, and fade/rotation timing)

Use the controls to set:

- date/time
- time zone
- latitude
- longitude

The preview renders only the sky banner so you can manually inspect future dates such as tomorrow.

The banner also displays the same context-dependent information as Android Home:
two to five blurbs for dawn, daylight, evening twilight, or night, rotating every
eight seconds with a fade. The single line overlays the existing terrain.

- Keep **Advance preview time** off to hold your selected date/time while the
  messages rotate. Enable it to run the clock forward from that selection and
  watch countdowns and periods change.
- Toggle **Category icon circles** to compare the colored native outline icons
  with and without their dark background circles.
- Date/time, zone, and coordinate controls update the shared metric snapshot.
  Native Swing painting is separate from Compose; astronomy, message rules,
  text, palette, and transition timing use the same source files.

Run `.\gradlew.bat :sky-banner-lab:test` for rotation, countdown, and headless
rendering checks. The rendering test saves the four periods, with and without
circles, under `sky-banner-lab/build/overlay-previews/`.

Moon phase orientation also uses the shared `MoonPhaseMask` calculation. Its
lighting direction is measured in the upright viewing plane centered on the Moon,
equivalent to the bright-limb angle adjusted for the observer's parallactic angle
([orientation reference](https://github.com/mourner/suncalc#moon-illumination)).
The banner's north/south facing mode controls arc placement independently.

For the orientation regression, select October 4, 2026, 15:01, `America/Boise`,
latitude `43.61211`, longitude `-116.39151`. The terminator axis runs approximately
from 1:12 to 7:12 on a clock face. The previous fixed east/up projection incorrectly
placed it around 3:21 to 9:21.
