package io.github.leonfox28.maxbo.ui.profile

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.github.leonfox28.maxbo.MaxboApp
import io.github.leonfox28.maxbo.core.session.SessionState
import io.github.leonfox28.maxbo.support.FakeWebCookies
import io.github.leonfox28.maxbo.support.InMemorySessionStorage
import io.github.leonfox28.maxbo.support.signedIn
import io.github.leonfox28.maxbo.support.testGraph
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MePageTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun `asks visitors to log in, with settings still at hand`() {
        compose.setContent { MaxboApp(testGraph()) }
        compose.onNodeWithContentDescription("我").performClick()

        compose.onNodeWithText("登录后即可浏览关注的人的微博").assertExists()
        compose.onNodeWithText("登录微博").assertExists()
        compose.onNodeWithText("设置").assertExists()
        compose.onNodeWithText("退出登录").assertDoesNotExist()
    }

    @Test
    fun `says when the session expired`() {
        val graph = testGraph(restored = signedIn)
        graph.session.expire()
        compose.setContent { MaxboApp(graph) }
        compose.onNodeWithContentDescription("我").performClick()

        compose.onNodeWithText("登录已过期，请重新登录").assertExists()
    }

    @Test
    fun `shows the account and signs out once confirmed`() {
        val storage = InMemorySessionStorage(signedIn)
        val webCookies = FakeWebCookies()
        val graph =
            testGraph(restored = signedIn, sessionStorage = storage, webCookies = webCookies)
        compose.setContent { MaxboApp(graph) }
        compose.onNodeWithContentDescription("我").performClick()

        compose.onNodeWithText("微博用户").assertExists()
        compose.onNodeWithText("UID 7").assertExists()

        compose.onNodeWithText("退出登录").performClick()
        compose.onNodeWithText("将清除本机保存的微博登录状态。").assertExists()
        compose.onNodeWithText("取消").performClick()
        assertTrue(graph.session.state.value is SessionState.SignedIn)

        compose.onNodeWithText("退出登录").performClick()
        compose.onNodeWithText("退出").performClick()
        compose.waitForIdle()

        assertEquals(SessionState.SignedOut(), graph.session.state.value)
        assertNull(storage.stored)
        assertTrue(webCookies.cleared)
        compose.onNodeWithText("登录微博").assertExists()
    }

    @Test
    fun `the login button opens Weibo's login page`() {
        compose.setContent { MaxboApp(testGraph()) }

        compose.onNodeWithText("登录微博").performClick()

        compose.onNodeWithText("下方是微博官方登录页面。maxbo 不会接触你的密码，登录状态只保存在本机。").assertExists()
        compose.onNodeWithContentDescription("关闭").performClick()
        compose.onNodeWithText("登录后即可浏览关注的人的微博").assertExists()
    }
}
