package io.github.leonfox28.maxbo.core.media

import okio.Path.Companion.toOkioPath
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ImageDiskCacheTest {
    @get:Rule val files = TemporaryFolder()

    private val cache by lazy { ImageDiskCache(files.root.toOkioPath(), maxSizeBytes = 10_000) }

    @After fun tearDown() = cache.shutdown()

    private fun write(key: String, bytes: Int) {
        val editor = cache.openEditor(key)!!
        cache.fileSystem.write(editor.data) { write(ByteArray(bytes)) }
        editor.commit()
    }

    private fun has(key: String) = cache.openSnapshot(key)?.use { true } ?: false

    /** Waits for Coil's cleanup, which runs in the background. */
    private fun waitUntil(condition: () -> Boolean) {
        val deadline = System.nanoTime() + 5_000_000_000
        while (!condition()) {
            check(System.nanoTime() < deadline) { "timed out" }
            Thread.sleep(10)
        }
    }

    @Test
    fun `a lower limit deletes the least recently used pictures at once`() {
        write("old", 400)
        write("mid", 400)
        write("new", 400)
        assertEquals(1200, cache.size)

        cache.resize(900)

        waitUntil { cache.size <= 900 }
        assertEquals(900, cache.maxSize)
        assertNull(cache.openSnapshot("old"))
        assertEquals(listOf(true, true), listOf(has("mid"), has("new")))
    }

    @Test
    fun `what fits stays, and new pictures are kept to the new limit`() {
        write("a", 100)

        cache.resize(100)
        // Cleanup, if any, runs in the background.
        Thread.sleep(100)
        assertEquals(true, has("a"))

        write("b", 50)

        waitUntil { cache.size <= 100 }
        assertNull(cache.openSnapshot("a"))
        assertEquals(true, has("b"))
    }
}
