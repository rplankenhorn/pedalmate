# HR ride: resistance follows a heart-rate target

Design, 2026-10-26. Companion to `2026-10-04-pedalmate-design.md` (Phase A overlay). Status: approved 2026-10-26 (brainstorm + builder check; two user decisions recorded inline).

## Intent

The rider plays Lichess on the Bike+ screen while riding. Holding a steady heart rate (130–140 bpm) today means turning the resistance knob by hand every minute or two, which breaks chess concentration. A new ride type, **HR ride**, has PedalMate nudge the Bike+ motorised resistance so heart rate stays inside the target band, and the rider only thinks about chess.

The ride the user described: a few minutes of easy warm-up, then roughly 30 minutes with the bike holding HR in the band, then a short cooldown. About 40 minutes total.

## Goals

- During the target block, HR stays inside the band without the rider touching the knob, for an engaged rider pedalling at a roughly steady cadence.
- Resistance moves in small, infrequent steps. The rider should barely notice individual changes.
- The rider can always override by turning the knob, and the controller does not fight the override.
- The feature reuses the existing workout engine, overlay, cues, ride log and Setup flow. An HR ride is a workout preset, not a parallel system.

## Non-goals

- Tight HR tracking. ±5 bpm around the band edge is fine. This is not an ergometer.
- Power-zone auto-follow (resistance from a watt target). Different feature, same write path; not built here.
- Editing warm-up/cooldown resistance or durations from the UI. The preset JSON holds those; the target band is the only user-editable value.
- Any Phase B (native chess) work.

## Anti-goals (what would make this a failure even if it "works")

