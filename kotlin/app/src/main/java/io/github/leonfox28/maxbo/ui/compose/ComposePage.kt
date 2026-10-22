package io.github.leonfox28.maxbo.ui.compose

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.leonfox28.maxbo.LocalAppGraph
import io.github.leonfox28.maxbo.core.net.WeiboException
import io.github.leonfox28.maxbo.ui.interactions.PictureThumb
import io.github.leonfox28.maxbo.ui.interactions.SendButton
import io.github.leonfox28.maxbo.ui.interactions.SendError
import io.github.leonfox28.maxbo.ui.navigation.LocalNavigator
import io.github.leonfox28.maxbo.ui.navigation.Route
import io.github.leonfox28.maxbo.ui.widget.AppSnackbarHost
import io.github.leonfox28.maxbo.ui.widget.CloseButton
import io.github.leonfox28.maxbo.ui.widget.LocalSnackbars
import io.github.leonfox28.maxbo.ui.widget.readingPadding
import io.github.leonfox28.maxbo.ui.widget.showMessage
import kotlinx.coroutines.launch

/**
 * Writes a new post: text, up to nine pictures, and who can see it.
 *
 * Nothing goes to Weibo until the user taps 发送; the pictures upload then, one at a time. The draft
 * outlives the page (`AppGraph.composeDraft`).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ComposePage() {
    val graph = LocalAppGraph.current
    val controller = graph.composeDraft
    val draft by controller.draft.collectAsStateWithLifecycle()
    val navigator = LocalNavigator.current
    val snackbars = LocalSnackbars.current
    val picker = LocalPicturePicker.current
    val focusManager = LocalFocusManager.current
    val scope = rememberCoroutineScope()
    var text by remember {
        val initial = controller.draft.value.text
        mutableStateOf(TextFieldValue(initial, TextRange(initial.length)))
    }
    var sending by remember { mutableStateOf(false) }
    // The picture going up, from 0, while sending.
    var uploading by remember { mutableStateOf<Int?>(null) }
    var error by remember { mutableStateOf<WeiboException?>(null) }

    fun pick() = scope.launch {
        val room = MAX_DRAFT_PICTURES - controller.draft.value.pictures.size
        if (room <= 0) return@launch
        val uris =
            try {
                picker.pick(room)
            } catch (_: PickerUnavailableException) {
                snackbars.showMessage("无法打开相册", scope)
                return@launch
            }
        val left = controller.addPictures(uris)
        // Pickers without a limit, on older Android, let the user pick more.
        if (left > 0) {
            snackbars.showMessage("最多 $MAX_DRAFT_PICTURES 张图片，多选的 $left 张没有加上", scope)
        }
    }

    fun send() = scope.launch {
        focusManager.clearFocus()
        sending = true
        error = null
        try {
            controller.send(onUpload = { uploading = it })
            snackbars.showMessage("已发送", graph.appScope)
            navigator.pop()
        } catch (e: WeiboException) {
            error = e
        } finally {
            sending = false
            uploading = null
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("发微博") },
                navigationIcon = { CloseButton() },
                actions = {
                    Box(Modifier.padding(end = 12.dp)) {
                        SendButton("发送", sending = sending, enabled = draft.canSend) { send() }
                    }
                },
            )
        },
        snackbarHost = { AppSnackbarHost() },
    ) { padding ->
        BoxWithConstraints(Modifier.fillMaxSize().imePadding()) {
            val focus = remember { FocusRequester() }
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding =
                    readingPadding(
                        maxWidth,
                        PaddingValues(
                            start = 16.dp,
                            top = padding.calculateTopPadding() + 8.dp,
                            end = 16.dp,
                            bottom = padding.calculateBottomPadding() + 16.dp,
                        ),
                    ),
            ) {
                item(key = "text") {
                    TextField(
                        text,
                        {
                            text = it
                            // It also hears the cursor move.
                            if (it.text != controller.draft.value.text) controller.setText(it.text)
                        },
                        Modifier.fillMaxWidth().focusRequester(focus),
                        enabled = !sending,
                        placeholder = { Text("分享新鲜事…") },
                        minLines = 5,
                        colors =
                            TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                disabledContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                                disabledIndicatorColor = Color.Transparent,
                            ),
                    )
                }
                item(key = "pictures") {
                    PictureGrid(
                        draft.pictures,
                        uploading = uploading,
                        enabled = !sending,
                        onAdd = { pick() },
                        onRemove = controller::removePicture,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                item(key = "visibility") {
                    Box(Modifier.padding(top = 12.dp)) {
                        VisibilityButton(
                            draft.visibility,
                            onChange = if (sending) null else controller::setVisibility,
                        )
                    }
                }
                uploading?.let { i ->
                    item(key = "uploading") {
                        Text(
                            "正在上传图片 ${i + 1}/${draft.pictures.size}",
                            modifier = Modifier.padding(top = 8.dp),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
                error?.let { e ->
                    item(key = "error") {
                        SendError(e, onVerify = { navigator.push(Route.Web(it)) })
                    }
                }
            }
            LaunchedEffect(Unit) { focus.requestFocus() }
        }
    }
}

private val Gap = 4.dp

/** The picked pictures in rows of three, with a tile to add more while there is room. */
@Composable
private fun PictureGrid(
    pictures: List<DraftPicture>,
    uploading: Int?,
    enabled: Boolean,
    onAdd: () -> Unit,
    onRemove: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val side: Dp = (maxWidth - Gap * 2) / 3
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(Gap),
            verticalArrangement = Arrangement.spacedBy(Gap),
            maxItemsInEachRow = 3,
        ) {
            pictures.forEachIndexed { i, picture ->
                PictureThumb(
                    picture.uri,
                    size = side,
                    uploading = i == uploading,
                    onRemove = if (enabled) ({ onRemove(i) }) else null,
                )
            }
            if (pictures.size < MAX_DRAFT_PICTURES) {
                Surface(
                    Modifier.size(side)
                        .clip(RoundedCornerShape(4.dp))
                        .clickable(enabled = enabled, onClick = onAdd),
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Outlined.AddPhotoAlternate,
                            contentDescription = "添加图片",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}
