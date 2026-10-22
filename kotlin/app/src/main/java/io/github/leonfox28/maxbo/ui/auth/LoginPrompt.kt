package io.github.leonfox28.maxbo.ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.leonfox28.maxbo.ui.navigation.LocalNavigator
import io.github.leonfox28.maxbo.ui.navigation.Route

/** Shown in place of content that needs a signed-in user, centred in [modifier]'s bounds. */
@Composable
fun LoginPrompt(expired: Boolean, modifier: Modifier = Modifier) {
    val navigator = LocalNavigator.current
    Box(modifier, contentAlignment = Alignment.Center) {
        Column(
            Modifier.padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                Icons.Outlined.Lock,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(16.dp))
            Text(
                if (expired) "登录已过期，请重新登录" else "登录后即可浏览关注的人的微博",
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(24.dp))
            Button(onClick = { navigator.push(Route.Login) }) { Text("登录微博") }
            Spacer(Modifier.height(16.dp))
            Text(
                "登录在微博官方页面中完成，maxbo 不会接触你的密码，登录状态只保存在本机。",
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
            )
        }
    }
}
