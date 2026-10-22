package io.github.leonfox28.maxbo.core.session

import org.junit.Assert.assertEquals
import org.junit.Test

// CookieManager itself is checked on a device (androidTest), as the Flutter
// app's integration test did.
class WebCookiesTest {
    @Test
    fun `maps cookies by name, values with = and all`() {
        assertEquals(
            mapOf("SUB" to "_2A25abc", "XSRF-TOKEN" to "tok=en"),
            parseCookieHeader("SUB=_2A25abc; XSRF-TOKEN=tok=en"),
        )
    }

    @Test
    fun `last cookie wins when names repeat`() {
        assertEquals(mapOf("SUB" to "user"), parseCookieHeader("SUB=visitor; SUB=user"))
    }

    @Test
    fun `no cookies at all`() {
        assertEquals(emptyMap<String, String>(), parseCookieHeader(null))
        assertEquals(emptyMap<String, String>(), parseCookieHeader(""))
    }
}
