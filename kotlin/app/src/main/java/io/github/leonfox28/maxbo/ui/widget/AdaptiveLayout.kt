package io.github.leonfox28.maxbo.ui.widget

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

/**
 * Material 3's window size classes, by the width of the app's window. The window, not the screen:
 * the app may share the screen with another, and a foldable changes size as it folds and unfolds.
 */
enum class WindowSize {
    Compact,
    Medium,
    Expanded;

    companion object {
        fun fromWidth(width: Dp): WindowSize =
            when {
                width < 600.dp -> Compact
                width < 840.dp -> Medium
                else -> Expanded
            }
    }
}

/**
 * The widest a page's content runs. Wider windows (tablets, unfolded foldables, phones on their
 * side) show it as a column of this width in their middle, much as a phone shows it, rather than
 * stretching lines, pictures and videos across the window.
 */
val ReadingWidth = 640.dp

/**
 * The widest a timeline of status cards runs, on a window at least [WideCardFrom] wide; narrower
 * windows keep it to [ReadingWidth].
 */
val FeedWidth = 1000.dp

/**
 * The width from which a status card puts its media beside its text: that of an expanded window, so
 * an unfolded foldable has it as a tablet does.
 */
val WideCardFrom = 840.dp

/** The column a timeline of status cards takes in a window [width] wide. */
fun feedColumnWidth(width: Dp): Dp = if (width >= WideCardFrom) FeedWidth else ReadingWidth

/**
 * The padding that keeps a list's items to the reading column of a window [width] wide, plus
 * [extra]. Margins rather than a narrower list, so the user can drag the list anywhere across the
 * window.
 *
 * @param feed for a timeline of status cards, in a [feedColumnWidth] column.
 */
fun readingPadding(
    width: Dp,
    extra: PaddingValues = PaddingValues(),
    feed: Boolean = false,
): PaddingValues {
    val column = if (feed) feedColumnWidth(width) else ReadingWidth
    val side = maxOf(0.dp, (width - column) / 2)
    return PaddingValues(
        start = side + extra.calculateStartPadding(LayoutDirection.Ltr),
        top = extra.calculateTopPadding(),
        end = side + extra.calculateEndPadding(LayoutDirection.Ltr),
        bottom = extra.calculateBottomPadding(),
    )
}
