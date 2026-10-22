package io.github.leonfox28.maxbo.ui.shell

import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.printToString
import io.github.leonfox28.maxbo.MaxboApp
import io.github.leonfox28.maxbo.support.testGraph
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class HomeShellTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Before
    fun setUp() {
        compose.setContent { MaxboApp(testGraph()) }
    }

    private fun tab(label: String) = compose.onNodeWithContentDescription(label)

    @Test
    fun `opens on the home tab`() {
        tab("首页").assertIsSelected()
        compose.onNodeWithText("设置").assertDoesNotExist()
    }

    @Test
    fun `switches tabs from the pill`() {
        tab("我").performClick()
        tab("我").assertIsSelected()
        compose.onNodeWithText("设置").assertExists()

        tab("首页").performClick()
        tab("首页").assertIsSelected()
        compose.onNodeWithText("设置").assertDoesNotExist()
    }

    @Test
    fun `screen readers get one node per tab, with no tooltip of its own`() {
        tab("搜索").assert(SemanticsMatcher.expectValue(SemanticsProperties.Selected, false))
        val tree = compose.onRoot(useUnmergedTree = true).printToString()
        assertFalse(tree, tree.contains("OnLongClick"))
    }

    @Test
    fun `opens settings over the tabs and closes it again`() {
        tab("我").performClick()
        compose.onNodeWithText("设置").performClick()
        compose.onNodeWithContentDescription("返回").assertExists()
        tab("我").assertDoesNotExist()

        compose.onNodeWithContentDescription("返回").performClick()
        tab("我").assertIsSelected()
        compose.onNodeWithContentDescription("返回").assertDoesNotExist()
    }

    @Test
    fun `system back closes the page, back to the tab it was opened from`() {
        tab("我").performClick()
        compose.onNodeWithText("设置").performClick()
        compose.onNodeWithContentDescription("返回").assertExists()
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }

        tab("我").assertIsSelected()
        compose.onNode(hasText("设置")).assertExists()
    }
}
