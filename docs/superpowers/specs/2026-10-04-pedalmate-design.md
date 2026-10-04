# PedalMate — Design + Implementation Plan

## Context

PedalMate: sideloaded Android app for the user's **Peloton Bike+** (board `topaz`, Android 10 / API 29, 1920x1080 landscape). Goal from `PedalMate.md`: ride structured Power Zone workouts while playing Lichess chess on the same screen, with live power/cadence/resistance from the bike, plus (new ask) **heart rate from Apple Watch**. Personal project, single user.

Why now: nothing existing does this. OpenRide (Apache-2) has verified Bike+ sensor access, BLE HR, zone math, Room history, but no interval workouts, no overlay, no chess. Grupetto (no license) proves a Compose overlay over other apps works on Peloton. OpenPelo is the PC-side ADB installer the user will use.

### Decisions made with user (2026-10-04)
- **Shape: overlay first, native later.** Phase A = floating PZ-workout overlay (SYSTEM_ALERT_WINDOW + foreground service) over the stock Lichess app. Phase B = native full-screen app, centered Compose chess board via Lichess Board API, per the layout in PedalMate.md.
- **Structure:** single `:app` Gradle module, package-per-subsystem, `mock`/`real` product flavors for sensor source.
- **Heart rate:** generic BLE Heart Rate Service client (GATT 0x180D/0x2A37). Apple Watch cannot broadcast BLE HR itself; user runs an iPhone relay app (BlueHeart / HeartCast / HR Broadcast) that advertises as a standard HRM. Same code path works for chest straps.
- **Lichess default game:** 5+3 blitz, casual (rated later). Phase A: stock Lichess app, any time control. Phase B (Board API): spec says *"Time controls: Rapid, Classical and Correspondence only. For direct challenges, games vs AI, and bulk pairing, Blitz is also possible."* → native default = **5+3 vs Lichess AI, level configurable** (`POST /api/challenge/ai`); secondary = rapid 10+5 casual human seek (`POST /api/board/seek`).
- **Workflow:** beads (`bd` 0.60) for task tracking. **Model roles: Sonnet subagents implement; Opus subagents verify/review (diff + test output); main session only orchestrates** (dispatch, relay, decide). User may also `/model opus` the main session. TDD; pure-Kotlin engine code JVM-unit-tested.
- **Reuse:** copy OpenRide's affernet AIDL + `BikeData` parcel, sensor data sources, BLE heart-rate package, `PowerZone`/`FtpEstimator`/`LiveAggregates`. `NOTICE` reproduces OpenRide's NOTICE verbatim (Apache-2.0 §4(d)). Each copied or adapted file starts with `// Derived from OpenRide (Apache-2.0), <original path>. Modified by PedalMate.` (§4(b)). OpenRide files have no header of their own to keep. Grupetto = reference only (no license, copy nothing).

### Verified technical facts
| Fact | Source |
|---|---|
| Bike+ streams over `IBikeInterface` (bind action `com.onepeloton.affernetservice.IBikeInterface`, pkg `com.onepeloton.affernetservice`); `registerCallback` = txn 15, `getBikeData` poll = txn 14; `IV1Interface` binds but never pushes on Bike+. AIDL method order is load-bearing; copy verbatim incl. placeholders. | OpenRide `docs/SENSOR_PROTOCOL.md`, `IBikeInterface.aidl` |
| `BikeData` parcel: `mRPM` long (rpm), `mPower` long (centi-watts, ÷100), `mCurrentResistance` int (0-100); ~21 Hz push. Package name must stay `com.onepeloton.affernetservice` (binder descriptor + `readParcelable` class name). | same |
| BLE scan on API 29 needs runtime `ACCESS_FINE_LOCATION`; `BLUETOOTH`/`BLUETOOTH_ADMIN` manifest-only (`maxSdkVersion=30`) | OpenRide `BlePermissions.kt`, manifest |
| Apple Watch HR → BLE requires iPhone relay app; appears as standard HRM | pelobuddy BlueHeart guide |
| Lichess Board API restriction text (above); scopes `board:play` (+`challenge:write`). Streams NDJSON: `GET /api/stream/event`, `GET /api/board/game/stream/{id}`; move `POST /api/board/game/{id}/move/{uci}` | lichess-org/api `lichess-api.yaml` |
| Local toolchain: Java 26 default (AGP needs 17 → mise pin), SDK platforms 30/33/36 (no 34), build-tools 33/36, only sys image android-36.1, adb, no gradle wrapper, no device attached | local checks |
| Grupetto manifest: `SYSTEM_ALERT_WINDOW` + `FOREGROUND_SERVICE`; Peloton shows the "draw over apps" prompt | grupetto README/manifest |

