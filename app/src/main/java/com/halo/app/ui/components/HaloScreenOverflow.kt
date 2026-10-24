package com.halo.app.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.halo.app.R
import com.halo.app.language.uiText

/** Labels are localized by the screen. Callbacks keep their existing domain/policy owners. */
data class HaloOverflowAction(val label: String, val enabled: Boolean = true, val onClick: () -> Unit)

@Composable
fun HaloScreenOverflow(actions: List<HaloOverflowAction>, enabled: Boolean = true) {
    var expanded by remember { mutableStateOf(false) }
    HaloScreenOverflow(actions, enabled, expanded) { expanded = it }
}

/** Hoisted variant keeps Back and screen guidance aware of an open menu. */
@Composable
fun HaloScreenOverflow(actions: List<HaloOverflowAction>, enabled: Boolean,
    expanded: Boolean, onExpandedChange: (Boolean) -> Unit) {
    LaunchedEffect(enabled) { if (!enabled) onExpandedChange(false) }
    Box {
        IconButton(onClick = { onExpandedChange(true) }, enabled = enabled && actions.isNotEmpty(),
            modifier = Modifier.size(48.dp)) {
            Icon(painterResource(R.drawable.halo_ic_more), uiText("More options"), Modifier.size(24.dp))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { onExpandedChange(false) }) {
            actions.forEach { action ->
                DropdownMenuItem(text = { Text(action.label) }, enabled = action.enabled,
                    onClick = { onExpandedChange(false); action.onClick() })
            }
        }
    }
}
