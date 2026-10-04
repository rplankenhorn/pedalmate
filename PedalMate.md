# PedalMate

**An Android cycling dashboard combining Power Zone training with online chess.**

## Project Concept

PedalMate is a custom Android application designed to run directly on a Peloton tablet. The goal is to combine Peloton cycling telemetry and Power Zone workouts with a Peloton-optimized Lichess chess experience.

The Peloton already has a custom launcher installed that can launch sideloaded Android applications, so PedalMate does not need to solve launcher or navigation restrictions. It can be developed and installed as a normal sideloaded Android APK.

The primary use case is simple: **ride a structured Power Zone workout while playing chess on Lichess, without switching between applications.**

The chess board should remain the primary interactive element on screen. Cycling information should complement it rather than force the board to one side.

---

## Core Goals

PedalMate should:

- Read live cycling telemetry directly from the Peloton hardware.
- Display power, cadence, and resistance in real time.
- Calculate and display the rider's current Power Zone.
- Support FTP testing and automatically calculate FTP and Power Zones.
- Run structured Power Zone workouts with timed intervals.
- Integrate with Lichess so real online games can be played while riding.
- Use a **centered chess board**, rather than the left-aligned layout of the current Lichess app.
- Provide large, touch-friendly controls appropriate for use while riding.
- Keep cycling telemetry read-only; there is no need for PedalMate to control the bike hardware.
- Record workout statistics locally for post-ride review.

---

## Target Platform

PedalMate will be a native Android application intended primarily for a Peloton tablet.

A likely technology stack is:

- Kotlin
- Jetpack Compose
- Android services / Binder for Peloton sensor access
- Lichess HTTP/Board API for online chess
- OAuth2/PKCE for Lichess authentication
- Local persistence for settings, workouts, FTP, and ride history

The application should be designed specifically around the Peloton tablet's screen dimensions, touch behavior, and landscape orientation rather than attempting to be a generic phone application first.

---

## Peloton Sensor Integration

The critical first technical milestone is proving that PedalMate can reliably access the Peloton's live sensors.

Existing open-source projects demonstrate that this is possible. In particular, OpenRide accesses Peloton telemetry by binding to Peloton's exported Android service:

`com.onepeloton.affernetservice.AffernetService`

Different Peloton hardware generations expose different interfaces. OpenRide documents interfaces including `IV1Interface` for certain original/Gen 2 bikes and `IBikeInterface` for Bike+.

PedalMate should abstract this behind its own sensor interface, for example:

```text
Peloton hardware
      ↓
AffernetService
      ↓
PelotonBikeDataSource
      ↓
WorkoutEngine
      ↓
Compose UI
```

The initial sensor prototype should do nothing more than show:

```text
POWER
  183 W

CADENCE
   87

RESISTANCE
   44
```

Once those three values update reliably on the actual bike, the major Peloton-specific technical risk has been addressed.

The sensor integration should remain **read-only**. PedalMate does not need to send resistance or other hardware-control commands to the bike.

Because these are undocumented/internal Peloton interfaces, firmware updates could potentially change or break the integration. The sensor layer should therefore be isolated so it can be updated without changing the rest of the application.

---

## Power Zone System

PedalMate should maintain the rider's FTP and calculate seven Power Zones from it.

The app should support both manually entering an FTP and determining it through an FTP test.

### FTP Test Mode

FTP Test mode should guide the rider through a complete test session, including:

- Warmup
- 20-minute FTP test
- Cooldown
- Continuous power recording
- 20-minute average power calculation
- Automatic FTP calculation
- Saving the resulting FTP and zones

The conventional 20-minute test calculation is:

`FTP = 20-minute average power × 0.95`

FTP Test mode should probably **not include chess**. The rider should be focused on pacing and effort during the test.

### Power Zone Workouts

PedalMate should allow structured workouts consisting of timed zone intervals, for example:

```text
Warmup       10:00
Zone 3        5:00
Zone 4        5:00
Zone 3        5:00
Zone 4        5:00
Zone 5        3:00
Zone 2        5:00
Cooldown      5:00
```

During a workout, the app should show:

- Current power
- Current Power Zone
- Target Power Zone
- Target watt range
- Cadence
- Resistance
- Current interval
- Time remaining in the interval
- Total workout time
- Next interval
- Overall workout progress

The UI should clearly indicate whether current output is **below, within, or above** the target zone.

Interval changes should have an audible cue so the rider does not need to constantly look away from the chess board.

A short visual notification can accompany the sound, such as:

```text
┌───────────────────────────┐
│       NEXT INTERVAL       │
│                           │
│          ZONE 5           │
│        253–296 W          │
│                           │
│          3:00             │
└───────────────────────────┘
```

The notification should disappear automatically after a few seconds.

---

## Riding Modes

PedalMate should eventually provide at least three main riding modes.

### Chess + Power Zone

This is the primary PedalMate experience. The rider selects a Power Zone workout and then plays chess while the workout engine runs independently.

