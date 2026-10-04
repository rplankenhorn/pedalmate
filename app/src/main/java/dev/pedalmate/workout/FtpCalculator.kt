package dev.pedalmate.workout

/** FTP estimation and validation. */
object FtpCalculator {
    const val MIN_FTP = 50
    const val MAX_FTP = 600

    /** 95 percent of the 20-minute average, rounded half up. */
    fun fromTwentyMinuteAverage(avgWatts: Double): Int =
        kotlin.math.floor(avgWatts * 95.0 / 100.0 + 0.5).toInt()

    fun isValid(ftp: Int?): Boolean = ftp != null && ftp in MIN_FTP..MAX_FTP
}
