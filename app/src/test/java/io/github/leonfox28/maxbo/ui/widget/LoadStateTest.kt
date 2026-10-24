package io.github.leonfox28.maxbo.ui.widget

import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.unit.dp
import io.github.leonfox28.maxbo.core.net.CaptchaRequiredException
import io.github.leonfox28.maxbo.core.net.WeiboApiException
import io.github.leonfox28.maxbo.support.TestHost
import io.github.leonfox28.maxbo.ui.navigation.Navigator
import io.github.leonfox28.maxbo.ui.navigation.Route
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class LoadStateTest {
    @get:Rule val compose = createComposeRule()

    private val navigator = Navigator()
    private val captcha = CaptchaRequiredException("https://m.weibo.cn/captcha?x=1".toHttpUrl())

    @Test
    fun `ErrorRetry retries`() {
        var retries = 0
        compose.setContent {
            TestHost(navigator = navigator) { ErrorRetry(WeiboApiException("服务繁忙"), { retries++ }) }
        }

        compose.onNodeWithText("服务繁忙").assertExists()
        compose.onNodeWithText("重试").performClick()
        assertEquals(1, retries)
    }

    @Test
    fun `ErrorRetry lets the user take a security check, then retries`() {
        var retries = 0
        compose.setContent {
            TestHost(navigator = navigator) { ErrorRetry(captcha, { retries++ }) }
        }

        compose.onNodeWithText("需要先完成微博的安全验证").assertExists()
        compose.onNodeWithText("重试").assertDoesNotExist()
        compose.onNodeWithText("去验证").performClick()
        assertEquals(
            "https://m.weibo.cn/captcha?x=1",
            (navigator.backStack.last() as Route.Web).url,
        )
        assertEquals(0, retries)

        navigator.pop()
        assertEquals(1, retries)
    }

    @Test
    fun `LoadMoreFooter offers the security check too`() {
        var loads = 0
        compose.setContent {
            TestHost(navigator = navigator) {
                LoadMoreFooter(
                    loading = false,
                    hasMore = true,
                    error = captcha,
                    onLoadMore = { loads++ },
                )
            }
        }

        compose.onNodeWithText("去验证").performClick()
        navigator.pop()
        assertEquals(1, loads)
    }

    @Test
    fun `LoadMoreFooter says when nothing is left`() {
        compose.setContent {
            TestHost {
                LoadMoreFooter(loading = false, hasMore = false, error = null, onLoadMore = {})
            }
        }

        compose.onNodeWithText("没有更多了").assertExists()
    }

    @Test
    fun `KeepAtTop shows items added above the top, but not to a list scrolled down`() {
        var items by mutableStateOf((1..30).map { "第${it}条" })
        lateinit var state: LazyListState
        compose.setContent {
            state = rememberLazyListState()
            Feed(state, items)
        }

        items = listOf("第0条") + items
        compose.onNodeWithText("第0条").assertExists()
        assertEquals(0, state.firstVisibleItemIndex)

        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(10)
        items = listOf("第-1条") + items
        compose.waitForIdle()
        assertEquals(11, state.firstVisibleItemIndex)
        compose.onNodeWithText("第10条").assertExists()
    }
}

/** [items] in a list, given to it by recomposition as the app's lists are. */
@Composable
private fun Feed(state: LazyListState, items: List<String>) {
    KeepAtTop(state, items)
    LazyColumn(state = state) { items(items, key = { it }) { Text(it, Modifier.height(100.dp)) } }
}
