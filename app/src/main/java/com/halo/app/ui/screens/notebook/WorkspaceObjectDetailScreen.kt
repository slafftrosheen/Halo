package com.halo.app.ui.screens.notebook

import com.halo.app.ui.components.HaloTextField
import com.halo.app.ui.components.VoiceFieldKind
import com.halo.app.language.uiText

import androidx.lifecycle.compose.collectAsStateWithLifecycle

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import com.halo.app.ui.design.HaloFeedbackSurface
import com.halo.app.ui.design.HaloFeedbackKind
import com.halo.app.ui.design.HaloHeaderAction
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
    onOpenSource: (WorkspaceObject) -> Unit = {},
    onStudy: ((String) -> Unit)? = null,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var revision by remember(id, type) { mutableIntStateOf(0) }
    var actionBusy by remember(id, type) { mutableStateOf(false) }
    var actionMessage by remember(id, type) { mutableStateOf<String?>(null) }
    var forgottenMoment by remember(id) { mutableStateOf(false) }
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
        }.getOrElse { if (it is kotlinx.coroutines.CancellationException) throw it; WorkspaceDetailState.Failed("Could not load this item") }
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
                    .onFailure { if (it is kotlinx.coroutines.CancellationException) throw it; actionMessage = "Could not relink the file" }
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
                        text = uiText("Loading…"),
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
                HaloFeedbackSurface(uiText("Could not load this item"), uiText("Your item is unchanged. Try loading it again."),
                    kind = HaloFeedbackKind.FAILURE, action = HaloHeaderAction(uiText("Retry")) { revision++ })
            }

              is WorkspaceDetailState.Ready -> {
                if(type == "file") item { TextButton(onClick={ scope.launch {
                    try {
                        val graph = com.halo.app.di.ManualDependencyGraph
                        graph.interactionContext.selectWorkspace(id)
                        graph.interactionContext.navigate(com.halo.runtime.cognition.context.ContextualNextActionTarget.FollowUp(
                            requireNotNull(graph.conversationStore.activeConversationId())))
                    } catch(error: Exception) { actionMessage = error.message ?: "Source unavailable" }
                } }) { Text(uiText("Use in conversation")) } }
                if (type == "file" && onStudy != null && state.file?.let { it.mimeType.startsWith("text/") || it.mimeType.startsWith("image/") || it.mimeType in setOf("application/json", "application/pdf") } == true) {
                    item { TextButton({ onStudy(id) }) { Text(uiText("Study this")) } }
                }
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
                                            .onFailure { if (it is kotlinx.coroutines.CancellationException) throw it; actionMessage = "Could not keep a local copy" }
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
                                onForget = { confirmForget = true },
                                onOpenSource = onOpenSource
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

    if (forgottenMoment) androidx.compose.ui.window.Dialog(onDismissRequest = onBack) {
        androidx.compose.material3.Surface(shape = com.halo.app.ui.design.HaloShapes.card) {
            Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                com.halo.app.ui.fx.HaloPackageFx(com.halo.app.ui.fx.HaloPackageMoment.FORGET_MEMORY, Modifier.size(80.dp))
                Text(uiText("Forgotten. Your notes are kept."))
            }
        }
    }
    if (confirmForget) {
        AlertDialog(
            onDismissRequest = { if (!actionBusy) confirmForget = false },
            title = { Text(uiText("Forget this memory?")) },
            text = { Text(uiText("halo will stop using this memory. Your notes are kept. Copies in existing backups follow your storage settings.")) },
            confirmButton = {
                TextButton(
                    enabled = !actionBusy,
                    onClick = {
                        actionBusy = true
                        scope.launch {
                            val forgotten = runCatching { workspace.forgetMemory(id) }.getOrDefault(false)
                            actionBusy = false
                            confirmForget = false
                            if (forgotten) {
                                forgottenMoment = true
                                kotlinx.coroutines.delay(700)
                                onBack()
                            } else actionMessage = "Could not forget this memory"
                        }
                    }
                ) { Text(uiText("Forget")) }
            },
            dismissButton = {
                TextButton(enabled = !actionBusy, onClick = { confirmForget = false }) { Text(uiText("Cancel")) }
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
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
    val mediaScope = rememberCoroutineScope()
    var mediaMessage by remember(file.objectId) { mutableStateOf<String?>(null) }
    var mediaAccess by remember(file.objectId) { mutableStateOf(com.halo.app.di.ManualDependencyGraph.workspaceMediaAiPolicy.access(file.objectId.value)) }
    HaloWorkspaceCard {
        Text(uiText("What halo can read"), style = MaterialTheme.typography.titleMedium)
        if (file.mimeType.startsWith("image/")) {
            HaloWorkspaceButton(text = uiText("Attach image to Home"), enabled = canOpen && !busy, onClick = {
                mediaScope.launch {
                    try {
                        com.halo.app.di.ManualDependencyGraph.visualAttachments.attach(file.uri, "Notebook",
                            com.halo.sdk.runtime.multimodal.MediaProvenance.WORKSPACE, file.objectId.value)
                        mediaMessage = "Image attached. Open Home to ask about it or remove it."
                    } catch (_: Exception) { mediaMessage = "Image could not be attached." }
                }
            })
        }
        if (file.mimeType.startsWith("image/") || file.mimeType.startsWith("text/") || file.mimeType in setOf("application/pdf", "application/json")) {
            Text(uiText("Choose how halo can use this file."),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            FlowRow(Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)) {
                com.halo.app.data.workspace.WorkspaceAiAccess.entries.forEach { access ->
                    androidx.compose.material3.FilterChip(
                        selected = mediaAccess == access,
                        enabled = !busy,
                        modifier = Modifier.heightIn(min = 48.dp),
                        onClick = {
                            com.halo.app.di.ManualDependencyGraph.workspaceMediaAiPolicy
                                .setAccess(file.objectId.value, access)
                            mediaAccess = access
                        },
                        label = { Text(uiText(when (access) {
                            com.halo.app.data.workspace.WorkspaceAiAccess.LOCAL_ONLY -> "On this phone only"
                            com.halo.app.data.workspace.WorkspaceAiAccess.ALLOW_CONFIGURED_CLOUD -> "Allow my cloud models"
                            com.halo.app.data.workspace.WorkspaceAiAccess.EXCLUDE_FROM_AI -> "Do not use with AI"
                        })) },
                    )
                }
            }
            if (mediaAccess == com.halo.app.data.workspace.WorkspaceAiAccess.ALLOW_CONFIGURED_CLOUD)
                Text(uiText("Cloud use also needs your separate permission."),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            mediaMessage?.let { Text(uiText(it), style = MaterialTheme.typography.bodySmall) }
        }
        Text(
            text = uiText(when {
                file.mimeType.startsWith("image/") -> "Image"
                file.mimeType == "application/pdf" -> "PDF document"
                file.mimeType.startsWith("text/") -> "Text document"
                file.mimeType.startsWith("audio/") -> "Audio"
                file.mimeType.startsWith("video/") -> "Video"
                else -> "File"
            }) + " · " + formatFileSize(file.sizeBytes),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = uiText(when (file.ownership) {
                ContentOwnership.HALO_OWNED -> "Saved on this phone"
                ContentOwnership.EXTERNAL_REFERENCE -> "Linked from another app"
            }),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = uiText(when (file.availability) {
                WorkspaceFileAvailability.AVAILABLE, WorkspaceFileAvailability.OWNED_LOCAL -> "Ready to use"
                WorkspaceFileAvailability.NEEDS_RELINK -> "Find the file to reconnect it"
                WorkspaceFileAvailability.MISSING -> "This file is missing"
            }),
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
            if (!canOpen) Text(
                uiText("The original file is unavailable. Choose it again to reconnect."),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            FlowRow(modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (canOpen) HaloWorkspaceButton(
                    text = uiText("Keep a copy"),
                    onClick = onKeepLocal,
                    modifier = Modifier.heightIn(min = 48.dp),
                    enabled = !busy,
                    primary = false
                )
                HaloWorkspaceButton(
                    text = uiText(if (canOpen) "Choose another file" else "Find the file"),
                    onClick = onRelink,
                    modifier = Modifier.heightIn(min = 48.dp),
                    enabled = !busy,
                    primary = false
                )
            }
        }
    }
}

@Composable
private fun MemoryDetailCard(memory: MemoryRecord, busy: Boolean, onSave: (String) -> Unit,
    onForget: () -> Unit, onOpenSource: (WorkspaceObject) -> Unit) {
    val graph = com.halo.app.di.ManualDependencyGraph
    val model: MemoryDetailViewModel = androidx.lifecycle.viewmodel.compose.viewModel(key = "memory-detail-${memory.id}", factory = object : androidx.lifecycle.ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST") override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T = MemoryDetailViewModel(memory.id, graph.memoryRepository,
            scopeName = { scope, id -> if (scope == com.halo.app.data.memory.MemoryScope.DEVICE) graph.deviceRegistry.getDevice(id)?.identity?.displayName else graph.workspaceUseCases.getObject(id)?.takeIf { it.lifecycle == com.halo.app.data.notebook.WorkspaceObjectLifecycle.ACTIVE }?.title },
            exists = graph.personalLearning::exists, sourceObject = graph.workspaceUseCases::getObject) as T
    })
    val detail by model.state.collectAsStateWithLifecycle()
    val item = detail.item ?: MemoryPresentation.item(memory)
    var editing by rememberSaveable(memory.id) { mutableStateOf(false) }
    var history by rememberSaveable(memory.id) { mutableStateOf(false) }
    var correction by rememberSaveable(memory.id, memory.content) { mutableStateOf(memory.content) }
    HaloWorkspaceCard {
        Text(item.kindLabel, style = MaterialTheme.typography.labelMedium)
        Text(item.record.content, style = MaterialTheme.typography.bodyLarge)
        com.halo.app.ui.components.HaloReadAloud(item.record.content)
        if (item.stale) Text(uiText("Needs review · This memory may be outdated."))
        Text(uiText("Why Halo remembers this"), style = MaterialTheme.typography.titleMedium)
        Text(item.originLabel)
        Text(item.confirmationLabel)
        item.record.lastConfirmedAt?.let { Text("Last confirmed ${MemoryPresentation.date(it)}") }
        Text(item.confidenceLabel, style = MaterialTheme.typography.bodySmall)
        Text(uiText("Where it applies"), style = MaterialTheme.typography.titleMedium)
        Text(item.scopeLabel)
        Text(uiText("Usage"), style = MaterialTheme.typography.titleMedium)
        Text(item.usageSummary)
        Text(uiText("Used means this memory was available as context. It does not prove that it changed an answer or caused an action."), style = MaterialTheme.typography.bodySmall)
        Text(uiText("Sources"), style = MaterialTheme.typography.titleMedium)
        if (detail.sources.isEmpty()) Text(uiText("No source detail available"))
        detail.sources.forEach { source ->
            Text(source.label)
            if (source.available == false) Text(uiText("Original source is no longer available"), style = MaterialTheme.typography.bodySmall)
            source.objectRef?.let { objectRef -> TextButton({ onOpenSource(objectRef) }) { Text(uiText("Open source: %1\$s", objectRef.title)) } }
        }
        TextButton({ history = !history }) { Text(uiText(if (history) "Hide history" else "History: %1\$d changes", detail.revisions.size)) }
        if (history) detail.revisions.forEach { revision ->
            Text(MemoryPresentation.date(revision.changedAt))
            Text(if (revision.reason == "User accepted updated memory") "You approved an updated suggestion" else "You corrected this")
            Text(uiText("Before")); Text(revision.previousContent); Text(uiText("After")); Text(revision.newContent)
        }
        detail.message?.let { Text(it) }
        Row {
            if (!item.record.userConfirmed || item.stale) TextButton(enabled = !busy && !detail.busy, onClick = model::confirm) { Text(if (item.stale) "Confirm still true" else "Confirm") }
            TextButton(enabled = !busy, onClick = { editing = true }) { Text(uiText("Correct")) }
            TextButton(enabled = !busy, onClick = onForget) { Text(uiText("Forget")) }
        }
        if (item.stale) Text(uiText("Confirmation keeps the original expiry rules. Correct the information if it has changed."), style = MaterialTheme.typography.bodySmall)
    }
    if (editing) AlertDialog(onDismissRequest = { if (!busy) editing = false }, title = { Text(uiText("Correct memory")) }, text = {
        Column {
            Text(uiText("Current")); Text(item.record.content)
            HaloTextField(correction, { correction = it }, label = { Text(uiText("Correction")) }, modifier = Modifier.fillMaxWidth(), maxLines = 6, kind = VoiceFieldKind.TEXT)
            if (correction.length >= 900) Text("${correction.length}/1000")
        }
    }, confirmButton = { TextButton(enabled = !busy && correction.trim().length in 1..1000 && correction.trim() != item.record.content, onClick = { onSave(correction.trim()); editing = false }) { Text(uiText("Save correction")) } },
        dismissButton = { TextButton({ editing = false }) { Text(uiText("Cancel")) } })
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
