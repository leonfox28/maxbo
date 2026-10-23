package io.github.leonfox28.maxbo.ui.settings

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import io.github.leonfox28.maxbo.AppGraph
import io.github.leonfox28.maxbo.core.settings.AppSettings
import io.github.leonfox28.maxbo.core.settings.TextSize
import io.github.leonfox28.maxbo.support.InMemorySettingsStorage
import io.github.leonfox28.maxbo.support.TestHost
import io.github.leonfox28.maxbo.support.testGraph
import io.github.leonfox28.maxbo.ui.navigation.Navigator
import io.github.leonfox28.maxbo.ui.navigation.Route
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class TextSizePageTest {
    @get:Rule val compose = createComposeRule()

    private val storage = InMemorySettingsStorage()
    private val navigator = Navigator().apply { push(Route.TextSize) }

    private lateinit var graph: AppGraph

    private fun showPage(size: TextSize = TextSize.Standard) {
        graph = testGraph(settings = AppSettings(textSize = size), settingsStorage = storage)
        compose.setContent { TestHost(graph, navigator) { TextSizePage() } }
    }

    private fun slider() = compose.onNode(hasProgress())

    private fun hasProgress() =
        SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo)

    private fun sliderValue() =
        slider().fetchSemanticsNode().config[SemanticsProperties.ProgressBarRangeInfo].current

    private fun slideTo(value: Float) {
        slider().performSemanticsAction(SemanticsActions.SetProgress) { it(value) }
        compose.waitForIdle()
    }

    private fun down() = compose.onNodeWithContentDescription("调小")

    private fun up() = compose.onNodeWithContentDescription("调大")

    @Test
    fun `shows samples over the current size`() {
        showPage(TextSize.Large)

        compose.onNode(hasText("拖动下面的滑块", substring = true)).assertExists()
        compose.onNodeWithText("「标准」就是系统设置里的字体大小。").assertExists()
        compose.onNodeWithText("大").assertExists()
        assertEquals(TextSize.Large.ordinal.toFloat(), sliderValue())
        // Read out by name, not as a number.
        assertEquals(
            "大",
            slider().fetchSemanticsNode().config[SemanticsProperties.StateDescription],
        )
    }

    @Test
    fun `two steps either way of standard, saved as chosen`() {
        showPage()
        assertEquals(2f, sliderValue())

        slideTo(4f)
        assertEquals(TextSize.ExtraLarge, storage.stored.textSize)
        compose.onNodeWithText("特大").assertExists()
        up().assertIsNotEnabled()

        slideTo(0f)
        assertEquals(TextSize.ExtraSmall, storage.stored.textSize)
        compose.onNodeWithText("特小").assertExists()
        down().assertIsNotEnabled()
    }

    @Test
    fun `the A buttons step down and up`() {
        showPage(TextSize.ExtraSmall)
        // Dimmed where there's no further step.
        down().assertIsNotEnabled()
        up().assertIsEnabled()

        up().performClick()
        compose.waitForIdle()
        assertEquals(TextSize.Small, storage.stored.textSize)
        down().assertIsEnabled()
        repeat(2) {
            up().performClick()
            compose.waitForIdle()
        }
        assertEquals(TextSize.Large, storage.stored.textSize)
        repeat(2) {
            down().performClick()
            compose.waitForIdle()
        }
        assertEquals(TextSize.Small, storage.stored.textSize)
        assertEquals(5, storage.saves)
    }

    @Test
    fun `the samples do nothing when tapped`() {
        showPage()

        compose.onNodeWithText("46").performClick()
        compose.onNodeWithText("maxbo").performClick()
        compose.waitForIdle()

        // A real like button would have counted it at once.
        compose.onNodeWithText("46").assertExists()
        assertTrue(graph.likes.changes.value.isEmpty())
        assertEquals(listOf(Route.Home, Route.TextSize), navigator.backStack.toList())
    }
}
