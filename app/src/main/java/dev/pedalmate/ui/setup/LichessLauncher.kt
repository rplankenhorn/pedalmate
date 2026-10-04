package dev.pedalmate.ui.setup

/** Chooses which installed Lichess package to launch. */
object LichessLauncher {
    const val FALLBACK_PACKAGE = "org.lichess.mobileapp"

    /** The configured package when installed, else the fallback when installed, else null. */
    fun resolve(configured: String, isInstalled: (String) -> Boolean): String? = when {
        isInstalled(configured) -> configured
        configured != FALLBACK_PACKAGE && isInstalled(FALLBACK_PACKAGE) -> FALLBACK_PACKAGE
        else -> null
    }
}
