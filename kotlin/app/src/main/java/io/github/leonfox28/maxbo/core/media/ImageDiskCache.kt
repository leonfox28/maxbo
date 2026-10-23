package io.github.leonfox28.maxbo.core.media

import coil3.ImageLoader
import coil3.disk.DiskCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okio.FileSystem
import okio.Path

/**
 * Coil's disk cache for Weibo's pictures, whose limit can change while the app runs, as the setting
 * does.
 *
 * Coil fixes a cache's limit when it is made, so [resize] makes the cache again over the same
 * directory (closing the old one first: two must never share it) and trims it at once.
 */
class ImageDiskCache(override val directory: Path, maxSizeBytes: Long) : DiskCache {
    @Volatile private var cache: DiskCache = make(maxSizeBytes)

    private fun make(maxSizeBytes: Long) =
        DiskCache.Builder().directory(directory).maxSizeBytes(maxSizeBytes).build()

    override val size: Long
        get() = cache.size

    override val maxSize: Long
        get() = cache.maxSize

    override val fileSystem: FileSystem
        get() = cache.fileSystem

    override fun openSnapshot(key: String) = cache.openSnapshot(key)

    override fun openEditor(key: String) = cache.openEditor(key)

    override fun remove(key: String) = cache.remove(key)

    override fun clear() = cache.clear()

    override fun shutdown() = cache.shutdown()

    /** Keeps the pictures to [maxBytes], deleting the least recently used at once if over it. */
    @Synchronized
    fun resize(maxBytes: Long) {
        if (maxBytes == cache.maxSize) return
        cache.shutdown()
        cache = make(maxBytes)
        // Coil trims once an edit ends with the cache over its limit; an
        // empty one given up on is enough.
        cache.openEditor(TRIM_KEY)?.abort()
    }

    private companion object {
        const val TRIM_KEY = "maxbo-trim"
    }
}

/** The pictures kept on the device, as the settings page sees them; tests fake it. */
interface PictureCache {
    /** How much room the pictures take on disk. */
    suspend fun sizeBytes(): Long

    /** Deletes every picture kept, on disk and in memory. */
    suspend fun clear()

    /** Keeps the pictures to [maxBytes] on disk, deleting the least recently used if over it. */
    suspend fun setLimit(maxBytes: Long)
}

/** [disk], and the memory cache of [loader] (made on first use). */
class WeiboPictureCache(private val disk: ImageDiskCache, private val loader: () -> ImageLoader) :
    PictureCache {
    override suspend fun sizeBytes(): Long = withContext(Dispatchers.IO) { disk.size }

    override suspend fun clear() {
        withContext(Dispatchers.IO) { disk.clear() }
        loader().memoryCache?.clear()
    }

    override suspend fun setLimit(maxBytes: Long) =
        withContext(Dispatchers.IO) { disk.resize(maxBytes) }
}