### Free Ride + Chess

No structured intervals. The rider plays chess while PedalMate displays basic live metrics:

- Power
- Current zone
- Cadence
- Resistance
- Ride time

This may become one of the most frequently used modes because it requires essentially no setup.

### FTP Test

A focused FTP-testing interface without chess distractions.

---

## Lichess Integration

Lichess is a particularly good fit because it is open source and exposes APIs intended for third-party clients.

Rather than attempting to embed or modify the existing Lichess Android application, the preferred final architecture is to build a **PedalMate-native chess interface** and connect it to Lichess.

The Lichess Board API can provide game state and allow moves to be submitted, enabling PedalMate to function as a third-party Lichess client while still playing real games against Lichess users.

Authentication should use the supported Lichess OAuth2/PKCE flow rather than storing account passwords.

Useful Lichess/open-source components and references include:

- Lichess API
- Lichess Board API
- Current open-source Lichess mobile application
- Chessground board component
- chessops chess-rules library

A WebView-based Lichess implementation could be used as a rapid prototype, but it should not constrain the final design. A native board/API implementation provides complete control over layout and is preferred for the finished application.

### Fair Play

PedalMate must not provide engine assistance during live Lichess games.

Stockfish analysis, suggested moves, evaluations, opening assistance, or other outside chess assistance should not be shown during an active game. Cycling telemetry and workout guidance are unrelated to chess decision-making and can remain visible.

---

## Chess UI Design

The current Lichess application places the board toward the left side of the Peloton display. PedalMate should explicitly avoid this.

**The chess board should be mathematically centered on the physical display.**

The board is the primary interactive element and should receive as much vertical space as practical. Cycling information should live around the board rather than permanently pushing it to one side.

A possible layout:

```text
┌────────────────────────────────────────────────────────────┐
│ Z3     191 W      86 RPM      RES 44       31:24          │
│ TARGET 175–210 W                       Z3 • 6:42 remaining │
├────────────────────────────────────────────────────────────┤
│                                                            │
│                        5:43                                │
│                                                            │
│                 ┌────────────────────┐                     │
│                 │                    │                     │
│                 │                    │                     │
│                 │    CHESS BOARD     │                     │
│                 │                    │                     │
│                 │                    │                     │
│                 └────────────────────┘                     │
│                                                            │
│                        6:17                                │
│                                                            │
│      Draw        Resign       ⚙       Workout             │
├────────────────────────────────────────────────────────────┤
│ ████████████████████████░░░░░░░░░░░░░░░░░░░░░░░░░░░░░ │
│ Current Z3 • 6:42                         Next Z4 • 5:00    │
└────────────────────────────────────────────────────────────┘
```

### UI Priorities

During Chess + Power Zone rides:

1. Chess board
2. Chess clocks
3. Current Power Zone
4. Current watts
5. Current interval/time remaining
6. Cadence and resistance
7. Next interval
8. Overall workout progress

The UI should use large touch targets because the rider will be moving while interacting with the tablet.

A dedicated **Ride Mode** could hide unnecessary menus and controls to reduce accidental touches.

---

## Workout Recording

PedalMate should record workout data locally.

A post-ride summary could include:

```text
POWER ZONE RIDE
─────────────────────────

Duration             48:32

Average Power        187 W
Peak Power           421 W

Average Cadence       84
Average Resistance    43

FTP                   248 W

TIME IN ZONE

Z1   02:14  ██
Z2   12:48  ███████████
Z3   14:31  █████████████
Z4   11:03  █████████
Z5   05:42  █████
Z6   01:51  ██
Z7   00:23
```

If Lichess is integrated through the API, PedalMate could also associate chess games with a ride:

```text
Chess
─────────────────────────
Games                    4
Wins                     2
Losses                   1
Draws                    1
```

Potential fun statistics could eventually include chess performance versus workout intensity or Power Zone, although these should remain secondary to the core riding/chess experience.

---

## Application Architecture

A reasonable high-level architecture is:

```text
                    PEDALMATE
                        │
          ┌─────────────┴─────────────┐
          │                           │
      CHESS ENGINE               RIDE ENGINE
          │                           │
   Centered board                Power / Zone
   Chess clocks                  Cadence
   Game state                    Resistance
   Game controls                 Intervals
          │                           │
          │                    PelotonBikeDataSource
          │                           │
   Lichess Board API             AffernetService
          │                           │
       Lichess                  Peloton hardware
```

The application should keep these major components independent:

- Peloton sensor layer
- Power Zone / workout engine
- Lichess networking layer
- Chess game state/UI
- Ride recording/storage
- Application UI

This separation is especially important because the Peloton sensor interface is undocumented and could require maintenance after firmware updates.

---

## Development Strategy

### Phase 1 — Peloton Sensor Prototype

Build the smallest possible APK that:

