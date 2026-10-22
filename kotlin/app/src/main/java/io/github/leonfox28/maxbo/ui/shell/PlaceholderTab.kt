package io.github.leonfox28.maxbo.ui.shell

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

/** Stands in for a tab not yet migrated. */
@Composable
internal fun PlaceholderTab(title: String, padding: PaddingValues) {
    Box(
        Modifier.fillMaxSize().padding(padding).safeDrawingPadding(),
        contentAlignment = Alignment.Center,
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge)
    }
}
