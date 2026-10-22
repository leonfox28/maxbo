package io.github.leonfox28.maxbo.data.api

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

/**
 * Extracts the `window.$CONFIG = {...}` object that weibo.com embeds in its pages, or returns null
 * if the page has none or it isn't valid JSON.
 *
 * The web app reads the signed-in user from it; there is no JSON endpoint for that (`/ajax/config`
 * answers 404).
 */
fun extractPageConfig(html: String): JsonObject? {
    val marker = html.indexOf("window.\$CONFIG")
    if (marker < 0) return null
    val start = html.indexOf('{', marker)
    if (start < 0) return null
    val end = matchingBrace(html, start)
    if (end < 0) return null
    return try {
        Json.parseToJsonElement(html.substring(start, end + 1)) as? JsonObject
    } catch (_: SerializationException) {
        null
    }
}

/**
 * Index of the `}` closing the object that opens at [start], skipping braces inside JSON strings;
 * -1 if it never closes.
 */
private fun matchingBrace(s: String, start: Int): Int {
    var depth = 0
    var inString = false
    var i = start
    while (i < s.length) {
        val c = s[i]
        if (inString) {
            if (c == '\\') i++ else if (c == '"') inString = false
        } else if (c == '"') {
            inString = true
        } else if (c == '{') {
            depth++
        } else if (c == '}' && --depth == 0) {
            return i
        }
        i++
    }
    return -1
}
