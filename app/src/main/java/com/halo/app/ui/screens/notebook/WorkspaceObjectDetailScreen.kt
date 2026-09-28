package com.halo.app.ui.screens.notebook

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.halo.app.data.memory.MemoryRecord
import com.halo.app.data.notebook.WorkspaceObject
import com.halo.app.data.workspace.ContentOwnership
import com.halo.app.data.workspace.WorkspaceFile
import com.halo.app.data.workspace.WorkspaceFileAvailability
import com.halo.app.notebook.WorkspaceUseCases
import com.halo.app.ui.workspace.HaloWorkspaceButton
import com.halo.app.ui.workspace.HaloWorkspaceCard
import com.halo.app.ui.workspace.HaloWorkspaceHeader
import kotlinx.coroutines.launch
import java.util.Locale

private sealed interface WorkspaceDetailState {
    data object Loading : WorkspaceDetailState
    data object Missing : WorkspaceDetailState
    data class Failed(val message: String) : WorkspaceDetailState
    data class Ready(
        val item: WorkspaceObject?,
        val file: WorkspaceFile?,
        val memory: MemoryRecord?
    ) : WorkspaceDetailState
}

@Composable
fun WorkspaceObjectDetailScreen(
    id: String,
    type: String,
    workspace: WorkspaceUseCases,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var revision by remember(id, type) { mutableIntStateOf(0) }
    var actionBusy by remember(id, type) { mutableStateOf(false) }
    var actionMessage by remember(id, type) { mutableStateOf<String?>(null) }
    var confirmForget by remember(id) { mutableStateOf(false) }

    val detailState by produceState<WorkspaceDetailState>(
        initialValue = WorkspaceDetailState.Loading,
        key1 = id,
        key2 = type,
        key3 = revision
    ) {
        value = runCatching {
            val item = workspace.getObject(id)
            val file = if (type == "file") workspace.getFile(id) else null
            val memory = if (type == "memory") workspace.getMemory(id) else null
            if (item == null && file == null && memory == null) {
                WorkspaceDetailState.Missing
            } else {
                WorkspaceDetailState.Ready(item, file, memory)
            }
        }.getOrElse { WorkspaceDetailState.Failed(it.message ?: "Could not load this item") }
    }

    val relinkLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null && !actionBusy) {
            actionBusy = true
            actionMessage = null
            scope.launch {
                runCatching { workspace.relinkFile(id, uri) }
                    .onSuccess {
                        actionMessage = "File relinked"
                        revision++
                    }
                    .onFailure { actionMessage = it.message ?: "Could not relink the file" }
                actionBusy = false
            }
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 20.dp),
        contentPadding = PaddingValues(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            val ready = detailState as? WorkspaceDetailState.Ready
            HaloWorkspaceHeader(
                title = ready?.item?.title
                    ?: ready?.file?.displayName
                    ?: if (type == "memory") "Memory" else "File",
                subtitle = if (type == "memory") "What Halo can remember" else "File details",
                onBack = onBack
            )
        }

        when (val state = detailState) {
            WorkspaceDetailState.Loading -> item {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator()
                    Text(
                        text = "Loading…",
                        modifier = Modifier.padding(top = 12.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            WorkspaceDetailState.Missing -> item {
                DetailMessageCard(
                    title = "This item is unavailable",
                    message = "It may have been moved, deleted, or removed from the workspace."
                )
            }

            is WorkspaceDetailState.Failed -> item {
                DetailMessageCard(title = "Could not load this item", message = state.message)
            }

            is WorkspaceDetailState.Ready -> {
                if (type == "file") {
                    item {
                        val file = state.file
                        if (file == null) {
                            DetailMessageCard(
                                title = "File is unavailable",
                                message = "The workspace entry exists, but its file link is missing."
                            )
                        } else {
                            FileDetailCard(
                                file = file,
                                busy = actionBusy,
                                onOpen = {
                                    actionMessage = runCatching {
                                        context.startActivity(
                                            Intent(Intent.ACTION_VIEW)
                                                .setDataAndType(file.uri, file.mimeType)
                                                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        )
                                        null
                                    }.getOrElse { "No installed app can open this file" }
                                },
                                onKeepLocal = {
                                    actionBusy = true
                                    actionMessage = null
                                    scope.launch {
                                        runCatching { workspace.keepLocalCopy(id) }
                                            .onSuccess {
                                                actionMessage = "A local copy is now stored in Halo"
                                                revision++
                                            }
                                            .onFailure { actionMessage = it.message ?: "Could not keep a local copy" }
                                        actionBusy = false
                                    }
                                },
                                onRelink = { relinkLauncher.launch(arrayOf("*/*")) }
                            )
                        }
                    }
                } else if (type == "memory") {
                    item {
                        val memory = state.memory
                        if (memory == null) {
                            DetailMessageCard(
                                title = "Memory is unavailable",
                                message = "It may already have been forgotten."
                            )
                        } else {
                            MemoryDetailCard(
                                memory = memory,
                                busy = actionBusy,
                                onSave = { correction ->
                                    actionBusy = true
                                    actionMessage = null
                                    scope.launch {
                                        runCatching { workspace.correctMemory(id, correction) }
                                            .onSuccess { saved ->
                                                actionMessage = if (saved) "Memory updated" else "Memory could not be updated"
                                                if (saved) revision++
                                            }
                                            .onFailure { actionMessage = it.message ?: "Could not update memory" }
                                        actionBusy = false
                                    }
                                },
                                onForget = { confirmForget = true }
                            )
                        }
                    }
                }
            }
        }

        actionMessage?.let { message ->
            item {
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }
        }
    }

    if (confirmForget) {
        AlertDialog(
            onDismissRequest = { if (!actionBusy) confirmForget = false },
            title = { Text("Forget this memory?") },
            text = { Text("Halo will stop using it as remembered context. This cannot be undone.") },
            confirmButton = {
                TextButton(
                    enabled = !actionBusy,
                    onClick = {
                        actionBusy = true
                        scope.launch {
                            val forgotten = runCatching { workspace.forgetMemory(id) }.getOrDefault(false)
                            actionBusy = false
                            confirmForget = false
                            if (forgotten) onBack() else actionMessage = "Could not forget this memory"
                        }
                    }
                ) { Text("Forget") }
            },
            dismissButton = {
                TextButton(enabled = !actionBusy, onClick = { confirmForget = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun FileDetailCard(
    file: WorkspaceFile,
    busy: Boolean,
    onOpen: () -> Unit,
    onKeepLocal: () -> Unit,
    onRelink: () -> Unit
) {
    val canOpen = file.availability == WorkspaceFileAvailability.AVAILABLE ||
        file.availability == WorkspaceFileAvailability.OWNED_LOCAL
    HaloWorkspaceCard {
        Text("File information", style = MaterialTheme.typography.titleMedium)
        Text(
            text = listOf(file.mimeType, formatFileSize(file.sizeBytes)).joinToString(" · "),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = when (file.ownership) {
                ContentOwnership.HALO_OWNED -> "Stored safely in Halo"
                ContentOwnership.EXTERNAL_REFERENCE -> "Linked from another app"
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = file.availability.name.lowercase().replace('_', ' ').replaceFirstChar { it.titlecase() },
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.secondary
        )
        HaloWorkspaceButton(
            text = when {
                busy -> "Working…"
                canOpen -> "Open file"
                else -> "File unavailable"
            },
            onClick = onOpen,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            enabled = !busy && canOpen
        )
        if (file.ownership != ContentOwnership.HALO_OWNED) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                HaloWorkspaceButton(
                    text = "Keep a copy",
                    onClick = onKeepLocal,
                    modifier = Modifier.weight(1f),
                    enabled = !busy && canOpen,
                    primary = false
                )
                HaloWorkspaceButton(
                    text = "Relink",
                    onClick = onRelink,
                    modifier = Modifier.weight(1f),
                    enabled = !busy,
                    primary = false
                )
            }
        }
    }
}

@Composable
private fun MemoryDetailCard(
    memory: MemoryRecord,
    busy: Boolean,
    onSave: (String) -> Unit,
    onForget: () -> Unit
) {
    var correction by remember(memory.id, memory.content) { mutableStateOf(memory.content) }
    HaloWorkspaceCard {
        Text("Remembered context", style = MaterialTheme.typography.titleMedium)
        Text(
            text = "${memory.kind.name.lowercase().replaceFirstChar { it.titlecase() }} · Revision ${memory.sourceRevision ?: 1}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        OutlinedTextField(
            value = correction,
            onValueChange = { correction = it },
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
            label = { Text("Memory") },
            minLines = 4,
            enabled = !busy
        )
        HaloWorkspaceButton(
            text = if (busy) "Saving…" else "Save changes",
            onClick = { onSave(correction.trim()) },
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
            enabled = !busy && correction.isNotBlank() && correction.trim() != memory.content
        )
        TextButton(onClick = onForget, enabled = !busy) { Text("Forget this memory") }
    }
}

@Composable
private fun DetailMessageCard(title: String, message: String) {
    HaloWorkspaceCard {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun formatFileSize(bytes: Long?): String {
    if (bytes == null || bytes < 0L) return "Size unknown"
    if (bytes < 1_024L) return "$bytes B"
    val kilobytes = bytes / 1_024.0
    if (kilobytes < 1_024.0) return String.format(Locale.getDefault(), "%.1f KB", kilobytes)
    return String.format(Locale.getDefault(), "%.1f MB", kilobytes / 1_024.0)
}
