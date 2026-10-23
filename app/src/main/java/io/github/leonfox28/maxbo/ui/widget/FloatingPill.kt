package io.github.leonfox28.maxbo.ui.widget

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * A bar floating over the bottom of a page, such as the tab bar or the comment bar. Styled as the
 * feed is: a neutral surface, set off by an outline and a shadow rather than by colour, leaving the
 * primary colour to mark what's active (as it does links and likes).
 *
 * Its [content] sits 8 in from every edge, so a round item at either end is as far from the pill's
 * ends as from its top and bottom.
 *
 * @param stretch spans the page, less a margin, rather than wrapping [content]. On a wide window,
 *   it spans the reading column instead.
 * @param alignment where a pill that wraps [content] sits across the page, kept as far in from the
 *   side as a stretched one.
 */
@Composable
fun FloatingPill(
    modifier: Modifier = Modifier,
    stretch: Boolean = false,
    alignment: Alignment = Alignment.Center,
    content: @Composable () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier
            .fillMaxWidth()
            .windowInsetsPadding(
                WindowInsets.systemBars
                    .union(WindowInsets.displayCutout)
                    .only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom)
                    .union(WindowInsets(bottom = 12.dp))
            )
            .padding(horizontal = 16.dp),
        contentAlignment = if (stretch) Alignment.Center else alignment,
    ) {
        Surface(
            modifier =
                if (stretch) Modifier.widthIn(max = ReadingWidth - 32.dp).fillMaxWidth()
                else Modifier,
            shape = CircleShape,
            color = colors.surfaceContainerHigh,
            border = BorderStroke(1.dp, colors.outlineVariant),
            shadowElevation = 3.dp,
        ) {
            Box(Modifier.padding(8.dp)) { content() }
        }
    }
}
