package io.github.leonfox28.maxbo.core.session

import io.github.leonfox28.maxbo.AppGraph
import io.github.leonfox28.maxbo.core.net.SessionExpiredException
import io.github.leonfox28.maxbo.core.net.WeiboApiException
import io.github.leonfox28.maxbo.data.model.Account
import io.github.leonfox28.maxbo.support.FakeSessionRenewer
import io.github.leonfox28.maxbo.support.FakeWebCookies
import io.github.leonfox28.maxbo.support.FakeWeibo
import io.github.leonfox28.maxbo.support.InMemorySessionStorage
import io.github.leonfox28.maxbo.support.homePage
import io.github.leonfox28.maxbo.support.json
import io.github.leonfox28.maxbo.support.testGraph
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonPrimitive
import mockwebserver3.MockResponse
import mockwebserver3.RecordedRequest
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

private val webCookies = mapOf("weibo.com" to mapOf("SUB" to "user-sub", "XSRF-TOKEN" to "token"))
private val signedIn = StoredSession(Account(uid = "42"), webCookies)

/**
 * Answers like weibo.com once the session's `SUB` lapsed: requests with it are rejected, those with
 * [renewedSub] get `{"ok":1}`.
 */
private fun lapsed(request: RecordedRequest, renewedSub: String = "renewed-sub"): MockResponse =
    when {
        request.url.encodedPath == "/" -> homePage()
        request.headers["Cookie"].orEmpty().contains("SUB=$renewedSub") -> json("""{"ok":1}""")
        else -> json("""{"ok":-100,"url":"https://weibo.com/login.php"}""")
    }

@RunWith(RobolectricTestRunner::class)
class SessionControllerTest {
    private val web = FakeWeibo()
    private val storage = InMemorySessionStorage()
    private val bridge = FakeWebCookies()
    private val renewer = FakeSessionRenewer()

    init {
        web.respond = { if (it.url.encodedPath == "/") homePage() else json("""{"ok":1}""") }
    }

    @After fun tearDown() = web.close()

    private fun TestScope.graph(restored: StoredSession? = null): AppGraph =
        testGraph(
            restored = restored,
            sessionStorage = storage,
            webCookies = bridge,
            renewer = renewer,
            web = web,
            scope = backgroundScope,
        )

    private suspend inline fun <reified T : Throwable> assertFails(block: () -> Unit): T {
        try {
            block()
        } catch (e: Throwable) {
            if (e is T) return e
            throw e
        }
        fail("expected ${T::class.simpleName}")
        error("unreachable")
    }

    @Test
    fun `starts signed out, or signed in with a restored session`() = runTest {
        assertEquals(SessionState.SignedOut(), graph().session.state.value)

        val restored = graph(restored = signedIn).session
        assertEquals(SessionState.SignedIn(Account("42")), restored.state.value)
        assertEquals("user-sub", restored.jar.value("weibo.com", "SUB"))
    }

    @Test
    fun `completeLogin verifies, then adopts and persists the cookies`() = runTest {
        val session = graph().session

        session.completeLogin(webCookies)

        assertEquals("42", (session.state.value as SessionState.SignedIn).account.uid)
        assertEquals("user-sub", session.jar.value("weibo.com", "SUB"))
        assertEquals("42", storage.stored?.account?.uid)
        assertEquals(webCookies, storage.stored?.cookies)
    }

    @Test
    fun `completeLogin rejects visitor cookies and keeps nothing`() = runTest {
        web.respond = { homePage(uid = 0) }
        val session = graph().session

        assertFails<SessionExpiredException> { session.completeLogin(webCookies) }

        assertEquals(SessionState.SignedOut(), session.state.value)
        assertTrue(session.jar.isEmpty)
        assertNull(storage.stored)
    }

    @Test
    fun `cookies rotated by the server are persisted`() = runTest {
        val session = graph(restored = signedIn).session

        session.jar.applySetCookies("https://weibo.com/".toHttpUrl(), listOf("XSRF-TOKEN=new"))
        runCurrent()

        assertEquals("new", storage.stored?.cookies?.get("weibo.com")?.get("XSRF-TOKEN"))
    }

    @Test
    fun `logout clears memory, storage and the WebView`() = runTest {
        val session = graph().session
        session.completeLogin(webCookies)

        session.logout()

        assertEquals(SessionState.SignedOut(expired = false), session.state.value)
        assertTrue(session.jar.isEmpty)
        assertNull(storage.stored)
        assertTrue(bridge.cleared)
    }

