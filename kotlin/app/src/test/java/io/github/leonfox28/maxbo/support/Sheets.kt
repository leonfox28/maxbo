package io.github.leonfox28.maxbo.support

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.performSemanticsAction

/**
 * Closes the bottom sheet showing, as tapping outside it does: through the scrim's semantics, as a
 * tap at its centre would land on the sheet.
 */
fun ComposeContentTestRule.closeSheet() {
    onNode(hasContentDescription("Close sheet")).performSemanticsAction(SemanticsActions.OnClick)
    waitForIdle()
}

/** Whether a text field shows, as one does while a composer sheet is open. */
fun ComposeContentTestRule.showsTextField() =
    onAllNodes(hasSetTextAction()).fetchSemanticsNodes().isNotEmpty()
