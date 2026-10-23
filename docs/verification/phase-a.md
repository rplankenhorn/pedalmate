# Phase A on-bike verification

Device: Peloton Bike+ (board `topaz`, Android 10). Pair ADB first (OpenPelo wireless ADB). Package for real builds: `dev.pedalmate`.

## Stage 1 (after A3): sensor proof

- [x] `adb shell getprop ro.product.board` prints `topaz`
- [x] Record: `adb shell wm size` = 1920x1080 ; `adb shell wm density` = 240 ; `adb shell getprop ro.build.version.sdk` = 29
- [x] `mise exec -- ./gradlew installRealDebug` succeeds (or `adb install -r app/build/outputs/apk/real/debug/app-real-debug.apk`)
- [x] App launches from the Peloton custom launcher (DoD 1)
- [x] Debug screen shows POWER / CADENCE / RESISTANCE
- [x] `adb logcat -s PedalMate:V PelotonBikeIface:V` shows the bind, then `bike frames=N state=Connected` with N growing about 20 per second
- [x] Pedalling: values plausible versus the stock Peloton UI and updating at about 20 Hz
- [x] Stop pedalling: power and cadence go to 0 (the screen still shows numbers; no red SENSOR LOST / NO SENSOR banner, which confirms the 1 Hz poll fallback works at idle)
- [x] `adb shell am force-stop com.onepeloton.affernetservice`: the rebinder recovers and frames resume within 30 s
Result: PASS (2026-10-04)  Notes:
  - Rebind: binding died at 10:33:07.336, reconnect attempts at 1000 ms then 2000 ms, recovered at 10:33:09.352
  - GMS crash spam (`com.google.android.gms.persistent`, "Failed to find provider com.google.android.gsf.gservices") is pre-existing and unrelated
  - Peloton's own activation activity (`com.peloton.activity/.activation.ActivationActivity`) self-launches over foreground apps; watch for it in Stage 2's "no Peloton activity takes over" item

## Stage 2 (A16): overlay ride

