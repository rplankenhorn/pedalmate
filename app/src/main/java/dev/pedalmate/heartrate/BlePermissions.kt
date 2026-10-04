// Derived from OpenRide (Apache-2.0), app/src/main/java/dev/digitalducktape/openride/core/heartrate/BlePermissions.kt. Modified by PedalMate.
package dev.pedalmate.heartrate

/**
 * Result of checking/requesting the BLE scan permissions — drives the
 * pairing screen's "clean rationale + graceful degradation" requirement (PRD P1-4, T17)
 * without the screen needing to re-derive this branching itself.
 */
sealed interface BlePermissionState {
    /** All required permissions are currently granted; scanning/connecting can proceed. */
    data object Granted : BlePermissionState

    /** Never asked yet this session — show the system prompt directly, no rationale needed. */
    data object NotRequested : BlePermissionState

    /** Denied at least once, but Android will still show its own prompt again — show a short
     *  explanation of why the permission is needed before re-asking. */
    data object ShouldShowRationale : BlePermissionState

    /** Denied "don't ask again" (or denied on a fresh install with no rationale ever shown) —
     *  Android won't show its own prompt again; the only path forward is this app's Settings
     *  page. Degrade gracefully here rather than looping a prompt the system will never show. */
    data object PermanentlyDenied : BlePermissionState
}

/**
 * Pure reducer from Android's permission-check/rationale signals to a [BlePermissionState] —
 * the part of the pairing screen's permission handling that's unit-testable without touching
 * real `ContextCompat`/`ActivityCompat` APIs (see `BlePermissionsTest`).
 *
 * @param permissions the permission strings to check, e.g. `ACCESS_FINE_LOCATION` on API 29
 * @param granted this app's current per-permission grant status (post
 *   `ContextCompat.checkSelfPermission`)
 * @param shouldShowRationale Android's per-permission "should show rationale" signal
 *   (`ActivityCompat.shouldShowRequestPermissionRationale`) — `false` both *before* the first
 *   request ever and *after* a permanent denial, which is why [everRequested] is needed to
 *   tell those two apart.
 * @param everRequested whether this screen has already asked at least once this process
 */
fun reduceBlePermissionState(
    permissions: Array<String>,
    granted: (String) -> Boolean,
    shouldShowRationale: (String) -> Boolean,
    everRequested: Boolean,
): BlePermissionState {
    if (permissions.all(granted)) return BlePermissionState.Granted
    if (!everRequested) return BlePermissionState.NotRequested
    return if (permissions.any(shouldShowRationale)) {
        BlePermissionState.ShouldShowRationale
    } else {
        BlePermissionState.PermanentlyDenied
    }
}
