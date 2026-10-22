package io.github.leonfox28.maxbo.ui.settings

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.leonfox28.maxbo.ui.widget.AppSnackbarHost
import io.github.leonfox28.maxbo.ui.widget.BackButton

/** A stand-in until K6, to try page transitions and the predictive back gesture on. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsPage() {
    Scaffold(
        topBar = { TopAppBar(title = { Text("设置") }, navigationIcon = { BackButton() }) },
        snackbarHost = { AppSnackbarHost() },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize(), contentPadding = padding) {
            item { ListItem(headlineContent = { Text("外观") }, supportingContent = { Text("迁移中") }) }
        }
    }
}