- [x] `adb shell appops set dev.pedalmate SYSTEM_ALERT_WINDOW allow` -> allow (already set 2026-10-04)
- [x] `adb shell pm grant dev.pedalmate android.permission.ACCESS_FINE_LOCATION` -> granted=true
- [x] `adb shell pm list packages | grep lichess` = org.lichess.mobileV2 ; set the package in PedalMate setup if it differs from `org.lichess.mobileV2` -> matches default, no change
- [ ] Set FTP, start workout `pz-43`; Lichess launches -> NOT RUN: 2026-10-06 ride was Free ride (FTP 250); Lichess launched on start
- [x] Overlay is visible over the Lichess board and does not block moves (pieces still move with the overlay on screen) -> visible over board; user played a rated 5+3 game with the panel on screen
- [x] Record whether the right column covers Lichess clocks or the resign button: neither; covers the move list only. Clocks at y~140 and y~955 px, panel y 337..574 px
- [x] Drag the overlay; position survives an app restart -> drag/snap OK; position restored after reinstall (2026-10-07; the pill/minimize gesture was later removed). Note: saved top-left is kept when the panel size changes, so after the R2 shrink it floated until dragged once. Persisted overlay position re-docks after a size change (fix f357534, 2026-10-07): PASS. Legacy saved position fell back to the default right dock.
- [ ] Open PedalMate button (trailing end of the interval strip, 48 overlay-dp circle, ~28 device-dp (~42 px on the bike)): with Lichess in front, tap it -> PedalMate Setup comes to the front. Tapping the panel body does nothing (no pill)
- [ ] Open PedalMate button with Setup already in front (or behind other apps) -> Setup is raised, no second instance; `adb shell dumpsys activity activities | grep SetupActivity` shows exactly one; if logcat shows `Background activity launch blocked`, implement the PendingIntent.getActivity fallback in `RideService.openApp()`
- [ ] Tap the ride notification -> same single Setup instance comes to the front
- [ ] Interval cues audible at each change; toast appears and dismisses after about 4 s -> NOT RUN (free ride has no intervals)
- [x] `adb shell dumpsys window windows | grep -i pedalmate` shows the overlay window(s) -> Window{... u0 dev.pedalmate} appop=SYSTEM_ALERT_WINDOW present during ride; SetupActivity window behind Lichess
- [x] `adb shell dumpsys activity services dev.pedalmate` shows RideService running -> running during ride (bike frames logged every 5 s); 0 ServiceRecord after STOP
- [x] 20+ minute ride: no crash or kill; `adb shell dumpsys meminfo dev.pedalmate` stays flat -> PASS (2026-10-07, idle soak 27 min on pz-43). 24 valid samples: PSS 67-72 MB flat, 1 foreground service, overlay windows present, device Awake throughout, interval toast shown at every step change and gone ~5 s later, panel never covered the Lichess dialog. Three samples lost to adb USB blip (not an app fault).
- [ ] 10 minutes without touching the screen: screen stays on, no Peloton activity takes over -> NOT RUN
- [ ] Heart rate via iPhone relay (BlueHeart / HeartCast / HR Broadcast): appears; also reconnects after the phone sleeps and wakes -> PARTIAL: BlueHeart (mock HR, watch app would not install) connected 12:27:43 bike clock, ride avgHeartRateBpm=80; link dropped 12:28:16, then every reconnect logged 'Heart Rate Measurement characteristic not found (status=0)' and 'cycle failed (N), retry in 30000ms' for 13+ cycles (bead pedalmate-9gp). Sleep/wake reconnect not tested
- [x] Pull the DB with its WAL files: `for f in pedalmate.db pedalmate.db-wal pedalmate.db-shm; do adb exec-out run-as dev.pedalmate cat databases/$f > $f; done`; a ride row with samples exists and has `endedAt` (a missing -wal/-shm, already checkpointed, yields an empty local file and is not a failure) -> rides row id=3 startedAt=1791307316300 endedAt=1791307719879 workoutId=NULL ftp=250 sampleCount=404 avgPowerWatts=36 maxPowerWatts=52 avgCadenceRpm=54 avgResistancePercent=33 avgHeartRateBpm=80; 404 samples rows; -wal 416152 bytes, -shm 32768 bytes
- [ ] Overlay tap-through confirmed with Lichess in front: a piece move succeeds while the panel and an interval toast are both on screen -> panel only: moves succeeded with the panel on screen; toast not exercised
- [ ] Toast dismissal timed from logcat: it goes away about 4 s after the last interval change -> NOT RUN
- [x] Real BLE scan from PAIR HR STRAP lists the iPhone relay -> BlueHeart found and paired
- [ ] If the pairing scan returns nothing, check logcat for "scanning too frequently" (R10: the connector now pauses its own scan while the pairing scan runs, so only one scanner is active) -> n/a (scan found the relay)
- [x] Record which iPhone relay app was used and whether it was in the foreground: BlueHeart (mock HR mode), foreground state not recorded . iOS moves service UUIDs to the overflow area when an app is backgrounded, so the 0x180D-filtered scan may be blind. R10 (scan-first connector) adds saved address and name to the scan filter but deliberately no unfiltered scan: a backgrounded relay is only caught once it is foreground again. Re-check with HeartCast: foreground the relay, the strap should connect within seconds without opening PAIR HR STRAP, and a 40 s gap between frame bursts must not tear the link down (DEAD_MS 45 s; 60 s is the tunable)
- [x] Overlay permission granted mid-ride (ride started with it missing, warning shown): after returning to PedalMate the panel appears without restarting the ride (I2) -> PASS (2026-10-07). Overlay denied via appops -> Setup screen shows banner "Overlay permission missing: the ride will run without the panel" -> START RIDE -> logcat "W PedalMate: overlay not shown: NO_PERMISSION", ride RUNNING, no overlay window -> appops allow -> app backgrounded and reopened -> overlay window appears (mFrame [1363,379][1920,629]) without restarting the ride.
- [x] Logcat watch list, note any hit: `typed startForeground`, `overlay not shown`, `interval toast not shown`, `scan failed`, `riding unrecorded` -> no PedalMate/BleHeartRate hits for any watch phrase; unrelated com.google.android.gms crash loop (gsf provider missing) spams AndroidRuntime on this bike
- [ ] Peloton's ActivationActivity may self-launch: back out of it and note when it happened: did not happen during the 6.7 min ride
- [x] Paste relevant logcat excerpts below (`adb logcat -s PedalMate:V PelotonBikeIface:V BleHeartRate:V`)
```text
10-06 12:21:56.308  2608  2661 I PedalMate: start ride workout=null result=Started
10-06 12:22:16.235  2608  2660 I PedalMate: ride status=RUNNING step=null elapsed=19s bike=Connected watts=34 zone=Z1 target=null
10-06 12:27:43.289  2608  2608 I BleHeartRate: connecting to 79:2E:8E:0B:CE:6B (BlueHeart) viaScan=false fallback=false
10-06 12:28:16.557  2608  2660 I BleHeartRate: reconnecting: link dropped
10-06 12:28:18.469  2608  2652 W BleHeartRate: Heart Rate Measurement characteristic not found (status=0)
10-06 12:28:30.591  2608  2660 I BleHeartRate: cycle failed (1), retry in 2000ms
10-06 12:36:19.476  2608  2655 I BleHeartRate: connecting to 79:2E:8E:0B:CE:6B (BlueHeart) viaScan=false fallback=false
10-06 12:38:46.440  2608  2656 I PedalMate: bike frames=22596 state=Connected polling=false
```
- Foreground service kept logging status every 5 s after a ride reached FINISHED (observed ~19 h later, bead pedalmate-815): fixed in 49c4e7b, the service now stops itself 120 s after FINISHED while the panel shows "Done" (emulator smoke: stop logged at 120 s, service and overlay gone). Resolved.
- Polish shipped in build 1369dc8: HR connect fast-fail on link failure, pill touch target >= 48 dp (dfb0466), mock flavor label "PedalMate (mock)" (cee041c). Build 49c4e7b adds the post-finish auto-stop (pedalmate-815) and test-summary logging (pedalmate-j3y).
Result: PARTIAL (2026-10-13, head 4c97e8c) - remaining: HR pairing with HeartCast on the bike, overlay tap-through during a real Lichess move, HR reconnect after the phone sleeps.
  - 2026-10-13 morning ride on ade2e34: user rode a free ride with HeartCast paired and no overlay appeared. Device evidence shows RideService never received START (no start log, no DB ride, no service, no crash); user cannot confirm whether START RIDE or LAUNCH LICHESS was tapped. adb repro of START RIDE on the idle bike worked (panel shown). Stage 2 items (HR reconnect, tap-through, Done + auto-stop) remain untested.
  - R6 (e25b9cb, bead pedalmate-i8j): Log.i at every START boundary (ViewModel start decision incl. blocked reason, LAUNCH LICHESS tap, RideService.start dispatch, onStartCommand action/startId). Bike smoke on e25b9cb showed the full chain.
  - R7 (65527c7 + 4c97e8c, bead pedalmate-mvd): LAUNCH LICHESS while no ride is active shows an inline confirm panel (START RIDE AND LAUNCH / LAUNCH ONLY / CANCEL); verified on the bike 2026-10-13 (panel, CANCEL, START RIDE AND LAUNCH -> ride + Lichess).
  - Bike installed build: 4c97e8c (lastUpdateTime 2026-10-13 11:09:08 bike clock).
  - Flake bead pedalmate-7ft reopened (RideServiceTest linger test failed once on the R7 full run).
  - 2026-10-23: R9 (bead pedalmate-z8w) and R10 (bead pedalmate-vsw) landed on phase-a at 9425264, unit 435/435 both flavors, not yet installed on the bike (bike was not attached). R9: Open PedalMate button at the trailing end of the interval strip (48 overlay-dp), panel tap-to-pill gesture removed, SetupActivity.bringToFrontIntent (NEW_TASK|SINGLE_TOP|CLEAR_TOP) for the button and the ride notification. R10: scan-first HR reconnect for the saved strap (one continuous filtered scan, 5 min refresh, 8 s floor between scan starts), rotated address saved on scan match, DEAD_MS 45 s, pairing pauses the connector scan, ride status log has hr=. Bike checks pending: (a) HeartCast closed -> open Setup -> open HeartCast: scan matched + connected within ~10 s, no tap; (b) bursty HR ride: no reconnecting: no frame under 45 s; (c) PAIR HR STRAP still works and resumes the connector; (d) START RIDE, go to Lichess, tap the panel button: SetupActivity in front, one instance (dumpsys activity activities | grep SetupActivity); tap again from Setup: no-op; notification tap: same; (e) Done panel still shows and auto-stops.
