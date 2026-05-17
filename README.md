# M. Parropeato

M. Parropeato is a novelty Android and Wear OS app that uses speech-to-text to listen to what you say and then parrots it back using text-to-speech.

## Help Map Wear OS Button Settings

M. Parropeato can open Samsung Wear OS **Buttons and gestures** settings so users can map a hardware button to the app.
Other Wear OS brands may use different private settings intents or activities.
If you have a Pixel Watch, OnePlus Watch, Mobvoi/TicWatch, Xiaomi, Fossil, or another Wear OS device, reports are welcome.

To collect the info:

1. Enable developer options and ADB debugging on the watch.
2. Connect ADB, then confirm the device id:
   ```bash
   adb devices
   ```
3. Open the watch Settings app and manually navigate to the button or gesture settings screen.
4. While that screen is visible, run:
   ```bash
   adb -s DEVICE_ID shell dumpsys activity activities | grep -E "mResumedActivity|topResumedActivity|activityToFront"
   adb -s DEVICE_ID shell dumpsys activity top | grep ACTIVITY
   ```
5. Find the settings package and activity filters:
   ```bash
   adb -s DEVICE_ID shell pm list packages | grep -i settings
   adb -s DEVICE_ID shell dumpsys package SETTINGS_PACKAGE | grep -A20 ACTIVITY_CLASS
   ```

Please open a GitHub issue with the watch model, Wear OS / vendor software version, the visible Settings path, and the command output.
Bonus points if it is confirmed that this command launches the same screen from ADB:
```bash
adb -s DEVICE_ID shell am start -n SETTINGS_PACKAGE/ACTIVITY_CLASS
```

## For Developers

Detailed instructions for project setup, adding new UI languages, and the release process can be found in [CONTRIBUTING.md](CONTRIBUTING.md).

If you are using an AI coding assistant, please refer to [AGENTS.md](AGENTS.md) for project-specific instructions and slash commands.
