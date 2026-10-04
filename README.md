# PedalMate

PedalMate: an Android cycling dashboard combining Power Zone training with online chess.

Sideloaded app for a Peloton Bike+ (Android 10, API 29, 1920x1080 landscape). Phase A is the cycling side: Bike+ and BLE heart-rate sensors, FTP and power zones, structured workouts, ride recording, and an in-ride overlay.

## Build

Prerequisites: [mise](https://mise.jdx.dev) and the Android SDK (`ANDROID_HOME`, set in `mise.toml`). The default system JDK is too new for Gradle 8.9, so always run Gradle through mise (JDK 17).

```bash
mise trust
mise exec -- ./gradlew testMockDebugUnitTest assembleMockDebug assembleRealDebug
```

The `mock` flavor (`dev.pedalmate.mock`) uses simulated sensors and runs on an emulator; the `real` flavor (`dev.pedalmate`) binds to the Bike+ service.

## Docs

- Design spec: `docs/superpowers/specs/2026-10-04-pedalmate-design.md`
- Phase A plan: `docs/superpowers/plans/2026-10-04-phase-a.md`

## Notices

"Peloton" and "Lichess" are used only descriptively to identify the hardware and services PedalMate interoperates with. PedalMate is not affiliated with, authorized, sponsored, or endorsed by Peloton Interactive, Inc. or Lichess. See `LICENSE`, `NOTICE` and `THIRD_PARTY_NOTICES.md`.
