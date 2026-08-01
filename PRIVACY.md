# Privacy Policy for Suntime Alerts

Last updated: August 1, 2026

This Privacy Policy describes how the current Android alpha version of Suntime Alerts handles information during closed testing and limited early use.

## Summary

Suntime Alerts is designed to work primarily on-device.

In the current alpha:
- The app does not require user accounts.
- The app does not run a project-hosted backend.
- The app does not upload location history to servers operated by this project.
- The app does not include advertising, analytics, or crash-reporting services operated by this project.

## Information the App Uses

Depending on the features a user enables, the app may access or store:
- Device location, if the user grants location permission.
- A manually selected city or manually selected coordinates.
- Alarm preferences and scheduling settings.
- A last resolved device coordinate needed for app behavior.
- Notification and exact-alarm related state needed to configure alerts.

This information is used to provide the app's core functionality and is currently stored locally on the device.

## How Location Information Is Used

If location permission is granted, Suntime Alerts may use location information to:
- Calculate sunrise and sunset times.
- Support device-based alert scheduling.
- Support related in-app visuals and readiness checks.

In the current alpha implementation, location information is intended to remain on-device and is not uploaded to project-operated servers.

## Feedback Email

If a user chooses to send feedback from within the app:
- The app opens the user's installed email app.
- The app pre-fills a draft email with:
  - The feedback text the user entered
  - App version
  - Android version
  - Device manufacturer and model
- The email is not sent automatically.
- The user reviews the draft and chooses whether to send it.

The project maintainer receives only the information the user chooses to send by email.

## Data Sharing

The current alpha does not share user data with project-operated advertising, analytics, or cloud-processing services because those services are not part of the current implementation.

If the app is distributed through Google Play or another platform, that platform may collect its own operational or analytics data under its own terms and privacy policies.

## Data Retention

Because the current alpha stores its working data locally on-device:
- App settings and related data remain on the device until the user clears app data or uninstalls the app, subject to Android platform behavior and backup settings.
- Feedback emails sent by testers may remain in the sender's email account and the maintainer's email systems until deleted.

## Security

This is an alpha build intended for limited testing.

It should not be treated as a hardened product for sensitive or high-stakes use. Users should not rely on it as the sole source of critical wake-up, medical, safety, or emergency alerts.

## Public Repository Notice

This project is developed in a public source repository.

If users open issues, pull requests, or discussions in the public repository, that content may be publicly visible. Do not post private personal information, exact home address data, or sensitive device data in public issue trackers.

## Children's Privacy

Suntime Alerts is not directed to children and is not intended for child-focused use.

## Changes to This Policy

This policy may be updated as the app changes. If the app's data practices materially change, this policy should be updated before those changes are distributed to testers or users.

## Contact

For privacy or feedback questions, contact: `suntimealerts@gmail.com`
