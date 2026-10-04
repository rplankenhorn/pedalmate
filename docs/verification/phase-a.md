# Phase A on-bike verification

Device: Peloton Bike+ (board `topaz`, Android 10). Pair ADB first (OpenPelo wireless ADB). Package for real builds: `dev.pedalmate`.

## Stage 1 (after A3): sensor proof

- [ ] `adb shell getprop ro.product.board` prints `topaz`
- [ ] Record: `adb shell wm size` = ____ ; `adb shell wm density` = ____ ; `adb shell getprop ro.build.version.sdk` = ____
- [ ] `mise exec -- ./gradlew installRealDebug` succeeds (or `adb install -r app/build/outputs/apk/real/debug/app-real-debug.apk`)
- [ ] App launches from the Peloton custom launcher (DoD 1)
- [ ] Debug screen shows POWER / CADENCE / RESISTANCE
- [ ] `adb logcat -s PedalMate:V PelotonBikeIface:V` shows the bind, then `bike frames=N state=Connected` with N growing about 20 per second
- [ ] Pedalling: values plausible versus the stock Peloton UI and updating at about 20 Hz
- [ ] Stop pedalling: power and cadence go to 0 (the screen still shows numbers; no red SENSOR LOST / NO SENSOR banner, which confirms the 1 Hz poll fallback works at idle)
- [ ] `adb shell am force-stop com.onepeloton.affernetservice`: the rebinder recovers and frames resume within 30 s
Result: PASS / FAIL  Notes:

## Stage 2 (A16): overlay ride

- [ ] `adb shell appops set dev.pedalmate SYSTEM_ALERT_WINDOW allow`
- [ ] `adb shell pm grant dev.pedalmate android.permission.ACCESS_FINE_LOCATION`
- [ ] `adb shell pm list packages | grep lichess` = ____ ; set the package in PedalMate setup if it differs from `org.lichess.mobileV2`
- [ ] Set FTP, start workout `pz-43`; Lichess launches
- [ ] Overlay is visible over the Lichess board and does not block moves (pieces still move with the overlay on screen)
- [ ] Record whether the right column covers Lichess clocks or the resign button: ____
- [ ] Drag the overlay; tap toggles minimised pill and back; position and mode survive an app restart
- [ ] Interval cues audible at each change; toast appears and dismisses after about 4 s
- [ ] `adb shell dumpsys window windows | grep -i pedalmate` shows the overlay window(s)
- [ ] `adb shell dumpsys activity services dev.pedalmate` shows RideService running
- [ ] 20+ minute ride: no crash or kill; `adb shell dumpsys meminfo dev.pedalmate` stays flat
- [ ] 10 minutes without touching the screen: screen stays on, no Peloton activity takes over
- [ ] Heart rate via iPhone relay (BlueHeart / HeartCast / HR Broadcast): appears; also reconnects after the phone sleeps and wakes
- [ ] Pull the DB with its WAL files: `for f in pedalmate.db pedalmate.db-wal pedalmate.db-shm; do adb exec-out run-as dev.pedalmate cat databases/$f > $f; done`; a ride row with samples exists and has `endedAt` (a missing -wal/-shm, already checkpointed, yields an empty local file and is not a failure)
- [ ] Paste relevant logcat excerpts below (`adb logcat -s PedalMate:V PelotonBikeIface:V BleHeartRate:V`)
Result: PASS / FAIL  Notes:
