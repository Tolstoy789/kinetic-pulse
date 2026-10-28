package com.kineticpulse.core.quicklog

import com.kineticpulse.core.model.RoutineSetDto
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/**
 * Recibe fragmentos de texto en streaming desde el LLM y emite cada objeto JSON en cuanto
 * su llave de cierre llega, sin esperar a que termine toda la generación.
 *
 * Tolera ruido alrededor (```json, prosa, saltos de línea) y llaves dentro de strings.
 * No es thread-safe: una instancia por generación.
 */
class StreamingJsonExtractor(
    private val json: Json = DefaultJson,
) {
    private val buffer = StringBuilder()
    private var depth = 0
    private var inString = false
    private var escaped = false

    /** Alimenta un fragmento y devuelve los sets completos que ya se pudieron decodificar. */
    fun feed(chunk: String): List<RoutineSetDto> {
        val completed = mutableListOf<RoutineSetDto>()
        for (c in chunk) {
            if (depth == 0) {
                if (c == '{') {
                    depth = 1
                    buffer.setLength(0)
                    buffer.append(c)
                }
                continue
            }
            buffer.append(c)
            when {
                escaped -> escaped = false
                inString && c == '\\' -> escaped = true
                c == '"' -> inString = !inString
                !inString && c == '{' -> depth++
                !inString && c == '}' -> {
                    depth--
                    if (depth == 0) decode(buffer.toString())?.let(completed::add)
                }
            }
        }
        return completed
    }

    private fun decode(raw: String): RoutineSetDto? = try {
        json.decodeFromString(RoutineSetDto.serializer(), raw).sanitized()
    } catch (_: SerializationException) {
        null
    } catch (_: IllegalArgumentException) {
        null
    }

    companion object {
        val DefaultJson = Json {
            ignoreUnknownKeys = true
            isLenient = true
            coerceInputValues = true
            explicitNulls = false
        }
    }
}
