# Screen Shots For Play Store

Run the instrumented test to collect screenshots, then stitch the
cute-icons shots into a single rainbow composite with `slice.py`.

`slice.py` discovers all `*_cute_icons_*.png` files in the given directory
(sorted by filename) and writes `cute_icons_rainbow.png` alongside them.

## One Time Setup

Install the Python Imaging Library (PIL) needed by `slice.py`:
```shell
brew install pillow
```

## Mobile (phone must be connected)

From the repo root:
```shell
./gradlew :mobile:connectedDebugAndroidTest \
  --no-configuration-cache \
  -Pandroid.device.serial=emulator-5556 \
  -Pandroid.testInstrumentationRunnerArguments.class=com.swooby.parropeato.presentation.PlayStoreScreenshotTest
adb -s "emulator-5556" pull /sdcard/Pictures/Parropeato/play-store/mobile/ ./google-play-store/3.0.0/
python google-play-store/slice.py google-play-store/3.0.0/mobile
```

## Wear OS (watch must be connected)

From the repo root:
```shell
./gradlew :wear:connectedDebugAndroidTest \
  --no-configuration-cache \
  -Pandroid.device.serial=emulator-5554 \
  -Pandroid.testInstrumentationRunnerArguments.class=com.swooby.parropeato.presentation.PlayStoreScreenshotTest
adb -s "emulator-5554" pull /sdcard/Pictures/Parropeato/play-store/wear/ ./google-play-store/3.0.0/
python google-play-store/slice.py google-play-store/3.0.0/wear
```
