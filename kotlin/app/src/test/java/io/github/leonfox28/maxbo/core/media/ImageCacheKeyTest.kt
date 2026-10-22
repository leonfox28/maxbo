package io.github.leonfox28.maxbo.core.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class ImageCacheKeyTest {
    @Test
    fun `cache key ignores avatar signatures`() {
        val base = "https://tvax1.sinaimg.cn/crop.0.0.180.180.180/abc.jpg"
        assertEquals(
            imageCacheKey("$base?KID=imgbed,tva&Expires=1760000000&ssig=aaa"),
            imageCacheKey("$base?KID=imgbed,tva&Expires=1760099999&ssig=bbb"),
        )
        assertEquals(imageCacheKey(base), imageCacheKey("$base?Expires=1&ssig=a"))
    }

    @Test
    fun `cache key keeps other query parameters and the path`() {
        val a = "https://wx1.sinaimg.cn/wap360/abc.jpg"
        assertEquals(a, imageCacheKey(a))
        assertNotEquals(imageCacheKey("$a?v=2"), imageCacheKey("$a?v=3"))
        assertNotEquals(imageCacheKey(a), imageCacheKey("https://wx1.sinaimg.cn/orj960/abc.jpg"))
    }
}
