package com.halo.app.ui.screens.notebook

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.halo.app.language.uiText

/** The editor remains composed when tools are hidden, preserving draft and keyboard focus. */
@Composable
internal fun EditorWorkspaceHeader(title: String, expanded: Boolean, onToggle: () -> Unit,
    onBack: () -> Unit, onHalo: (() -> Unit)? = null,
    subtitle: String? = null, onTitleClick: (() -> Unit)? = null,
    fallbackTitle: String = "Untitled") {
    Surface(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        shape = com.halo.app.ui.design.HaloShapes.card,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
            Icon(androidx.compose.ui.res.painterResource(com.halo.app.R.drawable.halo_ic_back), contentDescription = uiText("Back"))
        }
        Column(
            Modifier.weight(1f).then(
                if (onTitleClick == null) Modifier else
                    Modifier.heightIn(min = 48.dp).clickable(role = Role.Button, onClickLabel = uiText("Rename"), onClick = onTitleClick)
            ),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(title.ifBlank { uiText(fallbackTitle) }, style = MaterialTheme.typography.titleMedium,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (!subtitle.isNullOrBlank()) {
                Text(subtitle, style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (onHalo != null) IconButton(onClick = onHalo) {
            com.halo.app.ui.icons.HaloIcon(com.halo.app.ui.icons.HaloGlyph.HALO, uiText("Think with Halo"), Modifier.size(24.dp))
        }
        val label = uiText("Tools")
        val toolState = uiText(if (expanded) "Hide tools" else "More tools")
        IconButton(onClick = onToggle, modifier = Modifier.size(48.dp).semantics { stateDescription = toolState }) {
            Icon(androidx.compose.ui.res.painterResource(com.halo.app.R.drawable.halo_ic_more), label, Modifier.size(24.dp))
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
    Column(Modifier.fillMaxWidth().heightIn(max = (available * 0.3f).coerceAtMost(120.dp))
        .verticalScroll(rememberScrollState()), content = content)
}
