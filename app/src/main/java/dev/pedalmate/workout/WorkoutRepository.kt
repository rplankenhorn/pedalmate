package dev.pedalmate.workout

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
        for (name in assets.list(dir).filter { it.endsWith(".json") }.sorted()) {
            try {
                workouts += WorkoutJson.parse(assets.read("$dir/$name"))
            } catch (e: WorkoutFormatException) {
                errors[name] = e.message ?: "invalid workout"
            }
        }
        LoadResult(workouts, errors)
    }

    fun load(): LoadResult = result
    fun all(): List<WorkoutDefinition> = result.workouts
    fun byId(id: String): WorkoutDefinition? = result.workouts.firstOrNull { it.id == id }
}
