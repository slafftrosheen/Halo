package com.halo.app.ui.shell

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.unit.dp
import com.halo.app.language.uiText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close

/** Keep Close independent of translated subview widths and preserve all existing routes. */
@Composable
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
internal fun BellSubviewControls(route: String?, onNavigate: (String) -> Unit, onClose: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        Row(Modifier.weight(1f).horizontalScroll(rememberScrollState())) {
            listOf("activity" to "Activities", "notifications" to "Notifications").forEach { (destination, label) ->
                val current = route?.substringBefore("?") == destination
                val reveal = remember { BringIntoViewRequester() }
                var placed by remember { mutableStateOf(false) }
                LaunchedEffect(current, placed) { if (current && placed) reveal.bringIntoView() }
                TextButton(onClick = { onNavigate(destination) }, modifier = Modifier.heightIn(min = 48.dp)
                    .bringIntoViewRequester(reveal).onGloballyPositioned { placed = true }.semantics {
                    selected = current
                }) { Text(uiText(label), maxLines = 1, softWrap = false) }
            }
        }
        IconButton(onClick = onClose, modifier = Modifier.size(48.dp)) {
            Icon(Icons.Default.Close, uiText("Close"), Modifier.size(24.dp))
        }
    }
}
