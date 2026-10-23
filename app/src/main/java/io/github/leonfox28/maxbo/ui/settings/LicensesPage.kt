package io.github.leonfox28.maxbo.ui.settings

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.github.leonfox28.maxbo.R
import io.github.leonfox28.maxbo.core.util.readMap
import io.github.leonfox28.maxbo.core.util.readMaps
import io.github.leonfox28.maxbo.core.util.readString
import io.github.leonfox28.maxbo.ui.widget.AppAlertDialog
import io.github.leonfox28.maxbo.ui.widget.BackButton
import io.github.leonfox28.maxbo.ui.widget.readingPadding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive

/** A licence a library is under: its name, and its text when the list has it. */
data class License(val name: String, val content: String?)

/** A library the app is built on, with its licences. */
data class Library(val name: String, val version: String?, val licenses: List<License>)

/**
 * The libraries in the list the AboutLibraries plugin writes at build time (`aboutlibraries.json`),
 * by name.
 */
fun parseLibraries(json: String): List<Library> {
    val root = readMap(Json.parseToJsonElement(json)) ?: return emptyList()
    val licenses = readMap(root["licenses"])
    return readMaps(root["libraries"])
        .mapNotNull { library ->
            val name = readString(library["name"]) ?: return@mapNotNull null
            val ids = (library["licenses"] as? JsonArray).orEmpty()
            Library(
                name = name,
                version = readString(library["artifactVersion"]),
                licenses =
                    ids.mapNotNull { id ->
                        val key = (id as? JsonPrimitive)?.content ?: return@mapNotNull null
                        val license = readMap(licenses?.get(key))
                        License(
                            readString(license?.get("name")) ?: key,
                            readString(license?.get("content")),
                        )
                    },
            )
        }
        .sortedBy { it.name.lowercase() }
}

private suspend fun loadLibraries(context: Context): List<Library> =
    withContext(Dispatchers.IO) {
        context.resources.openRawResource(R.raw.aboutlibraries).use {
            parseLibraries(it.readBytes().decodeToString())
        }
    }

/** maxbo's own licence, then the libraries it is built on and theirs. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LicensesPage() {
    val context = LocalContext.current
    val libraries by produceState<List<Library>?>(null) { value = loadLibraries(context) }
    var shown by remember { mutableStateOf<Library?>(null) }
    Scaffold(topBar = { TopAppBar(title = { Text("开源许可") }, navigationIcon = { BackButton() }) }) {
        padding ->
        BoxWithConstraints(Modifier.fillMaxSize()) {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = readingPadding(maxWidth, padding)) {
                item(key = "maxbo") {
                    Column(
                        Modifier.fillMaxWidth().padding(vertical = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        AppTitle()
                        Text(
                            "以 GNU GPL-3.0 许可发布",
                            modifier = Modifier.padding(top = 8.dp),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
                val list = libraries
                if (list == null) {
                    item(key = "loading") { CircularProgressIndicator(Modifier.padding(24.dp)) }
                } else {
                    items(list, key = { "${it.name} ${it.version}" }) { library ->
                        ListItem(
                            headlineContent = { Text(library.name) },
                            modifier = Modifier.clickable { shown = library },
                            supportingContent = { Text(summaryOf(library)) },
                        )
                    }
                }
            }
        }
    }
    shown?.let { library -> LicenseDialog(library) { shown = null } }
}

private fun summaryOf(library: Library) =
    listOfNotNull(library.version, library.licenses.joinToString("、") { it.name }.ifEmpty { null })
        .joinToString(" · ")

@Composable
private fun LicenseDialog(library: Library, close: () -> Unit) {
    AppAlertDialog(
        onDismissRequest = close,
        title = { Text(library.name) },
        text = {
            Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState())) {
                for (license in library.licenses) {
                    Text(license.name, style = MaterialTheme.typography.titleSmall)
                    license.content?.let {
                        Text(
                            it,
                            modifier = Modifier.padding(top = 8.dp, bottom = 16.dp),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = close) { Text("确定") } },
    )
}
