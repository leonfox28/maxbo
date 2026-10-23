package io.github.leonfox28.maxbo.ui.widget

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val TagShape = RoundedCornerShape(3.dp)

/** A small label such as 广告 or 置顶: outlined, or filled with [color]. */
@Composable
fun TagLabel(text: String, modifier: Modifier = Modifier, color: Color? = null) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier
            .then(
                if (color == null) Modifier.border(1.dp, colors.outline, TagShape)
                else Modifier.background(color, TagShape)
            )
            .padding(horizontal = 4.dp, vertical = 1.dp)
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelSmall,
            color = if (color == null) colors.onSurfaceVariant else Color.White,
        )
    }
}

/** A small label over media, such as GIF or a video duration. */
@Composable
fun MediaBadge(label: String, modifier: Modifier = Modifier) {
    Box(
        modifier
            .background(Color.Black.copy(alpha = 0.54f), TagShape)
            .padding(horizontal = 4.dp, vertical = 1.dp)
    ) {
        Text(label, color = Color.White, fontSize = 10.sp, lineHeight = 14.sp)
    }
}
