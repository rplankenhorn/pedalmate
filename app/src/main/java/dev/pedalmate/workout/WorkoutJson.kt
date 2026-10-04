package dev.pedalmate.workout

import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** JSON encoding and validated decoding of [WorkoutDefinition]. */
object WorkoutJson {
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true; encodeDefaults = false }

    /** Parses and validates; throws [WorkoutFormatException] on any problem. */
    fun parse(text: String): WorkoutDefinition {
        val def = try {
            json.decodeFromString<WorkoutDefinition>(text)
        } catch (e: SerializationException) {
            throw WorkoutFormatException("Invalid workout JSON: ${e.message}", e)
        } catch (e: IllegalArgumentException) {
            throw WorkoutFormatException("Invalid workout JSON: ${e.message}", e)
        }
        val errors = def.validate()
        if (errors.isNotEmpty()) throw WorkoutFormatException(errors.joinToString("; "))
        return def
    }

    fun encode(def: WorkoutDefinition): String = json.encodeToString(def)
}
