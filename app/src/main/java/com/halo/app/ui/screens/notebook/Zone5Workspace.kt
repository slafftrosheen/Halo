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
import com.halo.app.ui.zone6.editZone6PageOnHold

@Composable
internal fun WorkspaceHeadingAndTabs(library: Boolean, onSelect: (Boolean) -> Unit, onEdit: (() -> Unit)? = null) {
    Zone5PageHeading(uiText("Workspace"), stringResource(R.string.zone5_workspace_subtitle),
        modifier = onEdit?.let { Modifier.editZone6PageOnHold(it) } ?: Modifier)
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

/** Workspace navigation belongs to the Zone; the working area contains saved objects or instruments. */
@Composable
internal fun WorkspaceSectionSwitcher(library: Boolean, onSelect: (Boolean) -> Unit) {
    val tabs: @Composable (Modifier) -> Unit = { tabModifier ->
        listOf(true, false).forEach { isLibrary ->
            val selected = library == isLibrary
            val ink = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            Surface(onClick = { onSelect(isLibrary) }, color = Color.Transparent, contentColor = ink,
                modifier = tabModifier.semantics { role = Role.Tab; this.selected = selected }) {
                Column {
                    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Zone5Icon(if (isLibrary) "nav_workspace" else "action_tools", HaloGlyph.TOOL, Modifier.size(24.dp))
                        Text(uiText(if (isLibrary) "Library" else "Tools"), Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
                    }
                    HorizontalDivider(thickness = if (selected) 3.dp else 1.dp,
                        color = if (selected) ink else MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
    }
    if (LocalDensity.current.fontScale >= 1.4f) Column(Modifier.fillMaxWidth()) { tabs(Modifier.fillMaxWidth()) }
    else Row(Modifier.fillMaxWidth()) { tabs(Modifier.weight(1f)) }
}

/** Native entry points keep each tool with its own data and permission owner. */
@Composable
internal fun WorkspaceToolsList(
    onStudy: () -> Unit,
    onMemory: () -> Unit,
    onStudio: (() -> Unit)?,
    onOffice: (() -> Unit)?,
    onCanvas: (() -> Unit)? = null,
    onEveryday: ((String) -> Unit)? = null,
    easy: Boolean = false,
    onEdit: (() -> Unit)? = null,
) {
    com.halo.app.ui.toolkit.HaloToolkitShelf(easy, onEdit) { route ->
        when (route) {
            "studio" -> onStudio?.invoke()
            "file_tools" -> onOffice?.invoke()
            "canvases" -> onCanvas?.invoke()
            "study" -> onStudy()
            "notebook/memory" -> onMemory()
            else -> onEveryday?.invoke(route)
        }
    }
}
