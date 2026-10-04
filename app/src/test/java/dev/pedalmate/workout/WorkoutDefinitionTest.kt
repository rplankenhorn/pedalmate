package dev.pedalmate.workout

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class WorkoutDefinitionTest {
    private fun def(
        id: String = "pz-43",
        name: String = "Name",
        steps: List<WorkoutStep> = listOf(WorkoutStep("Warmup", 60, 2)),
    ) = WorkoutDefinition(id, name, null, steps)

    private fun step(label: String = "S", seconds: Int = 60, zone: Int? = 2, tag: String? = null) =
        WorkoutStep(label, seconds, zone, tag)

    @Test fun `round trip keeps null description, null zone and tag`() {
        val d = WorkoutDefinition(
            "x", "X", null,
            listOf(WorkoutStep("A", 60, 2), WorkoutStep("Test", 1200, null, "ftp-window")),
        )
        assertEquals(d, WorkoutJson.parse(WorkoutJson.encode(d)))
    }

    @Test fun `totalSeconds sums steps`() {
        val d = def(steps = listOf(step(seconds = 600), step(seconds = 300), step(seconds = 300)))
        assertEquals(1200, d.totalSeconds)
    }

    @Test fun `valid definitions have no errors`() {
        assertTrue(def().validate().isEmpty())
        assertTrue(def(steps = listOf(step(zone = null))).validate().isEmpty())
        assertTrue(def(steps = listOf(step(zone = 7))).validate().isEmpty())
        assertTrue(def(steps = listOf(step(seconds = 21600))).validate().isEmpty())
    }

    private fun assertInvalid(d: WorkoutDefinition) = assertTrue(d.validate().isNotEmpty())

    @Test fun `invalid definitions report errors`() {
        assertInvalid(def(steps = emptyList()))
        assertInvalid(def(steps = listOf(step(seconds = 0))))
        assertInvalid(def(steps = listOf(step(seconds = -1))))
        assertInvalid(def(steps = listOf(step(seconds = 21601))))
        assertInvalid(def(steps = listOf(step(zone = 0))))
        assertInvalid(def(steps = listOf(step(zone = 8))))
        assertInvalid(def(steps = listOf(step(label = " "))))
        assertInvalid(def(name = " "))
        assertInvalid(def(id = "PZ 43"))
        assertInvalid(def(id = "-x"))
        assertInvalid(def(steps = List(201) { step() }))
    }

    @Test fun `all errors are collected`() {
        val errors = def(name = "", steps = listOf(step(seconds = 0, zone = 9))).validate()
        assertTrue(errors.size >= 3)
    }

    @Test fun `parse throws for malformed json`() {
        try { WorkoutJson.parse("{"); fail() } catch (_: WorkoutFormatException) {}
    }

    @Test fun `parse throws for missing steps`() {
        try { WorkoutJson.parse("""{"id":"a","name":"A"}"""); fail() } catch (_: WorkoutFormatException) {}
    }

    @Test fun `parse throws for zero seconds`() {
        val text = """{"id":"a","name":"A","steps":[{"label":"L","seconds":0}]}"""
        try { WorkoutJson.parse(text); fail() } catch (_: WorkoutFormatException) {}
    }

    @Test fun `parse ignores unknown keys`() {
        val text = """{"id":"a","name":"A","foo":1,"steps":[{"label":"L","seconds":5}]}"""
        assertEquals("a", WorkoutJson.parse(text).id)
    }
}
