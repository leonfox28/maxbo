package io.github.leonfox28.maxbo.core.media

import io.github.leonfox28.maxbo.core.net.BROWSER_USER_AGENT
import io.github.leonfox28.maxbo.core.net.WeiboApiException
import io.github.leonfox28.maxbo.data.api.WeiboApi
import io.github.leonfox28.maxbo.data.model.VideoInfo
import java.time.Duration
import java.time.Instant
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/** Headers for Weibo's video CDN, which like its image hosts expects a weibo.com Referer. */
val VideoHttpHeaders = mapOf("Referer" to "https://weibo.com/", "User-Agent" to BROWSER_USER_AGENT)

/** When a signed media URL stops working, from its `Expires` parameter; null for unsigned URLs. */
fun signedUrlExpiry(url: String): Instant? =
    url.toHttpUrlOrNull()?.queryParameter("Expires")?.toLongOrNull()?.let(Instant::ofEpochSecond)

/**
 * A stream URL of [video] that stays valid for at least [margin] after [now], or null when they
 * have all expired or are about to.
 */
fun freshStreamUrl(
    video: VideoInfo?,
    now: Instant = Instant.now(),
    margin: Duration = Duration.ofMinutes(5),
): String? {
    val deadline = now + margin
    return video?.streamUrls?.firstOrNull { url ->
        val expiry = signedUrlExpiry(url)
        expiry == null || expiry.isAfter(deadline)
    }
}

/** Where to play a video from; [fetched] when the status was fetched again for it. */
data class VideoSource(val url: String, val fetched: Boolean)

/**
 * Where to play the video of status [id] from: [known]'s URL while it is fresh, or else (or when
 * [refresh] is set) one from re-fetching the status. [VideoSource.fetched] tells which, so a
 * rejected URL is refreshed only once. In a post mixing pictures and videos, [index] picks the
 * video as `Status.videoAt` does.
 */
suspend fun resolveVideoUrl(
    api: WeiboApi,
    id: String,
    index: Int? = null,
    known: VideoInfo? = null,
    refresh: Boolean = false,
    now: Instant = Instant.now(),
): VideoSource {
    if (!refresh)
        freshStreamUrl(known, now)?.let {
            return VideoSource(it, fetched = false)
        }
    val status = api.fetchStatus(id)
    val url =
        status.videoAt(index)?.streamUrls?.firstOrNull() ?: throw WeiboApiException("这条微博的视频暂时无法播放")
    return VideoSource(url, fetched = true)
}
