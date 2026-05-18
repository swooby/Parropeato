# Android Testing

## Diagnostics consent locale screenshots

The mobile and wear instrumentation tests verify that the first-launch diagnostics consent message appears for every locale declared in `common/src/main/res/xml/locales_config.xml`. They also capture screenshots for human review, which is still required for text clipping and round-screen layout issues.

Run the tests from Android Studio, or with a connected device/emulator:

```bash
./gradlew :wear:connectedDebugAndroidTest
./gradlew :mobile:connectedDebugAndroidTest
```

The screenshots are published through `MediaStore` into shared media storage so they survive target-app uninstall/reinstall behavior during test runs:

```text
/sdcard/Pictures/Parropeato/diagnostics-consent/wear/
/sdcard/Pictures/Parropeato/diagnostics-consent/mobile/
```

Pull the Wear screenshots with:

```bash
adb -s emulator-5554 pull /sdcard/Pictures/Parropeato/diagnostics-consent/wear ./wear-diagnostics-screenshots
```

Pull the mobile screenshots with:

```bash
adb -s emulator-5554 pull /sdcard/Pictures/Parropeato/diagnostics-consent/mobile ./mobile-diagnostics-screenshots
```

Do not rely on `/data/user/0/...` for manual retrieval; it is private app storage and requires `run-as`, which fails after Android Studio removes the target package. Do not rely on `/sdcard/Android/data/...` either; scoped-storage behavior makes direct `adb shell ls` unreliable on modern Android and Wear OS images.

The test asserts that the exact localized consent message exists in the UI and has visible bounds. The screenshots are the final check for small-screen fit, clipping, and general readability.
