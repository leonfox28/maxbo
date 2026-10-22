package io.github.leonfox28.maxbo.ui.shell

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import io.github.leonfox28.maxbo.support.AppHarness
import io.github.leonfox28.maxbo.support.FakeWeibo
import io.github.leonfox28.maxbo.ui.navigation.Route
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ComposeButtonTest {
    @get:Rule val compose = createComposeRule()

    private val web = FakeWeibo()
    private val app = AppHarness(compose, web)

    @After fun tearDown() = web.close()

    @Test
    fun `the home tab has a button to write a post`() {
        app.start()

        compose.onNodeWithContentDescription("发微博").performClick()
        compose.waitForIdle()

        assertEquals(Route.Compose, app.top)
    }

    @Test
    fun `the other tabs have none`() {
        app.start()

        compose.onNodeWithContentDescription("搜索").performClick()
        compose.waitForIdle()

        compose.onNodeWithContentDescription("发微博").assertDoesNotExist()
    }
}
