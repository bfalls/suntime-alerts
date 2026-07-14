# Privacy Notice

This project is an early-stage open source mobile app. This notice covers both the repository and the current Android app behavior.

## Summary
- The app is designed to work primarily on-device.
- The current alpha does not require user accounts.
- The current alpha does not upload location history to a backend run by this project.
- The current alpha can open the user's email app to send feedback if the user chooses to do so.

## Data Used By The App

The app may access or store:
- Device location, if the user grants permission.
- Alarm preferences and scheduling settings.
- Selected manual coordinates.
- Last resolved device coordinate needed for app behavior.
- Notification and exact-alarm related state needed to configure alerts.

This data is currently stored locally on the device for app functionality.

## Location Data

Location is used to:
- Calculate sunrise and sunset times.
- Support device-based alert scheduling.
- Support sky banner rendering.

Location data is intended to stay on-device in the current alpha implementation.

## Feedback Email

If the user taps the in-app feedback option:
- The app opens the user's installed email app.
- The app pre-fills the draft with:
  - The feedback text the user typed
  - App version
  - Android version
  - Device manufacturer and model
- The email is not sent automatically.
- The user reviews and sends it manually.

The maintainer receives only the information the user chooses to send.

## Repository Privacy

If you open issues, pull requests, or discussions in the public repository:
- That content is public unless the platform says otherwise.
- Do not post private personal information, exact home address data, or sensitive device data in public issues.

For private testing feedback, prefer the dedicated feedback email address.

## Third-Party Services

The current alpha does not rely on a project-hosted backend for accounts, analytics, or feedback submission.

Distribution platforms such as Google Play may collect their own operational or analytics data under their own terms. If the app is distributed through Google Play closed testing, testers should also review Google's policies.

## Data Retention

Because the current alpha stores its working data locally on-device:
- Uninstalling the app should remove app-local data, subject to Android platform behavior.
- Emails sent by testers are retained in the maintainer's email systems until deleted.

## Security Expectations

This is an alpha build intended for limited testing.
- Do not treat it as hardened for sensitive or high-stakes use.
- Do not rely on it as the sole source of critical wake-up, medical, safety, or emergency alerts.

## Contact

For privacy or feedback questions, use `suntimealerts@gmail.com`.