## Architecture

Package root `dev.pedalmate`:

```
sensor/     BikeDataSource, BoundBikeDataSource, PollableBikeDataSource, BikeSourceSupervisor, BikeMetrics(cadenceRpm, resistancePercent, powerWatts), ConnectionState,
            PelotonBikeInterfaceDataSource (real), BikeDataMapping, ServiceRebinder, MockBikeDataSource(+ScriptedProfile)
            + src/main/aidl/com/onepeloton/affernetservice/{IBikeInterface,IBikeCallback,BikeData,V3BikeData}.aidl, BikeData.kt, V3BikeData.kt
heartrate/  HeartRateDataSource, HeartRateMeasurement, HeartRateParser, BleScanner, AndroidBleScanner, BleHeartRateDataSource,
            BlePermissions, MockHeartRateDataSource
workout/    PowerZone, ZoneTable, FtpCalculator, PowerSmoother(3s), TargetStatus(BELOW/IN/ABOVE, ±3W + 2s debounce),
            WorkoutDefinition/Step (@Serializable), WorkoutRepository(assets), WorkoutEngine/WorkoutState/WorkoutEvent, CuePolicy
ride/       RideSnapshot, RideSession (composition root), RideRecorder (1 Hz, batched flush 5s), LiveAggregates, RideService (FGS, 250ms ticker)
audio/      CuePlayer (ToneGenerator, STREAM_MUSIC)
overlay/    OverlayController (WindowManager), ComposeOverlayHost (lifecycle/savedstate/viewmodel owners), OverlayContent, IntervalToast, OverlayPrefs
data/       AppDatabase, RideEntity, SampleEntity, GameEntity, DAOs, SettingsStore (DataStore), TokenStore, AppContainer (manual DI)
lichess/    LichessAuth (PKCE), LichessApi/LichessClient, NdjsonReader, LichessEvent, GameEvent            [Phase B]
chess/      ChessPosition (chesslib wrapper), GameSession (reducer), ClockTracker, BoardLayout                 [Phase B]
ui/         theme, setup/SetupActivity, debug/SensorDebugScreen, ride/RideScreen, summary/, history/        [ride/summary Phase B]
```

Key rules:
- **WorkoutEngine is pure Kotlin, no wall clock.** Driven by `advance(deltaMs)`; `Idle → Running ⇄ Paused → Finished`. `state: StateFlow<WorkoutState>` (step index/count, step + total elapsed/remaining, progress, currentStep{label, zone, wattRange}, nextStep?). `events: SharedFlow<WorkoutEvent>` (Started, IntervalChanged, Countdown 3/2/1, Skipped, Finished).
- **RideSession** owns bike source, HR source, engine, recorder, cue player; exposes `snapshot: StateFlow<RideSnapshot>` (combine of metrics, HR, smoothed power, FTP, current zone, target zone/range, TargetStatus, engine state, connection states). Commands: startWorkout/startFreeRide/pause/resume/skip/stop (B13 adds startFtpTest).
- **RideService** (foreground, START_STICKY) owns the app-scoped RideSession from AppContainer, runs the `elapsedRealtime`-delta ticker, hosts OverlayController. Phase A overlay and Phase B Activity both just collect `session.snapshot` and call the same commands; overlay auto-hides while PedalMate's Activity is visible.
- Flavors: `flavorDimensions += "sensor"`; `mock`/`real` differ only by `src/mock` vs `src/real` `SensorFactory.kt`. `SensorFactory` supplies both the bike source and the HR source (mock HR in the mock flavor, because the emulator has no BLE). `mock` gets `applicationIdSuffix ".mock"`.
- ZoneTable: integer percents 55/75/90/105/120/150. `bound_i = ceil(pct_i·ftp/100)` in integer math `(pct_i*ftp + 99) / 100`. Zone i covers `[bound_{i-1}, bound_i − 1]`, with Z1 low 0 and Z7 `≥ bound_6`. `zoneFor(p)` uses the same bounds and matches OpenRide `fraction < maxFraction`. FTP 200 → Z1 0–109, Z2 110–149, Z3 150–179, Z4 180–209, Z5 210–239, Z6 240–299, Z7 ≥300. FTP = round(0.95 × 20-min avg).
- Fair play: no engine/eval/hint code anywhere; review checklist item.
- Logging: tag `PedalMate`; copied OpenRide files keep their own tags (`PelotonBikeIface`, `BleHeartRate`).

