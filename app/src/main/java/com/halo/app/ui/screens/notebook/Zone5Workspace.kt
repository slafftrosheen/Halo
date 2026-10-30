package com.halo.app.ui.screens.notebook

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import com.halo.app.R
import com.halo.app.language.uiText
import com.halo.app.ui.icons.HaloGlyph
import com.halo.app.ui.zone5.Zone5Icon
import com.halo.app.ui.zone5.Zone5PageHeading

@Composable
internal fun WorkspaceHeadingAndTabs(library: Boolean, onSelect: (Boolean) -> Unit) {
    Zone5PageHeading(uiText("Workspace"), stringResource(R.string.zone5_workspace_subtitle))
    val stackedTabs = LocalDensity.current.fontScale >= 1.4f
    val tabs: @Composable (Modifier) -> Unit = { tabModifier ->
        listOf(true, false).forEach { isLibrary ->
            val selected = library == isLibrary
            val colors = MaterialTheme.colorScheme
            Surface(onClick = { onSelect(isLibrary) }, color = Color.Transparent,
                contentColor = if (selected) colors.primary else colors.onSurfaceVariant,
                modifier = tabModifier.semantics { this.selected = selected; role = Role.Tab }) {
                Column {
                    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 8.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Zone5Icon(if (isLibrary) "nav_workspace" else "action_tools", HaloGlyph.TOOL, Modifier.size(24.dp))
                        Text(uiText(if (isLibrary) "Library" else "Tools"), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                    }
                    HorizontalDivider(thickness = if (selected) 3.dp else 1.dp,
                        color = if (selected) colors.primary else colors.outlineVariant)
                }
            }
        }
    }
    if (stackedTabs) Column(Modifier.fillMaxWidth()) { tabs(Modifier.fillMaxWidth()) }
    else Row(Modifier.fillMaxWidth()) { tabs(Modifier.weight(1f)) }
}

/** Rows navigate straight to existing owners. No suite state or operation lives here. */
@Composable
internal fun WorkspaceToolsList(onStudy: () -> Unit, onMemory: () -> Unit,
    onStudio: (() -> Unit)?, onOffice: (() -> Unit)?) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(stringResource(R.string.zone5_choose_tool), style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.semantics { heading() })
        Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = MaterialTheme.shapes.medium) {
            Column {
                WorkspaceInstrumentRow("Study", stringResource(R.string.zone5_study_purpose), "tool_study", onStudy)
                HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                WorkspaceInstrumentRow("Memory", stringResource(R.string.zone5_memory_purpose), "tool_memory", onMemory)
                HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                WorkspaceInstrumentRow("Halo Studio", stringResource(R.string.zone5_studio_purpose), "tool_studio", onStudio)
                HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                WorkspaceInstrumentRow("Office Tools", stringResource(R.string.zone5_office_purpose), "tool_office", onOffice)
            }
        }
        Text(stringResource(R.string.zone5_creations_in_library), style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun WorkspaceInstrumentRow(title: String, purpose: String, icon: String, open: (() -> Unit)?) {
    Surface(onClick = { open?.invoke() }, enabled = open != null, color = Color.Transparent,
        modifier = Modifier.fillMaxWidth().heightIn(min = 88.dp)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.primary) {
                Zone5Icon(icon, HaloGlyph.TOOL, Modifier.size(40.dp))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(uiText(title), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                Text(if (open == null) stringResource(R.string.zone5_host_unavailable) else purpose,
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Zone5Icon("action_forward", HaloGlyph.NEXT, Modifier.size(24.dp))
        }
    }
}
