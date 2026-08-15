# CellScope

CellScope is a local-first Android battery telemetry recorder. It displays live fuel-gauge readings and maintains one continuous device timeline that can be charted or exported.

## Features

- Live battery percentage, voltage, current, remaining charge, temperature, average current, and estimated power
- Default-on foreground recording with a persistent notification and 1–60 second sample intervals
- Automatic restart after first launch, boot, app update, and ordinary process recreation
- Room-backed continuous time-series history with schema-preserving v1 migration
- Charts for level, voltage, current, charge, temperature, and estimated power
- Min/max bucket downsampling that retains brief spikes in long sessions
- Explicit and inferred gap annotations, with optional visual gap collapsing
- Local CSV export through Android's document picker
- Explicit “Not reported” state for device-dependent properties
- No internet permission

## Build

Requirements: JDK 17 or newer and Android SDK Platform 35.

```sh
./gradlew testDebugUnitTest assembleDebug
```

The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

## Test on a device

```sh
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Battery properties depend on the phone's fuel-gauge hardware and firmware. Emulator state changes are useful for UI testing, but voltage, current, charge-counter, and energy behavior must be validated on physical devices.

## Recording behavior

Continuous recording is enabled by default after the first launch and runs as a `specialUse` foreground service. It starts again after boot or an app update and uses `START_STICKY` for ordinary process recreation. The Settings screen can disable collection; disabled time is retained as a labelled gap. Unexpected missing intervals are labelled as “Phone off or recorder unavailable.”

Android intentionally prevents immediate restart after a user force-stops the app or uses the system Active Apps Stop control. CellScope detects and annotates the missing interval when it is next allowed to run.

Before a Play Store release, update `compileSdk`/`targetSdk` to the then-current required API level and complete the foreground-service declaration in Play Console.
