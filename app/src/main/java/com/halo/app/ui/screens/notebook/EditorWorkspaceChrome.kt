package com.halo.app.ui.screens.notebook

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.halo.app.language.uiText

/** The editor remains composed when tools are hidden, preserving draft and keyboard focus. */
@Composable
internal fun EditorWorkspaceHeader(title: String, expanded: Boolean, onToggle: () -> Unit,
    onBack: () -> Unit) {
    Surface(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        shape = com.halo.app.ui.design.HaloShapes.card,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = uiText("Back"))
        }
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
        val label = uiText(if (expanded) "Hide tools" else "More tools")
        TextButton(onClick = onToggle, modifier = Modifier.heightIn(min = 48.dp).semantics { stateDescription = label }) {
            Text(label, style = MaterialTheme.typography.labelLarge)
        }
    }
    }
}

/** Even an open menu cannot consume the whole editor in landscape or above the IME. */
@Composable
internal fun EditorToolsPanel(content: @Composable ColumnScope.() -> Unit) {
    val density = LocalDensity.current
    val keyboard = with(density) { WindowInsets.ime.getBottom(density).toDp() }
    val available = (LocalConfiguration.current.screenHeightDp.dp - keyboard).coerceAtLeast(120.dp)
    Column(Modifier.fillMaxWidth().heightIn(max = available * 0.4f)
        .verticalScroll(rememberScrollState()), content = content)
}
