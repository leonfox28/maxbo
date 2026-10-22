package io.github.leonfox28.maxbo.ui.compose

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import io.github.leonfox28.maxbo.data.model.StatusVisibility

private val StatusVisibility.icon: ImageVector
    get() =
        when (this) {
            StatusVisibility.Public -> Icons.Outlined.Public
            StatusVisibility.Friends -> Icons.Outlined.Group
            StatusVisibility.OnlyMe -> Icons.Outlined.Lock
            StatusVisibility.Fans -> Icons.Outlined.People
        }

/**
 * Picks who can see a new post or repost, from [StatusVisibility.choices]; inert without
 * [onChange].
 */
@Composable
fun VisibilityButton(value: StatusVisibility, onChange: ((StatusVisibility) -> Unit)?) {
    var open by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { open = !open }, enabled = onChange != null) {
            Icon(value.icon, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(value.label)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            for (choice in StatusVisibility.choices) {
                DropdownMenuItem(
                    text = { Text(choice.label) },
                    onClick = {
                        open = false
                        onChange?.invoke(choice)
                    },
                    leadingIcon = { Icon(choice.icon, contentDescription = null) },
                    trailingIcon =
                        if (choice == value)
                            ({ Icon(Icons.Filled.Check, contentDescription = null) })
                        else null,
                )
            }
        }
    }
}
