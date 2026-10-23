package io.github.leonfox28.maxbo.data.api

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PageConfigTest {
    @Test
    fun `extracts the CONFIG embedded in a script`() {
        val html =
            """
            <html><head><script>
              var v = '2026.10.12.1' }; try{window.${'$'}CONFIG = {"serverTime":1,"user":{"idstr":"42","screen_name":"a{b}\"c"},"uid":42}; }catch(e){}
              window.other = {"x":1};
            </script></head></html>
            """

        val config = extractPageConfig(html)

        assertEquals(JsonPrimitive(42), config?.get("uid"))
        assertEquals(JsonPrimitive("a{b}\"c"), (config?.get("user") as JsonObject)["screen_name"])
    }

    @Test
    fun `returns null when absent, unterminated or not JSON`() {
        assertNull(extractPageConfig("<html></html>"))
        assertNull(extractPageConfig("window.\$CONFIG = {\"a\":1"))
        assertNull(extractPageConfig("window.\$CONFIG = {a: 'js-only'};"))
    }
}
