package dev.pedalmate.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import dev.pedalmate.ui.setup.HrTargetInput
import dev.pedalmate.workout.FtpCalculator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

/** User settings as read from [SettingsStore.settings]. */
data class Settings(
    val ftp: Int?,
    val lastWorkoutId: String?,
    val lichessPackage: String,
    val overlayX: Int?,
    val overlayY: Int?,
    val overlayW: Int?,
    val overlayH: Int?,
    val hrAddress: String?,
    val hrName: String?,
    val hrTargetMin: Int,
    val hrTargetMax: Int,
)

private val FTP = intPreferencesKey("ftp")
private val LAST_WORKOUT = stringPreferencesKey("lastWorkoutId")
private val LICHESS_PKG = stringPreferencesKey("lichessPackage")
private val OVERLAY_X = intPreferencesKey("overlayX")
private val OVERLAY_Y = intPreferencesKey("overlayY")
private val OVERLAY_W = intPreferencesKey("overlayW")
private val OVERLAY_H = intPreferencesKey("overlayH")
private val HR_ADDRESS = stringPreferencesKey("hrAddress")
private val HR_NAME = stringPreferencesKey("hrName")
private val HR_TARGET_MIN = intPreferencesKey("hrTargetMin")
private val HR_TARGET_MAX = intPreferencesKey("hrTargetMax")

/** DataStore-backed settings. Create one instance per process. */
class SettingsStore(private val dataStore: DataStore<Preferences>) {
    val settings: Flow<Settings> = dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { p ->
            val hrMin = p[HR_TARGET_MIN]
            val hrMax = p[HR_TARGET_MAX]
            val hrValid = hrMin != null && hrMax != null && HrTargetInput.isValid(hrMin, hrMax)
            Settings(
                ftp = p[FTP]?.takeIf { FtpCalculator.isValid(it) },
                lastWorkoutId = p[LAST_WORKOUT],
                lichessPackage = p[LICHESS_PKG]?.takeIf { it.isNotBlank() } ?: DEFAULT_LICHESS_PACKAGE,
                overlayX = p[OVERLAY_X],
                overlayY = p[OVERLAY_Y],
                overlayW = p[OVERLAY_W],
                overlayH = p[OVERLAY_H],
                hrAddress = p[HR_ADDRESS],
                hrName = p[HR_NAME],
                hrTargetMin = if (hrValid) hrMin!! else DEFAULT_HR_MIN,
                hrTargetMax = if (hrValid) hrMax!! else DEFAULT_HR_MAX,
            )
        }

    suspend fun setFtp(ftp: Int?) {
        require(ftp == null || FtpCalculator.isValid(ftp)) { "FTP out of range: $ftp" }
        dataStore.edit { if (ftp == null) it.remove(FTP) else it[FTP] = ftp }
    }

    suspend fun setLastWorkoutId(id: String?) {
        dataStore.edit { if (id == null) it.remove(LAST_WORKOUT) else it[LAST_WORKOUT] = id }
    }

    suspend fun setLichessPackage(pkg: String) {
        require(pkg.isNotBlank()) { "package name must not be blank" }
        dataStore.edit { it[LICHESS_PKG] = pkg }
    }

    suspend fun setOverlayPosition(x: Int, y: Int, w: Int, h: Int) {
        dataStore.edit { it[OVERLAY_X] = x; it[OVERLAY_Y] = y; it[OVERLAY_W] = w; it[OVERLAY_H] = h }
    }

    /** Both null clears the saved heart-rate device. */
    suspend fun setHrDevice(address: String?, name: String?) {
        dataStore.edit {
            if (address == null) it.remove(HR_ADDRESS) else it[HR_ADDRESS] = address
            if (name == null) it.remove(HR_NAME) else it[HR_NAME] = name
        }
    }

    suspend fun setHrTarget(min: Int, max: Int) {
        require(HrTargetInput.isValid(min, max)) { "HR target invalid: $min..$max" }
        dataStore.edit { it[HR_TARGET_MIN] = min; it[HR_TARGET_MAX] = max }
    }

    companion object {
        const val DEFAULT_HR_MIN = 130
        const val DEFAULT_HR_MAX = 140
        const val DEFAULT_LICHESS_PACKAGE = "org.lichess.mobileV2"

        fun create(context: Context): SettingsStore =
            SettingsStore(PreferenceDataStoreFactory.create { context.applicationContext.preferencesDataStoreFile("settings") })
    }
}