- **Resistance changes while the rider has stopped pedalling.** Cadence ~0 means hold, no exceptions. (User's words: "if I did stop, resistance shouldn't change".)
- **Resistance changes without a readable heart rate.** Strap dropped, stale frame, HR source not connected: hold. (User: "we shouldn't make changes if heart rate isn't readable".)
- **Fighting the knob.** If the rider turns the knob, the controller backs off for a grace period and then resumes from where the rider left it. (User: "I want to be able to override the resistance with me turning the knob"; grace period approach confirmed.)
- **Runaway resistance.** A controller bug must not be able to crank resistance to 100 or drop it to 0. Hard clamps and rate limits sit outside the control law.
- **Audible chatter.** No tone or toast per resistance step. Cues stay as today (interval changes only).

## Constraints and facts

- Bike+ (`topaz`, Android 10). Resistance is a stepper-motor knob; the Peloton app's own Power Zone auto-follow drives it, so the hardware path exists.
- `app/src/main/aidl/com/onepeloton/affernetservice/IBikeInterface.aidl` already declares `void setResistance(int resistance)` (transaction 7) and `int getTargetResistance()` (transaction 6), verified against the on-device dex. PedalMate has never called either. Whether the service accepts `setResistance` from a third-party binder client, what units it takes (assumed the same 0–100 as `mCurrentResistance`), how fast the motor settles, and whether `getTargetResistance` reflects the commanded value are all **unverified**. The spike below settles them before anything else is built.
- `PedalMate.md` line 90 says the sensor integration stays read-only. This feature deliberately reverses that for one call. Update that paragraph as part of the work: read-only remains the default, `setResistance` is the single sanctioned write, isolated behind an interface.
- Heart rate arrives from HeartCast (Apple Watch relay) over BLE in bursts; field logs (2026-10-16, BlueHeart relay) show gaps up to 40 s between bursts, and DEAD_MS was raised to 45 s for that reason. `HeartRateConnector` nulls `bpm` after 5 s of silence (STALE_MS). A strict "null means hold" rule would therefore hold between every burst and the controller might never act. **Decision (user-approved 2026-10-26): the controller treats the last heart-rate reading as readable for up to `HR_MAX_AGE_MS = 30_000` after the frame that carried it, then holds.** The snapshot therefore carries `heartRateAtMs` (elapsed-realtime of the last frame) alongside `heartRateBpm`, and the controller input takes `hrBpm` + `hrAgeMs`. The overlay keeps showing "--" after 5 s exactly as today; only the controller uses the longer window.
- HR responds to a load change with a lag on the order of 30–60 s. The controller must be slow: one small step, then wait.
- Bike metrics arrive at ~21 Hz; `RideSession.tick` runs every 250 ms on the service ticker. The controller is driven from that tick like the engine, with no wall clock of its own.
- Unit tests are pure JVM (`testMockDebugUnitTest`); on-bike verification is user-run. The mock flavour must exercise the full loop on the emulator.

## Shape

### 0. Spike (throwaway, needs the bike on adb)

A debug-only button on `SensorDebugScreen` ("RES +5" / "RES −5") that calls `IBikeInterface.setResistance(current ± 5)` and logs: return/exception, `getTargetResistance()` before and after, and `mCurrentResistance` from the next ~5 s of frames with timestamps. The rider also turns the knob by hand during that window to see what `getTargetResistance` and `mCurrentResistance` do. Outcome recorded in the bead; nothing from the spike is kept. If `setResistance` is rejected or does nothing, this design stops here and the bead records why.

Questions the spike answers: accepted at all; units; settle time (sets `SETTLE_MS`); whether the service clamps; what a manual knob turn looks like in the frame stream.

### 1. Write path

- New interface `BikeResistanceControl { fun setResistance(percent: Int): Boolean }` in `sensor/`. `Boolean` = call delivered (not that the motor arrived).
- `PelotonBikeInterfaceDataSource` implements it: binder call 7 on the Default dispatcher (blocking binder, same rule as `pollBikeData`). Returns false when unbound or on `RemoteException`, logs at W once per minute at most.
- `BikeSourceSupervisor` forwards to the wrapped source.
- `MockBikeDataSource` implements it: the mock gets a resistance state and a cheap physics model (power ≈ k · resistance · cadence, k picked so resistance 40 at 80 rpm ≈ 150 W). `ScriptedProfile` keeps driving cadence and dropouts; resistance becomes mutable state. `MockHeartRateDataSource` gains an optional physiology mode: HR tracks `60 + 0.45 · power` through a first-order lag (τ ≈ 40 s). Mock flavour wires the two together so the emulator shows the loop closing. Builder's call on exact constants; the point is a believable emulator demo, not fidelity.
- `SensorHub` exposes `resistance: BikeResistanceControl?` (null when the bound source cannot write). `RideSession` treats null as "controller permanently in HOLD".
- `PedalMate.md` read-only paragraph updated as described above.

### 2. Workout schema

`WorkoutStep` gains two optional fields:

| Field | Type | Meaning |
|---|---|---|
| `hrTarget` | boolean | step is a heart-rate target step; the band comes from Settings (see 4) |
| `resistance` | int 0–100 | set this resistance once when the step starts; controller inactive for the step |

Validation: a step has at most one of `zone`, `hrTarget`, `resistance`. `resistance` in 0..100. Unknown keys still ignored.

New preset `app/src/main/assets/workouts/hr-40.json` ("HR 40"):

```json
{
  "id": "hr-40",
  "name": "HR 40",
  "description": "Easy warm-up 5:00, hold the HR target 30:00, cooldown 5:00. Resistance follows heart rate.",
  "steps": [
    { "label": "Warm-up",    "seconds": 300,  "resistance": 30 },
    { "label": "HR target",  "seconds": 1800, "hrTarget": true },
    { "label": "Cooldown",   "seconds": 300,  "resistance": 25 }
  ]
}
```

Warm-up/cooldown resistance values 30/25 are my guess; the rider tunes the JSON. `StepInfo` carries `hrTarget: Boolean` and `resistance: Int?` so the session and overlay can see them.

### 3. Controller

`HrResistanceController` in `workout/`, pure Kotlin, no clock, no Android. Fed from `RideSession.tick` with a small input struct:

```
Input(nowMs, hrBpm: Int?, hrAgeMs: Long?, cadenceRpm: Int?, observedResistance: Int?, band: IntRange, step: StepRef, running: Boolean)
  // hrBpm is the last reading (not nulled by the 5 s connector staleness); hrAgeMs is nowMs minus the frame time.
  // step = index + the step's hrTarget/resistance so the controller can detect step entry itself.
Output = SetResistance(percent) | None
State (exposed): mode ∈ { AUTO, HOLD_NO_HR, HOLD_NO_CADENCE, HOLD_NO_BIKE, MANUAL(graceRemainingMs), PAUSED }
```

Control law (deliberately dumb):

- Evaluate at most once per `ADJUST_INTERVAL_MS = 20_000` and only after the previous command has settled (`SETTLE_MS`, from the spike, default 3 000).
- Error = distance from the band: 0 inside; `min − hr` below; `hr − max` above.
- Inside band → no change. Below → +1, or +2 when error ≥ 10 bpm. Above → −1, or −2 when error ≥ 10 bpm.
- Clamp the commanded value to `[AUTO_MIN = 10, AUTO_MAX = 70]`. These are safety rails, not tuning; they sit outside the law and are unit-tested as such.
- Hold conditions, checked before anything else, in this order: not running (paused/finished/not an hrTarget step) → PAUSED; `observedResistance == null` (bike not connected) → HOLD_NO_BIKE; `hrBpm == null || hrAgeMs > HR_MAX_AGE_MS` → HOLD_NO_HR; `cadenceRpm < CADENCE_FLOOR = 30` → HOLD_NO_CADENCE. Holding never emits a command. A hold that lasts `HOLD_RESET_MS = 10_000` or longer resets the adjust timer, so after a real stop (rider stopped pedalling, strap gone) the first adjustment waits a full interval; a shorter hold (a two-second coast, one sub-floor cadence sample, a burst gap) just blocks commands while it lasts and leaves the timer alone. My call, from the reviewer's point that per-sample holds would starve the controller during chess.
- Manual override: after the settle window, if observed resistance differs from the last commanded value by ≥ `OVERRIDE_DELTA = 2`, enter MANUAL for `GRACE_MS = 75_000` from the most recent observed change. Any further knob movement during grace restarts the grace. On exit, the current observed resistance becomes the new baseline and the adjust timer restarts. Before the first command of a step, the baseline is whatever the bike reports. A `setResistance` call that returns false (unbound, RemoteException) is not a command: it does not move the baseline and does not start the settle window; the controller simply tries again at the next evaluation. My call.
- Step entry: a `resistance` step sends exactly one `SetResistance` on entry (subject to the HOLD_NO_BIKE check only; this is the one case where a command goes out without HR and cadence, because the rider chose it in the preset). The step's value passes through unclamped by the AUTO rails. If the bike is not connected at entry, or the call returns false, the command stays **pending** and is sent at the first later tick where the bike is connected, until the step ends; then it is dropped. My call: step 0 enters inside `RideSession.begin()` before the first bike frame has necessarily arrived, so a fire-and-forget entry command would often be lost. An `hrTarget` step sends nothing on entry.
- Ride stop/finish: no command. Resistance stays where it is.

Grace length, interval and step sizes are my guesses from the 30–60 s HR lag; builder may tune from emulator runs but must keep: at most one change per 20 s, at most 2 units per change, hard clamps, and the hold rules verbatim.

Tests (pure JVM, scripted ticks): inside band → no command; below → +1 then nothing for 20 s; large error → +2; clamps at 10/70; each hold reason suppresses commands; a hold ≥ 10 s resets the timer, a 3 s hold does not; HR older than 30 s holds, 25 s does not; manual override detected only after settle; grace restarts on further movement; resume uses the knob position as baseline; false return leaves the baseline alone; `resistance` step emits once, and once-later when the bike connects mid-step, never after the step; live band change applies at the next evaluation without restarting the interval; pause → no command; stop → no command.

### 4. Settings and Setup

- `Settings` gains `hrTargetMin`, `hrTargetMax` (defaults 130, 140). Validation 60..200, `max − min ≥ 5`.
- **The band is read live (user-approved 2026-10-26), not latched at ride start.** `RideSession` reads the current band each tick (same provider pattern as `ftpProvider`, but sampled per tick rather than in `begin()`); the controller input carries it; the overlay target text follows it immediately. A band change does not restart the adjust interval and does not reset the HR TargetStatus debounce. The HR target row in Setup stays editable during a ride (unlike the workout picker).
- Setup screen: an "HR target" row next to FTP with two number fields (min/max) and a one-line explanation. Visible always (cheap) or only when the selected workout has an hrTarget step (builder's call; always-visible is fine).
- The workout picker lists "HR 40" like any other preset. START RIDE works as today. If the HR source is not connected at start, Setup shows a non-blocking message ("No heart rate: resistance will hold until the strap connects") and starts anyway.

### 5. Session, overlay, cues, log

- `RideSession` owns the controller, builds its input from the snapshot each tick, and dispatches `SetResistance` to `hub.resistance` on the session scope (never on the main thread). Snapshot gains `hrTarget: IntRange?` (non-null during an hrTarget step) and `resistanceMode: ResistanceMode?` (the controller's exposed state, null outside HR rides).
- `TargetStatus` for an hrTarget step is computed from HR against the band (BELOW/IN/ABOVE with the existing 2 s debounce), not from power.
- Overlay, hrTarget step: `targetText` = "HR 130–140", status line as today from the HR-based status; watts stay where they are (the rider still likes seeing them); a short mode line replaces nothing and sits under the interval block: `AUTO`, `MANUAL 1:05`, `HOLD · no HR`, `HOLD · pedal`, `HOLD · no bike`. Zone chip shows the current power zone as today or `--` without FTP. `needsFtp` is not raised by an HR ride (FTP is optional for it). Builder may mock the strip in lavish-axi before committing to layout; not required.
- Cues: `CuePolicy` treats an hrTarget step as STEP_SAME for the harder/easier tone decision; everything else unchanged. No per-adjustment sound.
- Ride log: `SampleEntity` already stores resistance and HR each second. Add nothing. (A future summary can show how often the controller acted; out of scope.)

### 6. On-bike verification (user-run)

Added to the Stage 2 checklist in `docs/verification/phase-a.md`:

- Start HR 40; warm-up sets resistance 30 within a few seconds, no further change for 5 min.
- Target block: HR enters 130–140 within ~5 min and stays within ±5 bpm of the band for 10 min while playing chess; logcat shows adjustments spaced ≥ 20 s, size ≤ 2.
- Stop pedalling 30 s: overlay `HOLD · pedal`, no change. Resume: adjustments resume after one interval.
- Close HeartCast: `HOLD · no HR`, no change. Reopen: resumes.
- Turn the knob +5 by hand: `MANUAL`, no command for the grace; after grace the controller continues from the new value.
- Cooldown sets 25; ride ends; Done panel as today.

## Left to the builder

- Exact constants beyond the stated invariants (tune on the emulator physiology model).
- Overlay mode-line typography and placement within the existing strip.
- Whether the HR target row in Setup is always visible (it must be visible, and editable, during an HR ride).
- Mock physics/physiology constants.
- Test file layout; follow the existing Robolectric/JVM split.
- Generalising `TargetStatusTracker` (watt-typed today) to an HR band; how `SensorFactory` (mock) wires mock bike power into the mock HR physiology; the throwaway accessor the spike needs on `PelotonBikeInterfaceDataSource`'s private proxy.

## Ordering

1. Spike (gated on the bike being attached; everything after this is gated on the spike passing).
2. Write path + mock physics (1).
3. Controller + tests (3). Can run in parallel with 2 once the interface exists.
4. Schema + preset + settings (2, 4).
5. Session/overlay/cues integration (5).
6. On-bike verification (6).

Tracked in beads under a new epic; each numbered part is one task with Sonnet implementing in a worktree and Fable reviewing, per the project's standing model-role rule.