    @Test
    fun `a session Weibo rejects and won't renew expires`() = runTest {
        storage.stored = signedIn
        val graph = graph(restored = signedIn)
        web.respond = { json("""{"ok":-100,"url":"https://weibo.com/login.php"}""") }

        assertFails<SessionExpiredException> { graph.api.getJson("https://weibo.com/ajax/feed/x") }
        runCurrent()

        assertEquals(1, renewer.runs)
        assertEquals(SessionState.SignedOut(expired = true), graph.session.state.value)
        assertTrue(graph.session.jar.isEmpty)
        assertNull(storage.stored)
        // The WebView keeps its cookies so re-login can be one tap.
        assertFalse(bridge.cleared)
    }

    @Test
    fun `renewal - a renewed session takes over and the read goes again`() = runTest {
        val graph = graph(restored = signedIn)
        val before = graph.session.state.value
        renewer.cookies = mapOf("weibo.com" to mapOf("SUB" to "renewed-sub"))
        web.respond = { lapsed(it) }

        val json = graph.api.getJson("https://weibo.com/ajax/feed/x")
        runCurrent()

        assertEquals(JsonPrimitive(1), json["ok"])
        // Still the same session, so nothing on screen reloads.
        assertSame(before, graph.session.state.value)
        assertEquals("renewed-sub", graph.session.jar.value("weibo.com", "SUB"))
        assertEquals("renewed-sub", storage.stored?.cookies?.get("weibo.com")?.get("SUB"))
    }

    @Test
    fun `renewal - a write isn't sent again`() = runTest {
        val graph = graph(restored = signedIn)
        renewer.cookies = mapOf("weibo.com" to mapOf("SUB" to "renewed-sub"))
        web.respond = { lapsed(it) }

        val e =
            assertFails<WeiboApiException> {
                graph.api.postForm(
                    "https://weibo.com/ajax/statuses/update",
                    mapOf("content" to "x"),
                )
            }

        assertEquals("登录状态已更新，请再试一次", e.message)
        assertEquals(1, web.requests.count { it.method == "POST" })
        assertTrue(graph.session.state.value is SessionState.SignedIn)
        assertEquals("renewed-sub", graph.session.jar.value("weibo.com", "SUB"))
    }

    @Test
    fun `renewal - a visitor or another account isn't taken`() = runTest {
        for (uid in listOf(0, 43)) {
            val graph = graph(restored = signedIn)
            renewer.cookies = mapOf("weibo.com" to mapOf("SUB" to "renewed-sub"))
            web.respond = { if (it.url.encodedPath == "/") homePage(uid) else lapsed(it) }

            assertFails<SessionExpiredException> {
                graph.api.getJson("https://weibo.com/ajax/feed/x")
            }

            assertTrue("uid $uid", graph.session.state.value is SessionState.SignedOut)
            assertTrue(graph.session.jar.isEmpty)
        }
    }

    @Test
    fun `renewal - rejections that come in together share one renewal`() = runTest {
        val graph = graph(restored = signedIn)
        renewer.cookies = mapOf("weibo.com" to mapOf("SUB" to "renewed-sub"))
        web.respond = { lapsed(it) }

        val answers =
            listOf(
                    async { graph.api.getJson("https://weibo.com/ajax/feed/x") },
                    async { graph.api.getJson("https://weibo.com/ajax/feed/y") },
                )
                .awaitAll()

        assertEquals(listOf(JsonPrimitive(1), JsonPrimitive(1)), answers.map { it["ok"] })
        assertEquals(1, renewer.runs)
    }

    @Test
    fun `renewal - one rejected after its session was renewed just goes again`() = runTest {
        val graph = graph(restored = signedIn)
        renewer.cookies = mapOf("weibo.com" to mapOf("SUB" to "renewed-sub"))
        val slowArrived = CountDownLatch(1)
        val slowGoes = CountDownLatch(1)
        web.respond = {
            if (it.url.encodedPath == "/ajax/slow" && slowGoes.count > 0) {
                slowArrived.countDown()
                slowGoes.await(10, TimeUnit.SECONDS)
            }
            lapsed(it)
        }

        val slowAnswer = async { graph.api.getJson("https://weibo.com/ajax/slow") }
        withContext(Dispatchers.IO) { slowArrived.await(10, TimeUnit.SECONDS) }
        graph.api.getJson("https://weibo.com/ajax/feed/x")
        slowGoes.countDown()

        assertEquals(JsonPrimitive(1), slowAnswer.await()["ok"])
        assertEquals(1, renewer.runs)
    }
}
