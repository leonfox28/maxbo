package io.github.leonfox28.maxbo.ui.profile

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performClick
import io.github.leonfox28.maxbo.data.model.WeiboUser
import io.github.leonfox28.maxbo.support.AppHarness
import io.github.leonfox28.maxbo.support.FakeWeibo
import io.github.leonfox28.maxbo.support.json
import io.github.leonfox28.maxbo.support.obj
import io.github.leonfox28.maxbo.support.profileDataJson
import io.github.leonfox28.maxbo.support.statusJson
import io.github.leonfox28.maxbo.support.userJson
import io.github.leonfox28.maxbo.support.userStatusesJson
import io.github.leonfox28.maxbo.ui.navigation.Route
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import mockwebserver3.MockResponse
import mockwebserver3.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// The app bar hiding on scroll is BarsOnScroll's, tested with the timeline.
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp")
class ProfilePageTest {
    @get:Rule val compose = createComposeRule()

    private val web = FakeWeibo()
    private val app = AppHarness(compose, web)

    private fun respond(request: RecordedRequest): MockResponse =
        when (request.url.encodedPath) {
            "/ajax/profile/info" ->
                json(
                    obj(
                            "ok" to 1,
                            "data" to profileDataJson(user = userJson(id = 1001, name = "示例用户")),
                        )
                        .toString()
                )
            "/ajax/statuses/mymblog" ->
                json(
                    userStatusesJson(
                            listOf(
                                statusJson(id = 1, text = "置顶的微博", extra = mapOf("isTop" to 1)),
                                statusJson(id = 2, text = "普通的微博"),
                            ),
                            sinceId = "",
                        )
                        .toString()
                )
            else -> MockResponse(code = 404)
        }

    init {
        web.respond = ::respond
    }

    @After fun tearDown() = web.close()

    private fun requestsTo(path: String) =
        web.requests.filter { it.url.encodedPath == path }.map { it.url }

    @Test
    fun `by uid - shows who was tapped, then the profile and statuses`() {
        val held = CountDownLatch(1)
        web.respond = {
            held.await(10, TimeUnit.SECONDS)
            respond(it)
        }
        app.start(Route.Profile("1001", WeiboUser("1001", "示例用户")))
        // App bar and header, before anything arrives.
        assertEquals(2, app.count("示例用户"))
        held.countDown()

        app.waitFor("这是一段简介")
        app.waitFor("普通的微博")
        app.text("上海 浦东新区").assertExists()
        app.text("已关注").assertExists()
        app.text("12.3万 粉丝", substring = true).assertExists()
        app.text("置顶的微博").assertExists()
        app.text("置顶").assertExists()
        app.text("没有更多了").assertExists()
        // Both load at once, each once.
        assertEquals(
            listOf("1001"),
            requestsTo("/ajax/profile/info").map { it.queryParameter("uid") },
        )
        assertEquals("1001", requestsTo("/ajax/statuses/mymblog").single().queryParameter("uid"))
    }

    @Test
    fun `by name - looks the user up, then lists their statuses`() {
        app.start(Route.ProfileByName("示例用户"))
        app.waitFor("普通的微博")

        assertEquals(listOf("/ajax/profile/info", "/ajax/statuses/mymblog"), app.paths())
        assertEquals(
            "示例用户",
            requestsTo("/ajax/profile/info").single().queryParameter("screen_name"),
        )
        assertEquals("1001", requestsTo("/ajax/statuses/mymblog").single().queryParameter("uid"))
    }

    @Test
    fun `an unknown name says so and loads nothing more`() {
        web.respond = { json("""{"ok":1,"user":{}}""") }
        app.start(Route.ProfileByName("没有这个人"))

        app.waitFor("找不到这个用户")
        app.text("重试").assertExists()
        assertTrue(requestsTo("/ajax/statuses/mymblog").isEmpty())
    }

    @Test
    fun `a failed profile keeps the statuses, with a retry`() {
        web.respond = {
            if (it.url.encodedPath == "/ajax/profile/info")
                json("""{"ok":0,"message":"服务繁忙"}""", status = 500)
            else respond(it)
        }
        app.start(Route.Profile("1001"))
        app.waitFor("服务繁忙", substring = true)
        app.waitFor("普通的微博")

        web.respond = ::respond
        app.text("重试").performClick()
        app.waitFor("这是一段简介")
        // The statuses were fine and stay as they are.
        assertEquals(1, requestsTo("/ajax/statuses/mymblog").size)
    }
}
