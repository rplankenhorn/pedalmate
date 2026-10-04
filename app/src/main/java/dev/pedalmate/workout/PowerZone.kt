package dev.pedalmate.workout

/** Seven-zone model; [upperPercent] is the exclusive upper edge as a whole percent of FTP (null for Z7). */
enum class PowerZone(val number: Int, val upperPercent: Int?) {
    Z1(1, 55), Z2(2, 75), Z3(3, 90), Z4(4, 105), Z5(5, 120), Z6(6, 150), Z7(7, null);

    companion object {
        fun fromNumber(n: Int): PowerZone? = entries.firstOrNull { it.number == n }
    }
}
