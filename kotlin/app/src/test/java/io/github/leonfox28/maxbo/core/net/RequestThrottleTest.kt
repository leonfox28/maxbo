package io.github.leonfox28.maxbo.core.net

import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class RequestThrottleTest {
    private fun TestScope.throttle() = RequestThrottle(now = { currentTime })

    @Test
    fun `spaces out request starts`() = runTest {
        val throttle = throttle()
        val started = mutableListOf<Long>()
        repeat(2) { launch { throttle.acquire().also { started += currentTime } } }

        advanceTimeBy(1_000)

        assertEquals(listOf(0L, 500L), started)
    }

    @Test
    fun `caps concurrency and hands freed slots to waiters in order`() = runTest {
        val throttle = throttle()
        val started = mutableListOf<Int>()
        for (i in 0 until 3) launch { throttle.acquire().also { started += i } }

        advanceTimeBy(5_000)
        assertEquals(listOf(0, 1), started)

        throttle.release()
        advanceTimeBy(1_000)
        assertEquals(listOf(0, 1, 2), started)
    }

    @Test
    fun `backs off exponentially and resets on success`() = runTest {
        val throttle = throttle()
        var startedAt: Long? = null

        throttle.backOff()
        launch { throttle.acquire().also { startedAt = currentTime } }
        advanceTimeBy(4_000)
        runCurrent()
        assertEquals(null, startedAt)
        advanceTimeBy(1_000)
        runCurrent()
        assertEquals(5_000L, startedAt)
        throttle.release()

        throttle.backOff()
        val second = currentTime
        startedAt = null
        launch { throttle.acquire().also { startedAt = currentTime } }
        advanceTimeBy(20_000)
        assertEquals(10_000L, startedAt!! - second)
        throttle.release()

        throttle.resetBackOff()
        throttle.backOff()
        val third = currentTime
        startedAt = null
        launch { throttle.acquire().also { startedAt = currentTime } }
        advanceTimeBy(20_000)
        assertEquals(5_000L, startedAt!! - third)
    }
}
