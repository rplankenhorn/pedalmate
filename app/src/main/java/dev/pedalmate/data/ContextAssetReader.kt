package dev.pedalmate.data

import android.content.res.AssetManager
import dev.pedalmate.workout.AssetReader

/** [AssetReader] backed by the Android [AssetManager]. */
class ContextAssetReader(private val assets: AssetManager) : AssetReader {
    override fun list(dir: String): List<String> = assets.list(dir)?.toList().orEmpty()
    override fun read(path: String): String = assets.open(path).bufferedReader().use { it.readText() }
}
