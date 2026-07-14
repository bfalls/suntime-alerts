# Play Store Closed Testing Checklist

Use this checklist when preparing the Android alpha for invited testers.

## Before Upload
- Confirm the feedback address in `android/app/src/main/java/com/bfalls/suntimealerts/alarm/presentation/ui/SettingsScreen.kt` is still correct.
- Confirm the app name, icon, and screenshots are acceptable for public-facing testers.
- Run:
  - `.\gradlew.bat testDebugUnitTest`
  - `.\gradlew.bat assembleDebugAndroidTest`
- Do one fresh-install test on a physical Android device.
- Verify onboarding, permissions, location selection, and alarm scheduling still work.
- Verify the in-app feedback button opens an email draft with device/app details appended automatically.

## Signing And Release Build
- Create or locate your Play App Signing keystore.
- Set release signing in the Android project if you have not already.
- Build an Android App Bundle (`.aab`) for Play distribution.
- Keep the keystore backed up in at least two secure places.

## Play Console Setup
- Create the app in Google Play Console.
- Set app category, contact email, and default language.
- Complete the App access questionnaire if required.
- Complete the Data safety form accurately.
- Add a privacy policy URL. Point it to the repo privacy notice or a hosted copy.
- Fill in content rating and target audience sections.

## Store Listing
- Short description:
  - Explain that this is an early alpha for sunrise and sunset alerts.
- Full description:
  - Explain current limitations clearly.
  - Mention that feedback is requested.
- Upload phone screenshots.
- Upload app icon and feature graphic if required.

## Closed Testing Track
- Create a closed testing track.
- Choose the tester model:
  - Email list if the group is small and controlled.
  - Google Group if you want easier list management.
- Add tester emails or attach the group.
- Upload the release `.aab`.
- Add release notes:
  - Keep them short.
  - Mention this is an alpha and feedback is welcome.
- Roll out the release to the closed track.

## Tester Instructions
- Send testers the Play opt-in link.
- Tell them to:
  - Join the test.
  - Install from Google Play.
  - Grant permissions when prompted.
  - Send feedback from the in-app feedback button or the listed email address.
- Ask them to include screenshots when possible.

## Feedback Handling
- Email is enough for an alpha.
- Create a dedicated inbox or alias just for app feedback.
- Triage messages into:
  - Bug
  - UX confusion
  - Feature request
  - Device-specific issue
- Track issues in GitHub Issues or a simple spreadsheet.

## Recommended Alpha Guardrails
- Be explicit that this is not a production alarm app yet.
- Warn users that exact alarms, notification settings, and vendor battery restrictions can affect reliability.
- Keep the tester pool small at first.
- Expand only after install, onboarding, and feedback flow are smooth.

## Nice Next Steps
- Add a simple release build workflow.
- Add crash reporting only if the tester pool grows enough to justify it.
- Add a lightweight feedback form backend only if email becomes hard to manage.
