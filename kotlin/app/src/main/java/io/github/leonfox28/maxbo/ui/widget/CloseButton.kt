package io.github.leonfox28.maxbo.ui.widget

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import io.github.leonfox28.maxbo.ui.navigation.LocalNavigator

/** An app bar's close button, for pages that work like a dialog, such as logging in. */
@Composable
fun CloseButton() {
    val navigator = LocalNavigator.current
    IconButton(onClick = navigator::pop) { Icon(Icons.Filled.Close, contentDescription = "关闭") }
}
