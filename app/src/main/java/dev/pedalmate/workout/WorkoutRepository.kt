package dev.pedalmate.workout

import java.io.IOException

/** Minimal asset access so the workout package stays free of Android types. */
interface AssetReader {
    fun list(dir: String): List<String>
    fun read(path: String): String
}

/** Loads workout definitions (`*.json`) from [dir] via [assets]. */
class WorkoutRepository(private val assets: AssetReader, private val dir: String = "workouts") {
    /** Valid [workouts] sorted by file name, plus [errors] keyed by file name. */
    class LoadResult(val workouts: List<WorkoutDefinition>, val errors: Map<String, String>)

    private val result: LoadResult by lazy {
        val workouts = mutableListOf<WorkoutDefinition>()
        val errors = linkedMapOf<String, String>()
        val sources = HashMap<String, String>()
        for (name in assets.list(dir).filter { it.endsWith(".json") }.sorted()) {
            try {
                val w = WorkoutJson.parse(assets.read("$dir/$name"))
                val first = sources[w.id]
                if (first != null) {
                    errors[name] = "duplicate id '${w.id}' (already defined in $first); ignored"
                } else {
                    sources[w.id] = name
                    workouts += w
                }
            } catch (e: WorkoutFormatException) {
                errors[name] = e.message ?: "invalid workout"
            } catch (e: IOException) {
                errors[name] = "could not read: ${e.message ?: e.javaClass.simpleName}"
            }
        }
        LoadResult(workouts, errors)
    }

    fun load(): LoadResult = result
    fun all(): List<WorkoutDefinition> = result.workouts
    fun byId(id: String): WorkoutDefinition? = result.workouts.firstOrNull { it.id == id }
}
