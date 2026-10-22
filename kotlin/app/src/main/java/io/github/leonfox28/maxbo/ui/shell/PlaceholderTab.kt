package io.github.leonfox28.maxbo.ui.shell

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.leonfox28.maxbo.LocalAppGraph
import io.github.leonfox28.maxbo.core.session.SessionState
import io.github.leonfox28.maxbo.ui.auth.LoginPrompt

/** Stands in for a tab not yet migrated; like those tabs, it asks visitors to log in. */
@Composable
internal fun PlaceholderTab(title: String, padding: PaddingValues) {
    val session by LocalAppGraph.current.session.state.collectAsStateWithLifecycle()
    val area =
        Modifier.fillMaxSize()
            .windowInsetsPadding(
                WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)
            )
    when (val state = session) {
        is SessionState.SignedOut -> LoginPrompt(state.expired, area)
        is SessionState.SignedIn ->
            Box(area.padding(padding), contentAlignment = Alignment.Center) {
                Text(title, style = MaterialTheme.typography.titleLarge)
            }
    }
}
