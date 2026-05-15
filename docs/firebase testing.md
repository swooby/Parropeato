# Firebase Testing

## Crashlytics — trigger a test crash

Both the mobile and wear apps have a **Test Crash** button at the bottom of the Settings screen (debug builds only, styled red). Tapping it throws a `RuntimeException` which Crashlytics captures and uploads on the next app launch.

1. Open the app → Settings → tap **Test Crash**.
2. The app will crash. Relaunch it — Crashlytics uploads the unsent report on startup.
3. Check **Firebase Console → Crashlytics** for the new issue (may take a minute to appear).

## Analytics — DebugView

By default, Analytics batches events and flushes them to the dashboard every few hours. To see events in real time, enable debug mode per device.

### Enable debug mode

```bash
# List connected devices first — you need the exact ID
adb devices

# Single device (or only one connected)
adb shell setprop debug.firebase.analytics.app com.swooby.parropeato

# Specific device (required when multiple are connected)
# Wrap IDs that contain spaces in quotes
adb -s "emulator-5554" shell setprop debug.firebase.analytics.app com.swooby.parropeato
adb -s "adb-RFAXB0XAP9L-DLA7Tw (2)._adb-tls-connect._tcp" shell setprop debug.firebase.analytics.app com.swooby.parropeato
```

Then restart the app. Logcat should confirm:
```
FA  Faster debug mode event logging enabled.
```

### View events

Open **Firebase Console → Analytics → DebugView**. Select the target device from the dropdown at the top. Events appear within seconds of occurring in the app.

### Disable debug mode

```bash
adb shell setprop debug.firebase.analytics.app .none.
```

### Caveats

- The `setprop` does **not** survive device reboots or emulator cold boots — re-run it each session.
- Each device (emulator or physical) must be targeted separately when multiple are connected.
- The main Analytics dashboard (not DebugView) has a processing delay of several hours — use DebugView for active development, the main dashboard for production monitoring.

## Known warnings

### `GoogleCertificatesRslt: not allowed`

```
PhFlagUpdateRegistry  W  Failed to register flag update listener which may lead to stale flags.
    SecurityException: GoogleCertificatesRslt: not allowed: pkg=com.swooby.parropeato ...
```

This appears on physical devices running debug/sideloaded builds. Play Services checks whether the app's signing certificate is in Google's internal allowlist (used for Phenotype feature-flag updates), which only includes apps published on the Play Store. It is **harmless** — Analytics and Crashlytics work normally. The warning disappears once the app is published.
