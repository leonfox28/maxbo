package io.github.leonfox28.maxbo.core.media

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import coil3.request.ImageRequest
import io.github.leonfox28.maxbo.support.FakeWeibo
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import okio.Buffer
import okio.ByteString.Companion.decodeHex
import okio.Path.Companion.toOkioPath
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class WeiboImageLoaderTest {
    @get:Rule val files = TemporaryFolder()

    private val web = FakeWeibo()
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val cache by lazy { ImageDiskCache(files.root.toOkioPath(), maxSizeBytes = 100_000) }
    private val loader by lazy { weiboImageLoader(context, web.client, cache) }

    init {
        web.respond = {
            MockResponse.Builder()
                .addHeader("Content-Type", "image/png")
                .body(Buffer().write(OnePixelPng))
                .build()
        }
    }

    @After
    fun tearDown() {
        loader.shutdown()
        web.close()
    }

    private fun load(url: String) = runBlocking {
        loader.execute(ImageRequest.Builder(context).data(url).build())
    }

    @Test
    fun `pictures go with weibo com as their Referer`() {
        load("https://wx1.sinaimg.cn/orj360/a.jpg")
        assertEquals("https://weibo.com/", web.requests.single().headers["Referer"])
    }

    @Test
    fun `plain http pictures are fetched over https`() {
        // As a comment's pictures come; Android refuses cleartext.
        load("http://wx3.sinaimg.cn/bmiddle/a.jpg")
        val request = web.requests.single()
        assertEquals("/bmiddle/a.jpg", request.url.encodedPath)
        assertEquals("wx3.sinaimg.cn", request.headers["Host"])
    }
}

/** A 1×1 PNG. */
private val OnePixelPng =
    ("89504e470d0a1a0a0000000d49484452000000010000000108060000001f15c489" +
            "0000000d49444154789c63000100000500010d0a2db40000000049454e44ae426082")
        .decodeHex()
