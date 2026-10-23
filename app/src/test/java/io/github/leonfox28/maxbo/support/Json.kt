package io.github.leonfox28.maxbo.support

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

// Synthetic JSON for tests, written like the Dart tests' map literals. A null
// entry (as from `x?.let { "key" to it }`) is left out, like Dart's `?x`; a
// null value is JSON null.

fun obj(vararg entries: Pair<String, Any?>?): JsonObject =
    JsonObject(entries.filterNotNull().associate { (key, value) -> key to toJson(value) })

fun arr(vararg items: Any?): JsonArray = JsonArray(items.map(::toJson))

fun toJson(value: Any?): JsonElement =
    when (value) {
        null -> JsonNull
        is JsonElement -> value
        is String -> JsonPrimitive(value)
        is Number -> JsonPrimitive(value)
        is Boolean -> JsonPrimitive(value)
        is Map<*, *> -> JsonObject(value.entries.associate { (k, v) -> k.toString() to toJson(v) })
        is Iterable<*> -> JsonArray(value.map(::toJson))
        else -> error("not JSON: $value")
    }

/** This object with [entries] added or replaced, like Dart's `{...a, ...b}`. */
operator fun JsonObject.plus(entries: Map<String, Any?>): JsonObject =
    JsonObject(this.toMap() + entries.mapValues { toJson(it.value) })

operator fun JsonObject.plus(other: JsonObject): JsonObject = JsonObject(this.toMap() + other)

fun JsonObject.without(vararg keys: String): JsonObject = JsonObject(this.toMap() - keys.toSet())