- Launches from the existing custom launcher
- Binds to the appropriate Peloton sensor service
- Displays live power
- Displays live cadence
- Displays live resistance

Test it on the actual Peloton.

**Success criterion:** all three metrics update reliably during a ride.

### Phase 2 — Power Zone Engine

Add:

- FTP setting
- Seven-zone calculation
- Current-zone calculation from live power
- Workout interval model
- Workout timer
- Target watt ranges
- Audio interval cues
- Workout builder / saved workouts
- Ride data recording

### Phase 3 — FTP Test

Add the guided 20-minute FTP test and automatic FTP calculation/storage.

### Phase 4 — Chess Prototype

Initially, an embedded Lichess/WebView approach could validate screen layout and usability while riding.

Key question to answer: is playing chess comfortable with the board centered and cycling metrics around it?

### Phase 5 — Native Lichess Client

Replace the WebView with a PedalMate-native chess experience using the Lichess Board API.

Add:

- OAuth2/PKCE login
- Challenges/game creation as appropriate
- Live game-state streaming
- Move submission
- Centered native board
- Chess clocks
- Draw/resign controls
- Game result handling

### Phase 6 — Polish

Potential improvements:

- Better ride-history views
- Power graphs
- Time-in-zone graphs
- Workout templates
- Custom workout editor
- Configurable audio cues
- Full-screen interval-change overlays
- Larger/simplified Ride Mode controls
- Chess/game statistics associated with rides
- Mock sensor implementation for development without the Peloton

---

## Development Without the Bike

The sensor subsystem should expose an interface such as `BikeDataSource` with both real and mock implementations.

For example:

```text
BikeDataSource
 ├── PelotonBikeDataSource
 └── MockBikeDataSource
```

The mock implementation can generate realistic power, cadence, and resistance values. This allows nearly all UI, Power Zone, workout, storage, and Lichess development to happen on a normal Android emulator or development device.

Only the Peloton-specific sensor adapter needs to be tested directly on the bike.

---

## Design Principles

### Chess First During a Ride

The board is the thing the rider actively interacts with. Cycling information should be visible without making the chess experience awkward.

### Center the Board

The board should be physically centered on the Peloton display, not merely centered in the leftover area after allocating a cycling sidebar.

### Glanceable Cycling Data

Current zone and watts should be immediately readable. Secondary values can be smaller.

### Audio Over Visual Interruptions

Interval changes should primarily use audio cues. Visual overlays should be brief and should not unnecessarily interfere with the chess game.

### Large Touch Targets

Controls need to work while the user is pedaling.

### Read-Only Bike Integration

There is no need to control Peloton hardware. Reading telemetry is sufficient and reduces risk and complexity.

### Modular Hardware Layer

Peloton's internal sensor API is undocumented, so hardware integration must be isolated from the rest of the application.

### No Chess Assistance

No engine analysis or move suggestions during live Lichess games.

---

## Open Questions

Before implementing the Peloton sensor adapter, determine the exact Peloton model:

- Original Peloton Bike / hardware generation
- Bike+
- Tablet model / Android version if relevant

This determines which known Affernet/Binder interface should be implemented first.

Other decisions can be made during development:

- Whether the initial chess prototype should use WebView or go directly to the Board API
- Exact Power Zone percentage boundaries
- Workout file/data format
- Local database technology
- Whether workout history should eventually export to other fitness platforms
- Whether heart-rate sensor support should be added

---

## Repository

Repository name:

`pedalmate`

Suggested project description:

> **PedalMate — an Android cycling dashboard combining Power Zone training with online chess.**

Avoid using `Peloton` or `Lichess` in the product/repository name so PedalMate does not appear to be an official product from either organization. Their names can be used descriptively in documentation to explain compatibility and integrations.

---

## Useful Existing Projects / References

These projects are useful implementation references:

- **OpenRide** — open-source Android application demonstrating direct Peloton sensor access, including AffernetService/Binder integration and hardware abstraction.
- **Grupetto / Peloton Overlay** — demonstrates displaying Peloton power, cadence, resistance, and speed from an Android application.
- **Lichess Mobile** — official open-source Lichess mobile client.
- **Lichess API / Board API** — supported interface for third-party chess clients.
- **Chessground** — Lichess's open-source chess board UI component.
- **chessops** — Lichess chess rules/tooling library.

These should be treated as references and dependencies only where their licenses and integration models permit.

---

## Initial Definition of Done

The first genuinely useful PedalMate version does **not** need every planned feature.

A strong v0.1 would be:

1. Launchable from the Peloton's existing custom Android launcher.
2. Reads live power, cadence, and resistance.
3. Stores an FTP value and displays the current Power Zone.
4. Runs a simple predefined interval workout.
5. Gives audible interval-change cues.
6. Displays a centered chess board connected to Lichess.
7. Shows current zone, watts, cadence, resistance, interval time, and next interval without displacing the board from the center of the display.

Once that works reliably, everything else can evolve incrementally.
