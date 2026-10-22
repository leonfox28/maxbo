package io.github.leonfox28.maxbo.core.net

import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Semaphore

/**
 * Caps concurrent requests and spaces out their starts, so the app never sends Weibo a burst; backs
 * off further when Weibo signals rate limiting.
 *
 * @param now the time in milliseconds, on a clock that never goes back.
 */
class RequestThrottle(
    val maxConcurrent: Int = 2,
    val minIntervalMs: Long = 500,
    private val now: () -> Long = { System.nanoTime() / 1_000_000 },
) {
    // Fair: freed slots go to waiters in the order they came.
    private val slots = Semaphore(maxConcurrent)
    private var nextStart: Long? = null
    private var backOffUntil: Long? = null
    private var backOffMs = 0L

    /**
     * Waits for a free slot and the next allowed start time.
     *
     * Every call that returns must be paired with exactly one [release].
     */
    suspend fun acquire() {
        slots.acquire()
        try {
            val wait =
                synchronized(this) {
                    val now = now()
                    var start = now
                    nextStart?.let { start = max(start, it) }
                    backOffUntil?.let { start = max(start, it) }
                    nextStart = start + minIntervalMs
                    start - now
                }
            if (wait > 0) delay(wait)
        } catch (e: Throwable) {
            slots.release()
            throw e
        }
    }

    fun release() {
        slots.release()
    }

    /** Delays later requests, doubling the pause on each consecutive call. */
    fun backOff() {
        synchronized(this) {
            backOffMs =
                if (backOffMs == 0L) FIRST_BACK_OFF_MS else min(backOffMs * 2, MAX_BACK_OFF_MS)
            backOffUntil = now() + backOffMs
        }
    }

    fun resetBackOff() {
        synchronized(this) { backOffMs = 0 }
    }

    private companion object {
        const val FIRST_BACK_OFF_MS = 5_000L
        const val MAX_BACK_OFF_MS = 60_000L
    }
}
