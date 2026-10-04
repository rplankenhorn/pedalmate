package dev.pedalmate

import android.app.Application
import dev.pedalmate.data.AppContainer

/** Application class; owns the manual-DI [AppContainer]. */
class PedalMateApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
