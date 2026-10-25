package io.github.leonfox28.maxbo.core.media

import io.github.leonfox28.maxbo.core.net.WeiboApiException
import io.github.leonfox28.maxbo.data.model.PicInfo
import io.github.leonfox28.maxbo.data.model.Status
import io.github.leonfox28.maxbo.data.model.VideoInfo
import io.github.leonfox28.maxbo.support.FakeWeibo
import io.github.leonfox28.maxbo.support.json
import io.github.leonfox28.maxbo.support.mixedMediaFields
import io.github.leonfox28.maxbo.support.picJson
import io.github.leonfox28.maxbo.support.statusJson
import io.github.leonfox28.maxbo.support.videoPageInfo
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class VideoSourceTest {
    // Expires=2000000000 is 2033-05-18T03:33:20Z.
    private val expiry = Instant.parse("2033-05-18T03:33:20Z")
    private val video = Status.tryFromJson(statusJson(pageInfo = videoPageInfo()))!!.video
    private val web = FakeWeibo()

    init {
        // A re-fetched status carries URLs signed for later.
        web.respond = {
            json(statusJson(pageInfo = videoPageInfo(expires = 2100000000)).toString())
        }
    }

    @After fun tearDown() = web.close()

    @Test
    fun `reads the expiry of signed URLs`() {
        assertEquals(
            expiry,
            signedUrlExpiry("https://f.video.weibocdn.com/a.mp4?Expires=2000000000"),
        )
        assertNull(signedUrlExpiry("https://f.video.weibocdn.com/a.mp4"))
    }

    @Test
    fun `a stream URL is fresh until a few minutes before it expires`() {
        val early = expiry - Duration.ofHours(1)
        assertTrue(freshStreamUrl(video, now = early)!!.contains("mp4_720p"))

        val late = expiry - Duration.ofMinutes(2)
        assertNull(freshStreamUrl(video, now = late))
        assertNull(freshStreamUrl(null, now = early))
        assertEquals(
            "https://f.video.weibocdn.com/a.mp4",
            freshStreamUrl(
                VideoInfo(streamUrls = listOf("https://f.video.weibocdn.com/a.mp4")),
                now = late,
            ),
        )
    }

    @Test
    fun `uses the known URL while it is fresh, without a request`() = runTest {
        val source =
            resolveVideoUrl(web.api(), "1", known = video, now = expiry - Duration.ofHours(1))

        assertFalse(source.fetched)
        assertTrue(source.url.contains("Expires=2000000000"))
        assertTrue(web.requests.isEmpty())
    }

    @Test
    fun `re-fetches the status once the known URL has expired`() = runTest {
        val source = resolveVideoUrl(web.api(), "5000000000000001", known = video, now = expiry)

        assertTrue(source.fetched)
        assertTrue(source.url.contains("Expires=2100000000"))
        val url = web.requests.single().url
        assertEquals("/ajax/statuses/show", url.encodedPath)
        assertEquals("5000000000000001", url.queryParameter("id"))
    }

    @Test
    fun `re-fetches when asked to, or when nothing is known`() = runTest {
        val api = web.api()
        val early = expiry - Duration.ofHours(1)

        resolveVideoUrl(api, "1", known = video, refresh = true, now = early)
        resolveVideoUrl(api, "1", now = early)

        assertEquals(2, web.requests.size)
    }

    @Test
    fun `picks the same video of a post mixing videos`() = runTest {
        web.respond = {
            json(
                statusJson(
                        extra =
                            mixedMediaFields(
                                listOf(
                                    videoPageInfo(name = "first", expires = 2100000000),
                                    picJson("p1"),
                                    videoPageInfo(name = "second", expires = 2100000000),
                                )
                            )
                    )
                    .toString()
            )
        }

        assertTrue(resolveVideoUrl(web.api(), "1", index = 1).url.contains("/second/"))
    }

    @Test
    fun `a live photo uses its own URL while it is fresh, without a request`() = runTest {
        val pic = livePhoto(expires = 2000000000)

        val source = resolveLivePhotoUrl(web.api(), pic, "1", now = expiry - Duration.ofHours(1))

        assertFalse(source.fetched)
        assertEquals(pic.liveVideo, source.url)
        assertTrue(web.requests.isEmpty())
    }

    @Test
    fun `an expired live photo takes the same picture's URL from its status fetched again`() =
        runTest {
            web.respond = {
                json(
                    statusJson(
                            pics =
                                listOf(
                                    picJson("other", type = "livephoto", expires = 2100000000),
                                    picJson("p1", type = "livephoto", expires = 2100000000),
                                )
                        )
                        .toString()
                )
            }

            val source =
                resolveLivePhotoUrl(web.api(), livePhoto(), "5000000000000001", now = expiry)

            assertTrue(source.fetched)
            assertTrue(source.url.contains("p1.mov&Expires=2100000000"))
            val url = web.requests.single().url
            assertEquals("/ajax/statuses/show", url.encodedPath)
            assertEquals("5000000000000001", url.queryParameter("id"))
        }

    @Test
    fun `finds a live photo among a post's mixed media`() = runTest {
        web.respond = {
            json(
                statusJson(
                        extra =
                            mixedMediaFields(
                                listOf(
                                    videoPageInfo(),
                                    picJson("p1", type = "livephoto", expires = 2100000000),
                                )
                            )
                    )
                    .toString()
            )
        }

        val source = resolveLivePhotoUrl(web.api(), livePhoto(), "1", refresh = true)

        assertTrue(source.url.contains("p1.mov&Expires=2100000000"))
    }

    @Test
    fun `a live photo of no known status keeps its own URL`() = runTest {
        val source = resolveLivePhotoUrl(web.api(), livePhoto(), null, refresh = true, now = expiry)

        assertTrue(source.url.contains("Expires=2000000000"))
        assertTrue(web.requests.isEmpty())
    }

    @Test
    fun `reports a live photo its status no longer has`() = runTest {
        web.respond = { json(statusJson(pics = listOf(picJson("p1"))).toString()) }

        val failure = runCatching {
            resolveLivePhotoUrl(web.api(), livePhoto(), "1", now = expiry)
        }
            .exceptionOrNull()
        assertTrue(failure is WeiboApiException)
    }

    private fun livePhoto(expires: Long = 2000000000) =
        PicInfo.tryFromJson(picJson("p1", type = "livephoto", expires = expires))!!

    @Test
    fun `reports a status that has no playable video`() = runTest {
        web.respond = { json(statusJson().toString()) }

        val failure = runCatching { resolveVideoUrl(web.api(), "1") }.exceptionOrNull()
        assertTrue(failure is WeiboApiException)
    }
}
