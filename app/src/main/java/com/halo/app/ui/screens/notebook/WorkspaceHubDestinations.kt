package com.halo.app.ui.screens.notebook

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.halo.app.R
import com.halo.app.language.uiText

/** Presentation choices over the existing Notebook, collections and Memory owners. */
internal enum class WorkspaceHubDestination(val label: String) {
    NOTES("Notes"), FILES("Files"), CANVAS("Canvas"), COLLECTIONS("Collections"), INBOX("Inbox"), MEMORY("Memory")
}

@Composable
internal fun WorkspaceHubDestinations(onSelect: (WorkspaceHubDestination) -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.workspace49_open), style = MaterialTheme.typography.titleSmall)
        val columns = if (LocalDensity.current.fontScale >= 1.4f) 1 else 2
        WorkspaceHubDestination.entries.chunked(columns).forEach { group ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                group.forEach { destination ->
                    OutlinedButton(onClick = { onSelect(destination) },
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
                        Text(if (destination == WorkspaceHubDestination.COLLECTIONS)
                            stringResource(R.string.workspace49_collections) else uiText(destination.label))
                    }
                }
            }
        }
        Text(stringResource(R.string.workspace49_memory_scope), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Surface(Modifier.fillMaxWidth(), shape = com.halo.app.ui.design.HaloShapes.card,
            color = MaterialTheme.colorScheme.surfaceContainerLow) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.workspace49_studio), style = MaterialTheme.typography.titleSmall)
                Text(stringResource(R.string.workspace49_studio_unavailable), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
