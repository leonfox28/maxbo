package io.github.leonfox28.maxbo.ui.widget

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import io.github.leonfox28.maxbo.ui.navigation.LocalNavigator

/** An app bar's back arrow, closing the page. */
@Composable
fun BackButton() {
    val navigator = LocalNavigator.current
    IconButton(onClick = navigator::pop) {
        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
    }
}
