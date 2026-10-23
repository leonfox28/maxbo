package io.github.leonfox28.maxbo.ui.widget

import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.Composable
import io.github.leonfox28.maxbo.ui.theme.KeepTextSize

/** Material's [AlertDialog], its text at the size the user chose. */
@Composable
fun AppAlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    dismissButton: (@Composable () -> Unit)? = null,
    title: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null,
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = { KeepTextSize(confirmButton) },
        dismissButton = dismissButton?.let { { KeepTextSize(it) } },
        title = title?.let { { KeepTextSize(it) } },
        text = text?.let { { KeepTextSize(it) } },
    )
}
