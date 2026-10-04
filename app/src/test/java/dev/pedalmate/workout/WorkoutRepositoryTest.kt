package dev.pedalmate.workout

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private class MemAssets(val files: Map<String, String>) : AssetReader {
    override fun list(dir: String) = files.keys.filter { it.startsWith("$dir/") }.map { it.removePrefix("$dir/") }
    override fun read(path: String) = files.getValue(path)
}

/** Reads the real preset files; Gradle runs unit tests with cwd = app/. */
private class DirAssets(val root: java.io.File) : AssetReader {
    override fun list(dir: String) = root.list()!!.toList()
    override fun read(path: String) = java.io.File(root, path.substringAfter("/")).readText()
}

class WorkoutRepositoryTest {
    private fun json(id: String, seconds: Int = 60) =
        """{"id":"$id","name":"N","steps":[{"label":"L","seconds":$seconds,"zone":2}]}"""

    @Test fun `load sorts by file name, isolates errors and ignores non-json`() {
        val repo = WorkoutRepository(
            MemAssets(
                mapOf(
                    "workouts/b.json" to json("b"),
                    "workouts/a.json" to json("a"),
                    "workouts/bad.json" to json("bad", seconds = 0),
                    "workouts/notes.txt" to "ignored",
                ),
            ),
        )
        val result = repo.load()
        assertEquals(listOf("a", "b"), result.workouts.map { it.id })
        assertEquals(setOf("bad.json"), result.errors.keys)
        assertEquals(listOf("a", "b"), repo.all().map { it.id })
        assertEquals("b", repo.byId("b")?.id)
        assertNull(repo.byId("missing"))
    }

    private val presets = WorkoutRepository(DirAssets(java.io.File("src/main/assets/workouts")))

    @Test fun `presets load without errors and ids match file names`() {
        val result = presets.load()
        assertTrue(result.errors.toString(), result.errors.isEmpty())
        assertEquals(setOf("intro-20", "endurance-z2-45", "pz-43", "ftp-test"), result.workouts.map { it.id }.toSet())
    }

    @Test fun `preset durations`() {
        assertEquals(1200, presets.byId("intro-20")!!.totalSeconds)
        assertEquals(2700, presets.byId("endurance-z2-45")!!.totalSeconds)
        assertEquals(2580, presets.byId("pz-43")!!.totalSeconds)
        assertEquals(2100, presets.byId("ftp-test")!!.totalSeconds)
    }

    @Test fun `pz-43 zones and labels`() {
        val steps = presets.byId("pz-43")!!.steps
        assertEquals(listOf(2, 3, 4, 3, 4, 5, 2, 1), steps.map { it.zone })
        assertEquals(
            listOf("Warmup", "Zone 3", "Zone 4", "Zone 3", "Zone 4", "Zone 5", "Zone 2", "Cooldown"),
            steps.map { it.label },
        )
    }

    @Test fun `ftp-test has one untargeted ftp-window step`() {
        val tagged = presets.byId("ftp-test")!!.steps.filter { it.tag == "ftp-window" }
        assertEquals(1, tagged.size)
        assertEquals(1200, tagged[0].seconds)
        assertNull(tagged[0].zone)
    }
}