## Scaffold

Files: `settings.gradle.kts`, `build.gradle.kts`, `gradle.properties`, `gradle/libs.versions.toml`, `gradle/wrapper/*` + `gradlew`, `app/build.gradle.kts`, `app/proguard-rules.pro`, `app/src/main/AndroidManifest.xml`, `app/src/main/assets/workouts/*.json`, `app/src/{mock,real}/java/dev/pedalmate/sensor/SensorFactory.kt`, `mise.toml`, `.gitignore`, `LICENSE` (Apache-2.0), `NOTICE`, `THIRD_PARTY_NOTICES.md`, `README.md`, `docs/superpowers/{specs,plans}/`.

`mise.toml`: `[tools] java = "zulu-17"` (reuses installed `zulu-17.46.19.0`); `[env] ANDROID_HOME = "{{env.HOME}}/Android/Sdk"`. Run `mise trust` once. Subagents run Gradle as `mise exec -- ./gradlew …`. Bootstrap wrapper once: `mise exec java@17 gradle@8.9 -- gradle wrapper --gradle-version 8.9` (Gradle 8.9 cannot run on the default JDK 26).

Versions (OpenRide's proven set): AGP 8.5.2, Kotlin 2.0.21 (+compose, +serialization plugins), KSP 2.0.21-1.0.28, Compose BOM 2024.09.03, activity-compose 1.9.2, lifecycle 2.8.6, Room 2.6.1, coroutines 1.9.0, kotlinx-serialization-json 1.7.3, OkHttp 4.12.0 (+mockwebserver), chesslib 1.3.4 (JitPack: `com.github.bhlangonijr:chesslib`; not on Maven Central), DataStore 1.1.1, androidx.browser 1.8.0, security-crypto 1.1.0-alpha06, junit 4.13.2, turbine 1.1.0, robolectric 4.13, androidx.test core 1.6.1, androidx.test.ext junit 1.2.1, kotlinx-coroutines-test 1.9.0. Gradle 8.9, JVM 17. `compileSdk 34` (install `platforms;android-34`), **`minSdk = targetSdk = 29`** (device fixed; avoids package-visibility gates). `buildFeatures { compose; aidl; buildConfig }`. `testOptions.unitTests { isIncludeAndroidResources = true; isReturnDefaultValues = true }`; `src/test/resources/robolectric.properties` with `application=android.app.Application`. `lint { disable += "ExpiredTargetSdkVersion" }` so `assembleRelease`/lintVital passes at targetSdk 29.

`settings.gradle.kts` repositories: `google()`, `mavenCentral()`, `maven("https://jitpack.io") { content { includeGroup("com.github.bhlangonijr") } }`.

Manifest (A): `SYSTEM_ALERT_WINDOW`, `FOREGROUND_SERVICE`, `BLUETOOTH`, `BLUETOOTH_ADMIN` (maxSdk 30), `ACCESS_FINE_LOCATION`, `INTERNET`, `WAKE_LOCK`; `SetupActivity` (launcher), `RideService android:foregroundServiceType="location|connectedDevice"` (started with `startForeground(id, n, TYPE_LOCATION or TYPE_CONNECTED_DEVICE)`, plus a notification channel; API 29 introduced the `location` FGS type, needed for BLE scans while another app is foreground). Phase B adds `OAuthRedirectActivity` for `pedalmate://oauth`.

## Phase A tasks (overlay v0.1-alpha) — one subagent session each, TDD, commit per task

DoD mapping: Phase A delivers PedalMate.md DoD items 1–5. Items 6–7 (centered board) are met at B9/B15.

| # | Task | Files / scope | Tests | Acceptance |
|---|---|---|---|---|
| A0 | Dev env + docs | `mise.toml` + `mise trust`, `.gitignore`, spec + plan docs; `sdkmanager "platforms;android-34" "build-tools;34.0.0" "system-images;android-29;google_apis;x86_64"`; wrapper bootstrap `mise exec java@17 gradle@8.9 -- gradle wrapper --gradle-version 8.9`; AVD `pm29` 1920x1080@160dpi landscape | — | `mise exec -- java -version` = 17; AVD exists; docs committed |
| A1 | Gradle scaffold | all scaffold files, stub SetupActivity | 1 trivial JVM test | `testMockDebugUnitTest assembleMockDebug assembleRealDebug` green; mock APK launches on emulator |
| A2 | Sensor import (real) | copy AIDL verbatim + `BikeData.kt`/`V3BikeData.kt`; adapt BikeMetrics (drop speed), ConnectionState, BikeDataSource, PelotonBikeInterfaceDataSource, BoundBikeDataSource, PollableBikeDataSource, BikeDataMapping, ServiceRebinder; BikeSourceSupervisor: if no push within 5s, poll at 1 Hz; if no frame for 3s → `Disconnected`; injected clock and scheduler; `src/real` SensorFactory; NOTICE + headers | mapping (÷100, clamp), rebinder backoff w/ injected handler; BikeData parcel round-trip (port OpenRide `BikeDataParcelTest`, Robolectric); supervisor: silent→poll, frames resume→stop poll, 3s gap→Disconnected | `assembleRealDebug` compiles; AIDL package unchanged |
| A3 | Mock source + debug screen | MockBikeDataSource + ScriptedProfile (Z1..Z7 ramp, noise, dropout); `src/mock` SensorFactory; `SensorDebugScreen` (POWER/CADENCE/RESISTANCE per PedalMate.md) | deterministic profile by seed; state transitions | emulator shows moving numbers. **First on-bike milestone (stage 1 checklist)** |
| A3V | Stage-1 on-bike check (user-run) | user runs Stage 1 checklist; record in `docs/verification/phase-a.md` | — | Stage 1 passes |
| A4 | Zone maths | PowerZone, ZoneTable, FtpCalculator, PowerSmoother, TargetStatus | exact boundaries; null/zero FTP; FTP 200 table above exactly; for FTP ∈ {1,150,200,233,248,600} every p in 0..3·ftp has zoneFor(p) == the table row containing p (no gap or overlap); smoother window; hysteresis | all green |
| A5 | Workout JSON + presets | WorkoutDefinition/Step, WorkoutRepository w/ injectable AssetReader; presets `intro-20`, `endurance-z2-45`, `pz-43` (doc example, 43:00), `ftp-test` (tagged 20-min window; playable as a workout in Phase A) | round-trip; validation; every preset parses w/ expected duration (`pz-43` = 43:00) | schema documented in spec |
| A6 | WorkoutEngine | engine, state, events | sequencing; countdown 3/2/1 (skip <4s steps); pause/resume; skip last → Finished; large delta emits every IntervalChanged in order; progress monotonic | pure JVM, no Android imports |
| A7 | Audio cues | CuePolicy (pure, harder/easier next zone → different tone), CuePlayer | policy mapping | cue audible while another app foreground (emulator) |
| A8 | Persistence | Room entities/DAOs/AppDatabase, SettingsStore, RideRecorder, LiveAggregates | aggregates; recorder w/ fake DAO + fake clock (1 Hz, flush, finish); unfinished ride rows are finalised (endedAt = last sample) on app start | 60s mock ride → ~60 samples + finished row |
| A9 | BLE heart rate | copy/adapt parser, scanner, BleHeartRateDataSource, BlePermissions; MockHeartRateDataSource; HeartRateConnector: saves {address, name}. On start and after any disconnect it tries the saved address first, then scans with the 0x180D filter and matches the saved name, falling back to the first HR device. Retry backoff is 2s→30s and runs while the ride is active. bpm goes to null after 5s without a frame (iOS relays rotate random private addresses, so a saved MAC goes stale) | parser (uint8/16, contact, energy, RR, truncated throws); connector state machine with fake scanner/gatt: address hit; address miss → name match; disconnect → rescan; stale bpm → null | pairing UI wired in A14; on-bike: iPhone relay discovered |
| A10 | RideSession + RideService | RideSnapshot, RideSession (commands startWorkout/startFreeRide/pause/resume/skip/stop), RideService (notification channel, typed `startForeground`, ticker, stop action; START_STICKY restart with null intent and no live session → `stopSelf()`), AppContainer | snapshot combine: zone from smoothed power, target range, status, finish finalises ride | mock: start `pz-43` → snapshot advances, cues fire; service survives Activity close |
| A11 | Overlay window host | OverlayController, ComposeOverlayHost: TYPE_APPLICATION_OVERLAY; FLAG_NOT_FOCUSABLE (implies NOT_TOUCH_MODAL); PixelFormat.TRANSLUCENT; WRAP_CONTENT; gravity TOP\|START. Owner init: `savedStateController.performRestore(null)` → lifecycle CREATED → `setViewTreeLifecycleOwner/ViewModelStoreOwner/SavedStateRegistryOwner` on the ComposeView → `addView` → RESUMED. On remove: DESTROYED, then `viewModelStore.clear()`. Guard `addView` with `canDrawOverlays` and catch `BadTokenException`. No Dialog/Popup/DropdownMenu in overlay content. `FLAG_KEEP_SCREEN_ON` while the ride is Running. Drag | `clampToScreen`, snap-to-edge maths | draggable box floats over Settings/Chrome; outside touches pass through |
| A12 | Overlay UI | OverlayContent expanded (right-docked column: zone chip, watts, target range + below/in/above, interval timer/name, next, HR, cadence, resistance, progress bar) + minimized pill (zone + watts); tap toggles; position/mode persist | OverlayUiModel formatting (mm:ss, "253–296 W", "Z7 ≥ 300 W", dashes when disconnected) | live mock data; dropout → "no sensor", not stale |
| A13 | Interval toast | second window, FLAG_NOT_TOUCHABLE, centered, PedalMate.md layout, auto-dismiss 4s, no stacking | ToastModel + dismiss timer | shows on IntervalChanged, self-dismisses, never swallows a move |
| A14 | Setup activity | FTP entry (50..600) + zone preview; workout picker + free ride; HR pairing (permission, scan, save); overlay permission status/Grant; Start/Stop; "Launch Lichess" via `getLaunchIntentForPackage` (package a setting, default `org.lichess.mobileV2` (current Flutter app), fallback `org.lichess.mobileapp`, verify on bike). Largest task: split HR pairing UI out as A14H if the implementer exceeds one session | ViewModel validation | Start → RideService + overlay + Lichess (or fallback toast) |
| A15 | Permission onboarding | PermissionHelper: `canDrawOverlays` → `ACTION_MANAGE_OVERLAY_PERMISSION`; if unresolvable show adb command; location same w/ `pm grant` fallback; also checks `LocationManager.isLocationEnabled` (fallback `adb shell settings put secure location_mode 3`); service never crashes on denial | decision logic | both paths reachable on emulator (`appops set … deny`) |
| A16 | On-bike verification (user-run) | user runs stage 1+2 checklist; record pass/fail + logcat in `docs/verification/phase-a.md` | — | **hard gate before B6+** |

## Phase B tasks (native chess v0.2)

| # | Task | Scope | Tests | Acceptance |
|---|---|---|---|---|
| B1 | OAuth PKCE + token store | LichessAuth, PkceGenerator, TokenStore (EncryptedSharedPreferences, plain-DataStore fallback), OAuthRedirectActivity. `https://lichess.org/oauth` S256, `client_id=pedalmate`, `redirect_uri=pedalmate://oauth`, scope `board:play challenge:write`; exchange `POST /api/token`. Random `state`, verified on redirect (mismatch → error); handle `error=access_denied`. Browser fallback chain: Custom Tabs → in-app WebView → paste personal token | RFC 7636 vector; URL builder; token parse; MockWebServer exchange; state mismatch; denied | login on emulator; token survives restart |
| B2 | HTTP + NDJSON | NdjsonReader (blank = keepalive), LichessEvent sealed, LichessApi, one app-scoped event stream (a second open closes the first server-side), collector w/ backoff (60s on 429), long read timeout, `ignoreUnknownKeys` | chunked NDJSON, split lines, keepalives, 429, unknown type | event stream connects + logs |
| B3 | Game stream + GameSession | GameEvent (GameFull, GameState, ChatLine, OpponentGone), reducer → GameUiState (FEN, last move, legal moves, clocks, status, my color, draw offer); optimistic + reconcile | NDJSON fixtures hand-built from lichess-org/api schema examples (subagents cannot record live games): full game, resign, draw offers, flag, abort, opponent gone; idempotent dup/out-of-order | pure JVM |
| B4 | ChessPosition | chesslib wrapper: fromMoves, fen, legalMovesFrom, isPromotion, toUci, lastMove, check/game-over | castling UCI as Lichess sends, en passant, promotion, pins, full-game replay → FEN | chesslib types don't leak |
| B5 | Board component | ChessBoard composable (Canvas, pieces, last-move/check highlights, legal dots, flip for Black); permissive piece set (cburnett BSD), listed in THIRD_PARTY_NOTICES | `squareAt(px,size,flipped)`, orientation | crisp at ~800px |
| B6 | Centered ride screen | `BoardLayout.compute(w,h)` pure; RideScreen: top HUD, bottom progress, board+clocks, control row; symmetric side columns; Activity `keepScreenOn` during the ride | board centre x == w/2, square, maximised; 1920x1080 + others | visually centered w/ mock telemetry |
| B7 | Move input | tap-tap / drag, big promotion picker, `GameSession.tryMove`, `LichessApi.move`; optimistic w/ rollback; disabled off-turn; 429 retry state | legal accepted, illegal rejected locally, server rejection rolls back | full AI game |
| B8 | Clocks | ClockTracker pure: wtime/btime/inc + receive ts; ticks side-to-move; resync per gameState; latency comp | increments, resync, stop at end, low-time format | within ~1s of Lichess |
| B9 | Game creation | NewGameSheet: default AI level N 5+3 casual (`clock.limit=300&clock.increment=3`); direct/open challenge; rapid seek via `/api/board/seek` held-open stream (cancel = close), seek form `time` (minutes) and `increment` (s), not `clock.*`. Speed = `limitSec + 40·incSec`: <480 blitz (**seek rejected with message**), <1500 rapid, otherwise classical. Open the event stream before the seek; gameStart event opens game stream | form builders; seek validation: 5+3=420 rejected, 8+0=480 ok, 10+5=800 ok; MockWebServer seek | AI game starts; rapid seek matches |
| B10 | Resign / draw / abort | confirm dialogs, large targets; abort only early | enable/disable by status + ply | each works live |
| B11 | Game end + rematch | result banner (win/loss/draw + reason), New game / Rematch; flag + abandonment | status/winner mapping | all B3 fixtures |
| B12 | Ride↔game association | GameRecorder: GameEntity(rideId, lichessId, color, opponent, speed, startedAt, result, avg power/zone in window) | window aggregate; tally | ride w/ 2 games shows both |
| B13 | FTP test mode | `RideSession.startFtpTest`; FtpTestAnalyzer pure (20-min window avg ×0.95, reject incomplete/>5s gaps); FtpTestScreen no chess, big watts, countdown, confirm-save → settings + zones | constant, ramp, gap, short | debug 60x clock run saves FTP |
| B14 | Summary + history | SummaryScreen/HistoryScreen per PedalMate.md layout (duration, avg/peak power, cadence, resistance, FTP, time-in-zone bars, chess tally) | summary-model builder incl. empty ride | matches doc layout |
| B15 | Ride mode integration | mode selector (Chess+PZ, Free Ride+Chess, FTP Test); Activity attaches to RideService session; overlay auto-hide; Ride Mode lock; Activity `keepScreenOn` during the ride | mode→config mapping | switch Lichess-app↔native keeps same workout running |
| B16 | On-bike verification B (user-run) | user runs stage 3 checklist | — | — |

## Beads tree

`bd init` in repo, then bulk-create from a markdown file (`bd create -f docs/beads/issues.md`) or scripted with `--silent` IDs. Epics: **E0 Foundation** (A0, A1), **EA Phase A** (A2–A16, A3V), **EB Phase B** (B1–B16). Dependencies (`--deps`):

| Issue | Deps | | Issue | Deps |
|---|---|---|---|---|
| A1 | A0 | | B1 | A1 |
| A2 | A1 | | B2 | B1 |
| A3 | A1, A2 | | B3 | B2 |
| A4 | A1 | | B4 | A1 |
| A5 | A4 | | B5 | B4 |
| A6 | A4, A5 | | B6 | B5, A10, **A16** |
| A7 | A6 | | B7 | B3, B4, B6 |
| A8 | A1, A2, A4 | | B8 | B3 |
| A9 | A1, A2 | | B9 | B2, B3, B6 |
| A10 | A2, A3, A6, A7, A8, A9 | | B10 | B7 |
| A11 | A1 | | B11 | B7, B10 |
| A12 | A10, A11 | | B12 | B3, A8 |
| A13 | A12 | | B13 | A6, A8 |
| A14 | A10, A9, A12 | | B14 | A8, B12 |
| A15 | A11, A14 | | B15 | B6, B13, A12 |
| A16 | A13, A15, A3V | | B16 | B11, B14, B15 |
| A3V | A3 | | | |

During Phase A pick only `bd ready --parent <E0|EA>`. Phase B beads are not scheduled until the Phase B plan exists (after A16). Run `bd dep cycles` after creation.

## Verification

**Unit:** `mise exec -- ./gradlew testMockDebugUnitTest`. Pure-JVM coverage required: zones/smoother/status, FTP calc + analyzer, workout JSON + engine, CuePolicy, HR parser, NDJSON/event/game parsing, GameSession, ClockTracker, ChessPosition, PKCE, BoardLayout, overlay clamp.

**Emulator:**
```bash
sdkmanager "platforms;android-34" "build-tools;34.0.0" "system-images;android-29;google_apis;x86_64"
avdmanager create avd -n pm29 -k "system-images;android-29;google_apis;x86_64" -d pixel_c
# ~/.android/avd/pm29.avd/config.ini: hw.lcd.width=1920 hw.lcd.height=1080 hw.lcd.density=160
emulator -avd pm29 &
mise exec -- ./gradlew installMockDebug
adb shell appops set dev.pedalmate.mock SYSTEM_ALERT_WINDOW allow
adb shell pm grant dev.pedalmate.mock android.permission.ACCESS_FINE_LOCATION
adb logcat -s PedalMate:V PelotonBikeIface:V BleHeartRate:V
```

**On-bike (after OpenPelo wireless ADB pairing):**
- Stage 1 (after A3): `getprop ro.product.board` = topaz; record `wm size`, `wm density`, `ro.build.version.sdk`; `installRealDebug`; launch from Peloton custom launcher (DoD 1); debug screen; logcat shows bind + frame count; pedal → ~20 Hz plausible values vs stock UI; stop → zeros; `am force-stop com.onepeloton.affernetservice` → rebinder recovers ≤30s.
- Stage 2 (A16): `appops set dev.pedalmate SYSTEM_ALERT_WINDOW allow`; `pm grant … ACCESS_FINE_LOCATION`; `pm list packages | grep lichess` → set package; set FTP, start `pz-43`, Lichess launches; overlay over board w/o blocking moves; record whether the right column covers Lichess's clocks or resign button; drag + toggle; cues audible, toast appears/dismisses; `dumpsys window windows | grep -i pedalmate`; `dumpsys activity services dev.pedalmate`; 20+ min ride no crash/kill, `dumpsys meminfo` flat; 10 min no touch: screen stays on, no Peloton activity takes over; HR via iPhone relay incl. reconnect after phone sleeps; pull DB via `run-as`.
- Stage 3 (B16): OAuth path works on bike; 5+3 AI game during workout; board centre = w/2 on `screencap`; resign/draw; games linked in summary.

## Risks (top)
- Peloton firmware changes Affernet → isolated sensor seam, AIDL verbatim, poll fallback, frame-count logging, clear "no sensor" state.
- Peloton Android lacks permission UIs → every permission has adb fallback surfaced in-app (A15).
- Compose-in-WindowManager lifecycle → custom owners (A11), remove view in onDestroy, Grupetto as reference only.
- Overlay swallows chess touches → NOT_FOCUSABLE (implies NOT_TOUCH_MODAL) panel docked right, NOT_TOUCHABLE toast.
- FGS killed under memory pressure → START_STICKY, 5s flushes, unfinished rides finalised on start.
- Display timeout brings up Peloton's own activity → FLAG_KEEP_SCREEN_ON / `keepScreenOn` while riding.
- iPhone HR relay rotates BLE address → name-match rescan with backoff (A9).
- Lichess: blitz seek impossible → AI default; 429 backoff 60s; single shared event stream; no blind move retries.
- No browser on bike → 3-level OAuth fallback (B1).
- Keystore flaky → plain DataStore token fallback.
- Licensing: OpenRide Apache-2 w/ NOTICE; no chessground assets (GPL); no Grupetto code; no "Peloton"/"Lichess" in app name/package.

## Model roles

| Activity | Model | How invoked | Why |
|---|---|---|---|
| Orchestration: pick next bead, dispatch implementer, dispatch reviewer, relay verdict, close bead, decide on blockers | **Main session** | main session | Holds full plan context; does no reading of code or diffs beyond reviewer summaries |
| Writing spec doc + Phase A/B plan docs (`writing-plans`) | **Sonnet** | `Agent model: "sonnet"` | Mechanical expansion of this plan into task files |
| Creating beads epics/issues from the tree | **Haiku** | `Agent model: "haiku"` | Scripted `bd create` calls, no judgment |
| Implementing each task A0–A16, B1–B16 (TDD, commit) | **Sonnet** | `Agent model: "sonnet"`, one agent per task, fresh context | Bulk of tokens; cheapest model that reliably writes Kotlin/Compose + tests |
| Reviewing each task: spec compliance, code quality, runs `./gradlew test…`, checks NOTICE/headers, fair-play check | **Opus** | `Agent model: "opus"` after each implementer | Stronger judgment than implementer, cheaper than main session |
| Fixing review findings | **Sonnet** | `SendMessage` back to same implementer agent | Keeps its context |
| Copying/adapting OpenRide code (A2, A9) | **Sonnet** | implementer | Transcription + rename; Opus reviewer checks AIDL verbatim + txn order |
| On-bike verification (A3V, A16, B16) | **User** + **Opus** on request | User runs the checklist; an Opus subagent diagnoses logcat/dumpsys only on request | Subagents have no bike access |
| Debugging a failed task after 2 Sonnet attempts | **Opus** | escalate via `systematic-debugging` | Avoid Sonnet looping |
| Final Phase A / Phase B acceptance review of whole codebase | **Opus** | `code-review` skill, high effort | Main session reads summary only |
| Memory saves, plan edits, user Q&A | **Main session** | main session | Session-level state |

Escalation ladder: Sonnet → Opus → main session. Main session never reads source files during execution unless Opus explicitly asks.

## Execution notes (on plan approval)
1. Save memories: Sonnet implements, Opus verifies (`Agent model: "opus"`), main session orchestrates only; beads for tasks; Bike+/Android 10/OpenPelo facts.
2. Write spec to `docs/superpowers/specs/2026-10-04-pedalmate-design.md` (this plan's Context + Architecture), commit.
3. `bd init` + create epics/issues per tree above.
4. Invoke `superpowers:writing-plans` for Phase A → `docs/superpowers/plans/2026-10-04-phase-a.md`, then `superpowers:subagent-driven-development`: **Sonnet** implementer per task → **Opus** reviewer per task (spec compliance + code quality + runs tests) → main session relays verdict, closes bead.
5. Phase B plan written after A16 passes on the bike.

## Workout JSON schema (added in A5)

Workouts are JSON files under `app/src/main/assets/workouts/`; the file name without `.json` equals the `id`. Example, `pz-43.json`:

```json
{
  "id": "pz-43",
  "name": "Power Zone 43",
  "description": "Warmup 10:00, Z3/Z4/Z3/Z4 blocks, Z5 3:00, Z2 5:00, cooldown 5:00.",
  "steps": [
    { "label": "Warmup",   "seconds": 600, "zone": 2 },
    { "label": "Zone 3",   "seconds": 300, "zone": 3 },
    { "label": "Zone 4",   "seconds": 300, "zone": 4 },
    { "label": "Zone 3",   "seconds": 300, "zone": 3 },
    { "label": "Zone 4",   "seconds": 300, "zone": 4 },
    { "label": "Zone 5",   "seconds": 180, "zone": 5 },
    { "label": "Zone 2",   "seconds": 300, "zone": 2 },
    { "label": "Cooldown", "seconds": 300, "zone": 1 }
  ]
}
```

| Field | Type | Required | Notes |
|---|---|---|---|
| `id` | string | yes | `^[a-z0-9][a-z0-9-]{0,39}$` |
| `name` | string | yes | non-blank, at most 60 chars |
| `description` | string | no | free text |
| `steps` | array | yes | 1..200 entries |
| `steps[].label` | string | yes | non-blank, at most 40 chars |
| `steps[].seconds` | int | yes | 1..21600 |
| `steps[].zone` | int | no | omitted/null (no target) or 1..7 |
| `steps[].tag` | string | no | non-blank, at most 20 chars; `"ftp-window"` marks the 20-minute FTP test window (used in Phase B) |

Validation: unknown JSON keys are ignored; missing required keys are an error; all violations are collected and reported together. Zone is explicit per step; null zone means no power target. Zone is never inferred from the label.
