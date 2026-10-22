package io.github.leonfox28.maxbo.ui.widget

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * The bar at the foot of a page that comments: a field look-alike, as tall as the tab bar's items,
 * that opens the comment sheet, and [trailing] buttons.
 */
@Composable
fun CommentBar(
    hint: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
) {
    FloatingPill(modifier, stretch = true) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(
                onClick = onClick,
                modifier = Modifier.weight(1f),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surface,
            ) {
                Box(
                    Modifier.fillMaxWidth().height(48.dp).padding(horizontal = 16.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Text(
                        hint,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (trailing != null) {
                Spacer(Modifier.width(4.dp))
                trailing()
            }
        }
    }
}
