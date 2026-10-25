package io.github.leonfox28.maxbo.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.FormatSize
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.MotionPhotosAuto
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.leonfox28.maxbo.LocalAppGraph
import io.github.leonfox28.maxbo.MB
import io.github.leonfox28.maxbo.core.settings.AppSettings
import io.github.leonfox28.maxbo.core.settings.ImageQuality
import io.github.leonfox28.maxbo.core.settings.ThemeMode
import io.github.leonfox28.maxbo.core.util.devLog
import io.github.leonfox28.maxbo.core.util.formatBytes
import io.github.leonfox28.maxbo.ui.navigation.LocalNavigator
import io.github.leonfox28.maxbo.ui.navigation.Route
import io.github.leonfox28.maxbo.ui.theme.KeepTextSize
import io.github.leonfox28.maxbo.ui.widget.AppAlertDialog
import io.github.leonfox28.maxbo.ui.widget.AppSnackbarHost
import io.github.leonfox28.maxbo.ui.widget.BackButton
import io.github.leonfox28.maxbo.ui.widget.LocalSnackbars
import io.github.leonfox28.maxbo.ui.widget.readingPadding
import io.github.leonfox28.maxbo.ui.widget.showMessage
import java.io.IOException
import kotlinx.coroutines.launch

private val ThemeMode.label: String
    get() =
        when (this) {
            ThemeMode.System -> "跟随系统"
            ThemeMode.Light -> "浅色"
            ThemeMode.Dark -> "深色"
        }

/** One of the options a setting offers: a value, its label and maybe a description. */
private class Choice<T>(val value: T, val label: String, val description: String? = null)

/** A setting being chosen in a dialog. */
private class Choosing<T>(
    val title: String,
    val selected: T,
    val choices: List<Choice<T>>,
    val onChoose: (T) -> Unit,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsPage() {
    val graph = LocalAppGraph.current
    val navigator = LocalNavigator.current
    val snackbars = LocalSnackbars.current
    val scope = rememberCoroutineScope()
    val settings by graph.settings.settings.collectAsStateWithLifecycle()
    var choosing by remember { mutableStateOf<Choosing<*>?>(null) }
    var confirmingClear by remember { mutableStateOf(false) }
    // The cache size, read again after clearing or trimming.
    var sizeReads by remember { mutableIntStateOf(0) }
    var cacheSize by remember { mutableStateOf("计算中…") }
    LaunchedEffect(sizeReads) {
        cacheSize =
            try {
                formatBytes(graph.pictureCache.sizeBytes())
            } catch (e: IOException) {
                devLog("reading the picture cache size failed: ${e.javaClass.simpleName}")
                "无法读取"
            }
    }

    fun update(change: (AppSettings) -> AppSettings) {
        graph.settings.update(change)
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("设置") }, navigationIcon = { BackButton() }) },
        snackbarHost = { AppSnackbarHost() },
    ) { padding ->
        BoxWithConstraints(Modifier.fillMaxSize()) {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = readingPadding(maxWidth, padding)) {
                item { SectionHeader("外观") }
                item {
                    Setting(Icons.Outlined.DarkMode, "主题", settings.themeMode.label) {
                        choosing =
                            Choosing(
                                "主题",
                                settings.themeMode,
                                ThemeMode.entries.map { Choice(it, it.label) },
                            ) { mode ->
                                update { it.copy(themeMode = mode) }
                            }
                    }
                }
                item {
                    Setting(
                        Icons.Outlined.FormatSize,
                        "字体大小",
                        settings.textSize.label,
                        opensPage = true,
                    ) {
                        navigator.push(Route.TextSize)
                    }
                }
                item { SectionHeader("图片") }
                item {
                    Setting(Icons.Outlined.Image, "图片质量", settings.imageQuality.label) {
                        choosing =
                            Choosing(
                                "图片质量",
                                settings.imageQuality,
                                ImageQuality.entries.map { Choice(it, it.label, it.description) },
                            ) { quality ->
                                update { it.copy(imageQuality = quality) }
                            }
                    }
                }
                item {
                    SwitchSetting(
                        Icons.Outlined.MotionPhotosAuto,
                        "自动播放实况照片",
                        "查看大图时静音播放一遍，每张要下载几 MB",
                        settings.autoplayLivePhotos,
                    ) { on ->
                        update { it.copy(autoplayLivePhotos = on) }
                    }
                }
                item {
                    Setting(
                        Icons.Outlined.Storage,
                        "图片缓存上限",
                        "${formatBytes(settings.imageCacheLimitMb * MB)}，超出后先删最早下载的图片",
                    ) {
                        choosing =
                            Choosing(
                                "图片缓存上限",
                                settings.imageCacheLimitMb,
                                AppSettings.imageCacheLimitChoices.map {
                                    Choice(it, formatBytes(it * MB))
                                },
                            ) { mb ->
                                update { it.copy(imageCacheLimitMb = mb) }
                                scope.launch {
                                    graph.pictureCache.setLimit(mb * MB)
                                    sizeReads++
                                }
                            }
                    }
                }
                item {
                    Setting(Icons.Outlined.CleaningServices, "清除图片缓存", "已用 $cacheSize") {
                        confirmingClear = true
                    }
                }
                item { SectionHeader("其他") }
                item {
                    Setting(Icons.Outlined.Info, "关于 maxbo", opensPage = true) {
                        navigator.push(Route.About)
                    }
                }
            }
        }
    }

    choosing?.let { ChoiceDialog(it) { choosing = null } }
    if (confirmingClear) {
        AppAlertDialog(
            onDismissRequest = { confirmingClear = false },
            title = { Text("清除图片缓存") },
            text = { Text("已下载的图片会被删除，之后浏览时重新下载。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmingClear = false
                        scope.launch {
                            graph.pictureCache.clear()
                            sizeReads++
                            snackbars.showMessage("图片缓存已清除", scope)
                        }
                    }
                ) {
                    Text("清除")
                }
            },
            dismissButton = { TextButton(onClick = { confirmingClear = false }) { Text("取消") } },
        )
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        title,
        modifier = Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 4.dp),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
