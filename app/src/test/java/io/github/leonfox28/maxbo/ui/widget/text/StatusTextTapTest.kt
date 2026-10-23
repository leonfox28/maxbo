package io.github.leonfox28.maxbo.ui.widget.text

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class StatusTextTapTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun `a tap just below a link is not the link's`() {
        val links = mutableListOf<StatusLink>()
        var threads = 0
        compose.setContent {
            // As a thread preview: a reply that starts with a mention, then
            // the line that opens the thread.
            Column(Modifier.clickable { threads++ }) {
                StatusText("图片评论", author = "某人", onLink = { links += it })
                Text("共1条回复 >")
            }
        }

        compose.onNodeWithText("共1条回复 >").performClick()
        assertEquals(1, threads)
        assertEquals(emptyList<StatusLink>(), links)
    }
}
