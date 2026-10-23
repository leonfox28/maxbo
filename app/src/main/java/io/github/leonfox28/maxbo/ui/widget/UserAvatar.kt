package io.github.leonfox28.maxbo.ui.widget

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.leonfox28.maxbo.core.media.WeiboImage
import io.github.leonfox28.maxbo.data.model.WeiboUser

/** A round avatar of [user], tappable when [onClick] is set. */
@Composable
fun UserAvatar(
    user: WeiboUser?,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    onClick: ((WeiboUser) -> Unit)? = null,
) {
    val tappable =
        Modifier.testTag("avatar")
            .then(
                if (user != null && onClick != null) Modifier.clickable { onClick(user) }
                else Modifier
            )
    val url = user?.avatarUrl
    if (url == null) {
        Surface(
            modifier.size(size).clip(CircleShape).then(tappable),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Filled.Person,
                    contentDescription = null,
                    modifier = Modifier.size(size * 0.6f),
                )
            }
        }
    } else {
        WeiboImage(url, modifier.size(size).clip(CircleShape).then(tappable))
    }
}

/** Weibo's verification mark: gold for people, blue for organisations. */
@Composable
fun VerifiedIcon(verifiedType: Int, modifier: Modifier = Modifier, size: Dp = 14.dp) {
    Icon(
        Icons.Filled.Verified,
        contentDescription = null,
        modifier = modifier.size(size),
        tint = if (verifiedType == 0) Color(0xFFFFA000) else Color(0xFF1E88E5),
    )
}

/** Whether [verifiedType] (`WeiboUser.verifiedType`) has a mark. */
fun showsVerified(verifiedType: Int?): Boolean = verifiedType != null && verifiedType in 0..7
