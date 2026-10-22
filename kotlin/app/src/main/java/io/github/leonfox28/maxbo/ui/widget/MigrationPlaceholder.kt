package io.github.leonfox28.maxbo.ui.widget

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign

/** Stands in for a page a later migration stage brings over; [what] says which. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MigrationPlaceholder(title: String, what: String) {
    Scaffold(
        topBar = { TopAppBar(title = { Text(title) }, navigationIcon = { BackButton() }) },
        snackbarHost = { AppSnackbarHost() },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
            Text(
                "$what\n（迁移中）",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
            )
        }
    }
}
