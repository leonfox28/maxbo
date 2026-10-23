package io.github.leonfox28.maxbo.ui.shell

import androidx.compose.runtime.compositionLocalOf
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/**
 * Taps on a home tab's button while the tab is already open, for its page to scroll back to the
 * top, or refresh if it's there already.
 */
val LocalTabReselect = compositionLocalOf<Flow<Unit>> { emptyFlow() }
