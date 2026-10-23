package io.github.leonfox28.maxbo.core.session

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RenewalTrackerTest {
    private val offered = mutableListOf<String?>()
    private val accepted = setOf("renewed")

    private fun sub(sub: String) = mapOf("weibo.com" to mapOf("SUB" to sub))

    private fun tracker(startSub: String? = "lapsed") =
        RenewalTracker(lapsedSub = "lapsed", startSub = startSub) { cookies ->
            val sub = cookies["weibo.com"]?.get("SUB")
            offered += sub
            sub in accepted
        }

    @Test
    fun `waits through pages with the lapsed session or none`() = runTest {
        val renewal = tracker(startSub = null)

        assertNull(renewal.pageLoaded(emptyMap()))
        assertNull(renewal.pageLoaded(sub("lapsed")))
        assertNull(renewal.pageLoaded(emptyMap()))
        assertEquals(emptyList<String?>(), offered)
    }

    @Test
    fun `takes the session Weibo renewed`() = runTest {
        val renewal = tracker()

        assertNull(renewal.pageLoaded(sub("lapsed")))
        assertEquals(true, renewal.pageLoaded(sub("renewed")))
        assertEquals(listOf("renewed"), offered)
    }

    @Test
    fun `takes a newer session the WebView already has`() = runTest {
        assertEquals(true, tracker(startSub = "renewed").pageLoaded(sub("renewed")))
    }

    @Test
    fun `gives up on a session Weibo set that isn't the user's`() = runTest {
        val renewal = tracker()

        // A visitor's, when there was nothing to renew.
        assertEquals(false, renewal.pageLoaded(sub("visitor")))
        assertEquals(listOf("visitor"), offered)
    }

    @Test
    fun `waits on when the WebView's own session has lapsed too`() = runTest {
        val renewal = tracker(startSub = "web-lapsed")

        assertNull(renewal.pageLoaded(sub("web-lapsed")))
        assertNull(renewal.pageLoaded(sub("web-lapsed")))
        assertEquals(true, renewal.pageLoaded(sub("renewed")))
        // Each session is offered once.
        assertEquals(listOf("web-lapsed", "renewed"), offered)
    }
}
