package io.github.leonfox28.maxbo.ui.compose

import io.github.leonfox28.maxbo.core.net.WeiboApiException
import io.github.leonfox28.maxbo.core.paging.ListLoad
import io.github.leonfox28.maxbo.data.model.StatusVisibility
import io.github.leonfox28.maxbo.support.FakeWeibo
import io.github.leonfox28.maxbo.support.feedJson
import io.github.leonfox28.maxbo.support.form
import io.github.leonfox28.maxbo.support.html
import io.github.leonfox28.maxbo.support.json
import io.github.leonfox28.maxbo.support.obj
import io.github.leonfox28.maxbo.support.signedIn
import io.github.leonfox28.maxbo.support.statusJson
import io.github.leonfox28.maxbo.support.testGraph
import io.github.leonfox28.maxbo.support.userJson
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ComposeDraftTest {
    @get:Rule val files = TemporaryFolder()

    private val web = FakeWeibo()
    private val graph = testGraph(restored = signedIn, web = web)
    private val draft = graph.composeDraft
    private val uploads = AtomicInteger()
    @Volatile private var failPost = false

    init {
        web.respond = { request ->
            when (request.url.encodedPath) {
                "/ajax/feed/friendstimeline" ->
                    json(feedJson(listOf(statusJson(id = 1))).toString())
                "/interface/pic_upload.php" ->
                    html(
                        """{"code":"A00006","data":{"pics":{"pic_1":""" +
                            """{"pid":"pid${uploads.incrementAndGet()}"}}}}"""
                    )
                "/ajax/statuses/update" ->
                    if (failPost) json("""{"ok":0,"msg":"发布太频繁"}""")
                    else
                        json(
                            obj(
                                    "ok" to 1,
                                    "data" to
                                        statusJson(id = 9, text = "新微博", user = userJson(id = 7)),
                                )
                                .toString()
                        )
                else -> MockResponse(code = 404)
            }
        }
    }

    @After fun tearDown() = web.close()

    private fun paths() = web.requests.map { it.url.encodedPath }

    private fun picture(name: String) =
        files.newFile(name).apply { writeText("GIF89a $name") }.toURI().toString()

    @Test
    fun `keeps at most nine pictures`() {
        assertEquals(0, draft.addPictures((0 until 7).map { "p$it" }))
        assertEquals(1, draft.addPictures(listOf("a", "b", "c")))
        assertEquals(
            (0 until 7).map { "p$it" } + listOf("a", "b"),
            draft.draft.value.pictures.map { it.uri },
        )

        draft.removePicture(0)
        assertEquals(8, draft.draft.value.pictures.size)
    }

    @Test
    fun `uploads the pictures in order, posts, and shows the post atop the timeline`() = runTest {
        graph.timeline.start()
        graph.timeline.state.first { it is ListLoad.Ready }
        draft.setText("  新微博 ")
        draft.setVisibility(StatusVisibility.Friends)
        draft.addPictures(listOf(picture("a"), picture("b")))
        val uploading = mutableListOf<Int>()

        draft.send(onUpload = { uploading += it })

        assertEquals(listOf(0, 1), uploading)
        assertEquals(
            listOf(
                "/interface/pic_upload.php",
                "/interface/pic_upload.php",
                "/ajax/statuses/update",
            ),
            paths().drop(1),
        )
        assertEquals("GIF89a a", web.requests[1].body?.utf8())
        assertEquals("GIF89a b", web.requests[2].body?.utf8())
        assertEquals(
            mapOf(
                "content" to "新微博",
                "pic_id" to """[{"type":"pic","pid":"pid1"},{"type":"pic","pid":"pid2"}]""",
                "visible" to "6",
            ),
            web.requests.last().form,
        )
        assertEquals(ComposeDraft(), draft.draft.value)
        val timeline = graph.timeline.state.value as ListLoad.Ready
        assertEquals(listOf("9", "1"), timeline.list.items.map { it.id })
    }

    @Test
    fun `sending again after a failure does not upload pictures again`() = runTest {
        draft.setText("新微博")
        draft.addPictures(listOf(picture("a")))
        failPost = true

        val error = runCatching { draft.send() }.exceptionOrNull()
        assertTrue("got $error", error is WeiboApiException)
        assertEquals("新微博", draft.draft.value.text)
        assertEquals("pid1", draft.draft.value.pictures.single().pid)

        failPost = false
        draft.send()

        assertEquals(1, uploads.get())
        assertEquals(2, paths().count { it == "/ajax/statuses/update" })
        assertTrue(web.requests.last().form["pic_id"].orEmpty().contains("pid1"))
    }

    @Test
    fun `the timeline is left alone when it is not open`() = runTest {
        draft.setText("新微博")

        draft.send()

        assertEquals(listOf("/ajax/statuses/update"), paths())
    }

    @Test
    fun `another account starts over`() = runTest {
        draft.setText("草稿")

        graph.session.logout()

        assertEquals(ComposeDraft(), draft.draft.value)
    }
}
