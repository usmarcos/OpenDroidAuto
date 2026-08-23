# OpenDroidAuto

Android Auto receiver for legacy Android ARM32 head units, including Honda Connect units and compatible generic multimedia systems.

## Connection behavior

- USB is the primary path. After Android grants access to the phone and associates OpenDroidAuto as its handler, the app switches the phone to AOAP mode and opens Android Auto automatically.
- Selecting **Exit** stops the session and keeps the dashboard open. Reconnect the USB cable to start a new automatic session.
- Wi-Fi is optional. Enable it in settings only after the phone hotspot is already saved by Android on the head unit. The app uses the hotspot gateway on TCP port 5277 and retries short connection failures automatically.
- Android controls USB permission. A non-rooted head unit can still show the system dialog on a first connection or after a device is disconnected; the app does not bypass that permission.

## Compatibility

- Android 4.x and `armeabi-v7a` remain the supported baseline.
- Honda integration is used only when Fujitsu/Honda system services are present. Generic head units fall back to standard Android audio and input behavior.
- Android Auto Wi-Fi Direct is not implemented. Use a saved phone hotspot.

## Build

Install JDK 17, Android SDK platform 34, and NDK `17.2.4988734`, then run:

```sh
./gradlew test
./gradlew assembleRelease
```

The APK is created at `app/build/outputs/apk/release/HondaAppCenter_A1.apk`.

For a production-signed APK, set these environment variables before the release build:

```text
ODA_RELEASE_STORE_FILE
ODA_RELEASE_STORE_PASSWORD
ODA_RELEASE_KEY_ALIAS
ODA_RELEASE_KEY_PASSWORD
```

Without them, the legacy debug-signing behavior is retained for local installation.

## Diagnostics

The home dashboard reports permission, AOAP switching, connection, error, and retry states. Enable external logs only when diagnosing a hardware problem; protocol logging can contain sensitive connection data.

## License

GPL-3.0. See [LICENSE](LICENSE).
