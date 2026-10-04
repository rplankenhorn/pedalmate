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
- [ ] Overlay tap-through confirmed with Lichess in front: a piece move succeeds while the panel and an interval toast are both on screen
- [ ] Toast dismissal timed from logcat: it goes away about 4 s after the last interval change
- [ ] Real BLE scan from PAIR HR STRAP lists the iPhone relay
- [ ] If the pairing scan returns nothing, check logcat for "scanning too frequently" (two scanners can run at once: pairing and the connector)
- [ ] Record which iPhone relay app was used and whether it was in the foreground: ____ . iOS moves service UUIDs to the overflow area when an app is backgrounded, so the 0x180D-filtered scan may be blind; the fallback would be an unfiltered scan matched by saved name
- [ ] Overlay permission granted mid-ride (ride started with it missing, warning shown): after returning to PedalMate the panel appears without restarting the ride (I2)
- [ ] Logcat watch list, note any hit: `typed startForeground`, `overlay not shown`, `interval toast not shown`, `scan failed`, `riding unrecorded`
- [ ] Peloton's ActivationActivity may self-launch: back out of it and note when it happened: ____
- [ ] Paste relevant logcat excerpts below (`adb logcat -s PedalMate:V PelotonBikeIface:V BleHeartRate:V`)
Result: PASS / FAIL  Notes:
