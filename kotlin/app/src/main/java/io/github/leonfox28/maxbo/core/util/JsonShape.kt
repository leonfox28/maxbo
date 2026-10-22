package io.github.leonfox28.maxbo.core.util

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.longOrNull

/**
 * Describes the structure of decoded JSON without any of its values, e.g. `{ok: int, data: {uid:
 * str, tags: [3× str]}}`.
 *
 * Safe to log: it reveals which fields exist, never what they contain.
 */
fun describeShape(json: JsonElement?, maxDepth: Int = 4): String {
    if (maxDepth < 0) return "…"
    return when (json) {
        null,
        JsonNull -> "null"
        is JsonPrimitive ->
            when {
                json.isString -> "str"
                json.booleanOrNull != null -> "bool"
                json.longOrNull != null -> "int"
                else -> "double"
            }
        is JsonArray ->
            if (json.isEmpty()) "[]"
            else "[${json.size}× ${describeShape(json.first(), maxDepth - 1)}]"
        is JsonObject ->
            json.entries.joinToString(", ", "{", "}") { (key, value) ->
                "$key: ${describeShape(value, maxDepth - 1)}"
            }
    }
}