private fun Setting(
    icon: ImageVector,
    title: String,
    value: String? = null,
    opensPage: Boolean = false,
    onClick: () -> Unit,
) {
    ListItem(
        headlineContent = { Text(title) },
        modifier = Modifier.clickable(onClick = onClick),
        supportingContent = value?.let { { Text(it) } },
        leadingContent = { Icon(icon, contentDescription = null) },
        trailingContent =
            if (opensPage) ({ Icon(Icons.Filled.ChevronRight, contentDescription = null) })
            else null,
    )
}

@Composable
private fun SwitchSetting(
    icon: ImageVector,
    title: String,
    description: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    ListItem(
        headlineContent = { Text(title) },
        modifier = Modifier.toggleable(checked, role = Role.Switch, onValueChange = onChange),
        supportingContent = { Text(description) },
        leadingContent = { Icon(icon, contentDescription = null) },
        trailingContent = { Switch(checked, onCheckedChange = null) },
    )
}

/**
 * Lets the user pick one of the choices, which closes it; dismissing it picks none. A list with no
 * buttons, as Material's simple dialogs are.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> ChoiceDialog(choosing: Choosing<T>, close: () -> Unit) {
    BasicAlertDialog(onDismissRequest = close) {
        Surface(
            shape = AlertDialogDefaults.shape,
            color = AlertDialogDefaults.containerColor,
            tonalElevation = AlertDialogDefaults.TonalElevation,
        ) {
            KeepTextSize {
                Column(Modifier.padding(vertical = 24.dp)) {
                    Text(
                        choosing.title,
                        modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 16.dp),
                        style = MaterialTheme.typography.headlineSmall,
                    )
                    Column(Modifier.selectableGroup()) {
                        for (choice in choosing.choices) Option(choice, choosing, close)
                    }
                }
            }
        }
    }
}

@Composable
private fun <T> Option(choice: Choice<T>, choosing: Choosing<T>, close: () -> Unit) {
    val selected = choice.value == choosing.selected
    Row(
        Modifier.fillMaxWidth()
            .selectable(
                selected = selected,
                role = Role.RadioButton,
                onClick = {
                    close()
                    choosing.onChoose(choice.value)
                },
            )
            .padding(horizontal = 24.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Column(Modifier.padding(start = 16.dp)) {
            Text(choice.label, style = MaterialTheme.typography.bodyLarge)
            choice.description?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
