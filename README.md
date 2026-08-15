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
- Optional read-only Linux power-supply telemetry through direct sysfs, Shizuku/Sui, or a cached libsu root shell
- Extended fuel-gauge, charge-policy, JEITA, USB/DC/parallel-input, and cycle-depth details when the device exposes them
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

On many stock builds, SELinux blocks an ordinary app from reading world-readable files under `/sys/class/power_supply`. CellScope always uses Android's battery APIs first. In Settings, users can optionally grant Shizuku/Sui access or enable a root fallback. Privileged readers are restricted to reads from `/sys/class/power_supply`: each supply's `uevent`, a fixed allowlist of scalar attributes, and the fuel gauge's `device/cycle_counts_bins`. Shizuku started through ADB has shell identity, while Sui or libsu can provide root identity. Availability, units, and attribute names remain device-dependent.

## Sysfs collection boundaries

CellScope stores only values with a useful read-only interpretation. Raw fuel-gauge SOC is retained without applying a universal scale because vendor drivers use different ranges. Boolean charger-policy fields are stored independently from measured current and voltage, and USB, DC/wireless, and parallel charging paths remain separate.

The following inspected values are intentionally not collected:

- Writable controls such as `rerun_aicl`, `dp_dm`, `update_now`, `full_level`, charger test controls, and fake-call/media modes. CellScope never changes charge policy or fuel-gauge state.
- Duplicated HTC aliases such as `batt_vol`, `batt_current_now`, `batt_temp`, and `batt_power_meter` when a standard power-supply property provides the same measurement.
- Ambiguous learning internals such as `charge_now`, `charge_now_raw`, and `charge_now_error`; their meanings are driver-specific and they must not be labelled as remaining charge.
- Diagnostic bitfields and register dumps such as `htc_extension` and most of `batt_attr_text`. Reading that vendor blob performs a large PMIC diagnostic dump and is inappropriate at the normal sampling cadence.
- Flash-power headroom and test state (`flash_current_max`, `flash_active`, `flash_trigger`), which describe camera-flash constraints rather than ordinary battery telemetry.
- Raw fuel-gauge SRAM and charger debugfs entries. They require stronger privileges, expose controls alongside data, and do not have a stable cross-device ABI.

## Recording behavior

Continuous recording is enabled by default after the first launch and runs as a `specialUse` foreground service. It starts again after boot or an app update and uses `START_STICKY` for ordinary process recreation. The Settings screen can disable collection; disabled time is retained as a labelled gap. Unexpected missing intervals are labelled as “Phone off or recorder unavailable.”

Android intentionally prevents immediate restart after a user force-stops the app or uses the system Active Apps Stop control. CellScope detects and annotates the missing interval when it is next allowed to run.

Before a Play Store release, update `compileSdk`/`targetSdk` to the then-current required API level and complete the foreground-service declaration in Play Console.
