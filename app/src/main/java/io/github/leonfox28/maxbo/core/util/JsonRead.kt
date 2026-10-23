package io.github.leonfox28.maxbo.core.util

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull

// Lenient readers for Weibo's JSON, where fields come and go and numbers are
// sometimes sent as strings.

/** A non-empty string, or an integer rendered as one; ids are always kept as strings. */
fun readString(value: JsonElement?): String? {
    val primitive = value as? JsonPrimitive ?: return null
    if (primitive.isString) return primitive.content.ifEmpty { null }
    return primitive.longOrNull?.toString()
}

fun readInt(value: JsonElement?): Long? {
    val primitive = value as? JsonPrimitive ?: return null
    if (primitive.isString) return primitive.content.toLongOrNull()
    return primitive.longOrNull ?: primitive.doubleOrNull?.toLong()
}

fun readBool(value: JsonElement?): Boolean? =
    (value as? JsonPrimitive)?.takeUnless { it.isString }?.booleanOrNull

fun readMap(value: JsonElement?): JsonObject? = value as? JsonObject

/** The objects in [value], skipping anything else; empty if it isn't a list. */
fun readMaps(value: JsonElement?): List<JsonObject> =
    (value as? JsonArray)?.filterIsInstance<JsonObject>().orEmpty()

/** An absolute https URL; Weibo often sends protocol-relative ones. */
fun readUrl(value: JsonElement?): String? {
    val url = readString(value) ?: return null
    if (url.startsWith("//")) return "https:$url"
    return if (url.startsWith("http")) url else null
}

/** An absolute https URL from an attribute such as an `<img>`'s `src`. */
fun readUrl(value: String?): String? = readUrl(value?.let(::JsonPrimitive))
