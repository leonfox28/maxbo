package io.github.leonfox28.maxbo.ui.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.leonfox28.maxbo.LocalAppGraph
import io.github.leonfox28.maxbo.core.settings.TextSize
import io.github.leonfox28.maxbo.data.model.Comment
import io.github.leonfox28.maxbo.data.model.Status
import io.github.leonfox28.maxbo.data.model.WeiboUser
import io.github.leonfox28.maxbo.ui.comments.CommentTile
import io.github.leonfox28.maxbo.ui.widget.BackButton
import io.github.leonfox28.maxbo.ui.widget.ReadingWidth
import io.github.leonfox28.maxbo.ui.widget.readingPadding
import io.github.leonfox28.maxbo.ui.widget.status.StatusCard
import java.time.Duration
import java.time.Instant
import kotlin.math.roundToInt

/**
 * Picks the text size on a slider, under a sample post and comment that show it. A new size applies
 * at once, here as everywhere, so the samples are the app itself at that size.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TextSizePage() {
    val graph = LocalAppGraph.current
    val settings by graph.settings.settings.collectAsStateWithLifecycle()
    val (status, comment) = remember { samples(Instant.now()) }
    Scaffold(topBar = { TopAppBar(title = { Text("字体大小") }, navigationIcon = { BackButton() }) }) {
        padding ->
        Column(Modifier.fillMaxSize().padding(top = padding.calculateTopPadding())) {
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                LazyColumn(
                    // Samples only: their like buttons and links do nothing.
                    Modifier.fillMaxSize().inert(),
                    contentPadding = readingPadding(maxWidth),
                ) {
                    item { StatusCard(status) }
                    item { HorizontalDivider() }
                    item { CommentTile(comment) }
                }
            }
            SizeSlider(settings.textSize) { chosen ->
                graph.settings.update { it.copy(textSize = chosen) }
            }
        }
    }
}

private fun samples(now: Instant): Pair<Status, Comment> =
    Status(
        id = "text-size-sample",
        user = WeiboUser(id = "text-size-sample", screenName = "maxbo"),
        createdAt = now - Duration.ofMinutes(2),
        text = "拖动下面的滑块调整字体大小，这条微博和下面的评论会按选中的大小显示。调整后所有页面的文字都会跟着变大或变小。",
        repostsCount = 3,
        commentsCount = 12,
        likesCount = 46,
    ) to
        Comment(
            id = "text-size-sample",
            user = WeiboUser(id = "text-size-sample-2", screenName = "示例用户"),
            createdAt = now - Duration.ofMinutes(1),
            text = "「标准」就是系统设置里的字体大小。",
            likesCount = 5,
        )

/**
 * Takes presses before what is inside sees them, so taps there do nothing, as Flutter's
 * IgnorePointer has it. Drags aren't taken, so the list still scrolls.
 */
private fun Modifier.inert() =
    pointerInput(Unit) {
        awaitPointerEventScope {
            while (true) {
                awaitPointerEvent(PointerEventPass.Initial).changes.forEach {
                    if (it.pressed != it.previousPressed) it.consume()
                }
            }
        }
    }

/** The sizes from smallest to largest on a slider, with a step button at either end. */
@Composable
private fun SizeSlider(size: TextSize, onChange: (TextSize) -> Unit) {
    val sizes = TextSize.entries
    fun choose(index: Int) {
        if (index != size.ordinal) onChange(sizes[index])
    }

    Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
        Box(Modifier.fillMaxWidth().navigationBarsPadding(), contentAlignment = Alignment.Center) {
            // The label above the slider, so that its growing and shrinking
            // leaves the slider where the finger is.
            Column(
                Modifier.widthIn(max = ReadingWidth)
                    .padding(start = 8.dp, top = 16.dp, end = 8.dp, bottom = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(size.label, style = MaterialTheme.typography.titleSmall)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StepButton("调小", glyphDp = 14, to = size.ordinal - 1, sizes.size, ::choose)
                    Slider(
                        value = size.ordinal.toFloat(),
                        onValueChange = { choose(it.roundToInt()) },
                        modifier = Modifier.weight(1f).semantics { stateDescription = size.label },
                        valueRange = 0f..(sizes.size - 1).toFloat(),
                        steps = sizes.size - 2,
                    )
                    StepButton("调大", glyphDp = 24, to = size.ordinal + 1, sizes.size, ::choose)
                }
            }
        }
    }
}

/**
 * An "A" of a fixed size, hinting at small or large text, rather than text that grows and shrinks
 * with the slider; it steps to size [to], and is dimmed when there is none.
 */
@Composable
private fun StepButton(
    label: String,
    glyphDp: Int,
    to: Int,
    count: Int,
    choose: (Int) -> Unit,
) {
    IconButton(
        onClick = { choose(to) },
        modifier = Modifier.semantics { contentDescription = label },
        enabled = to in 0 until count,
    ) {
        Text(
            "A",
            modifier = Modifier.clearAndSetSemantics {},
            fontSize = with(LocalDensity.current) { glyphDp.dp.toSp() },
            fontWeight = FontWeight.Medium,
            // Coloured as the button colours icons, dimmed at either end.
            color = LocalContentColor.current,
        )
    }
}
