package com.halo.app.ui.screens.notebook

import com.halo.app.ui.presence.BindShellChildPage
import com.halo.app.ui.presence.ShellPageAction
import com.halo.app.ui.icons.HaloGlyph
import com.halo.app.ui.components.HaloTextField
import com.halo.app.ui.components.VoiceFieldKind
import com.halo.app.language.uiText

import androidx.lifecycle.compose.collectAsStateWithLifecycle

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import com.halo.app.ui.zone5.*
import com.halo.app.R
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
import androidx.compose.ui.res.stringResource
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
    onStudio: ((String) -> Unit)? = null,
    onFileTools: ((String) -> Unit)? = null,
    memoryDetailModel: MemoryDetailViewModel? = null,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var revision by remember(id, type) { mutableIntStateOf(0) }
    var actionBusy by remember(id, type) { mutableStateOf(false) }
    var actionMessage by remember(id, type) { mutableStateOf<String?>(null) }
    var forgottenMoment by remember(id) { mutableStateOf(false) }
    var confirmForget by remember(id) { mutableStateOf(false) }
    var editingMemory by rememberSaveable(id) { mutableStateOf(false) }
    var correction by rememberSaveable(id) { mutableStateOf("") }
    var editedIdentity by rememberSaveable(id) { mutableStateOf<String?>(null) }
    var forgetIdentity by rememberSaveable(id) { mutableStateOf<String?>(null) }
    var showSources by rememberSaveable(id) { mutableStateOf(false) }
    // Root lifetime: scrolling a card out of view must not remove its action owner.
    val model = if (type == "memory") memoryDetailModel ?: memoryDetailOwner(id) else null
    val memoryState = model?.state?.collectAsStateWithLifecycle()?.value
    val memory = memoryState?.item?.record
    val memoryIdentity = memory?.let(::memoryEditIdentity)
    val changedMessage = stringResource(R.string.zone5_memory_changed)
    val beginEdit: () -> Unit = {
        memory?.let { correction = it.content; editedIdentity = memoryEditIdentity(it); editingMemory = true }
    }
    val beginForget: () -> Unit = {
        memory?.let { forgetIdentity = memoryEditIdentity(it); confirmForget = true }
    }

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

    val currentDetail = detailState as? WorkspaceDetailState.Ready
    val detailBusy = actionBusy || memoryState?.busy == true
    val shellHosted = BindShellChildPage(currentDetail?.item?.title ?: currentDetail?.file?.displayName
        ?: uiText(if (type == "memory") "Memory" else "File"), onBack, objectId = id, titleIsSource = true,
        instrumentHeader = type == "memory",
        canonicalToolIds = if (type == "memory") listOf("halo_read_memory", "halo_update_memory", "halo_forget_memory") else null,
        disabledActionReasons = if (type == "memory") mapOf("object.memory.source" to stringResource(R.string.zone5_no_memory_source)) else emptyMap(),
        sourceIdentity = "$id:$type:$revision:${currentDetail?.file?.version}:$memoryIdentity:$detailBusy:$editingMemory:$confirmForget:$showSources:${memoryState?.failed}",
        actions = buildList {
            if (type == "memory") {
                add(ShellPageAction("object.memory.source", "Sources", HaloGlyph.FILE,
                    !detailBusy && memoryState?.sources?.any { it.objectRef != null } == true) { showSources = true })
                add(ShellPageAction("object.memory.edit", "Correct", HaloGlyph.NOTE, !detailBusy && memory != null, beginEdit))
                add(ShellPageAction("object.memory.forget", "Forget", HaloGlyph.TRASH, !detailBusy && memory != null, beginForget))
            }
            add(ShellPageAction("object.refresh", "Refresh", HaloGlyph.NOTE,
                !detailBusy && detailState !is WorkspaceDetailState.Loading) { revision++; model?.retry() })
        })

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
            if (!shellHosted) HaloWorkspaceHeader(
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
                if (type == "file" && state.file != null) item {
                    com.halo.app.ui.workspace.WorkspaceContinueButton(id, state.file.version, enabled = !actionBusy)
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
                            if (memoryState?.loading == true) CircularProgressIndicator()
                            else if (memoryState?.failed == true) {
                                Text(stringResource(R.string.zone5_memory_load_error), color = MaterialTheme.colorScheme.error)
                                Zone5ActionButton(uiText("Try again"), "action_refresh", onClick = { model?.retry() })
                            } else if (memoryState?.item == null) Text(uiText("Memory is unavailable"))
                            else {
                                if (shellHosted) Zone5InstrumentHeading(uiText("Memory"), "tool_memory", state = uiText("What Halo can remember"))
                                MemoryDetailCard(memoryState, detailBusy, onEdit = beginEdit, onConfirm = { model?.confirm() },
                                    onForget = beginForget, onOpenSource = onOpenSource)
                            }
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

    if (showSources) AlertDialog(onDismissRequest = { showSources = false }, title = { Text(uiText("Sources")) },
        text = { Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
            if (memoryState?.sources.isNullOrEmpty()) Text(stringResource(R.string.zone5_no_memory_source))
            memoryState?.sources.orEmpty().forEach { source ->
                Text(uiText(source.label))
                if (source.available == false) Text(uiText("Original source is no longer available"))
                source.objectRef?.let { ref -> Zone5ActionButton(ref.title, "study_source", !detailBusy) { showSources = false; onOpenSource(ref) } }
            }
        } }, confirmButton = { TextButton({ showSources = false }) { Text(uiText("Close")) } })
    if (editingMemory) AlertDialog(onDismissRequest = { if (!detailBusy) editingMemory = false },
        title = { Text(uiText("Correct memory")) }, text = {
            Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                Text(uiText("Current")); Text(memory?.content.orEmpty())
                HaloTextField(correction, { correction = it }, enabled = !detailBusy, label = { Text(uiText("Correction")) },
                    modifier = Modifier.fillMaxWidth(), maxLines = 6, kind = VoiceFieldKind.TEXT)
                if (correction.length >= 900) Text("${correction.length}/1000")
                if (memoryIdentity != editedIdentity) Text(changedMessage, color = MaterialTheme.colorScheme.error)
                actionMessage?.let { Text(uiText(it)) }
            }
        }, confirmButton = { TextButton(enabled = !detailBusy && memory != null && memoryIdentity == editedIdentity &&
            correction.trim().length in 1..1000 && correction.trim() != memory.content, onClick = {
                actionBusy = true; actionMessage = null
                scope.launch {
                    try {
                        if (workspace.getMemory(id)?.let(::memoryEditIdentity) != editedIdentity) actionMessage = changedMessage
                        else if (workspace.correctMemory(id, correction.trim())) { revision++; editingMemory = false }
                        else actionMessage = "Memory could not be updated"
                    } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
                    catch (_: Exception) { actionMessage = "Could not update memory" }
                    finally { actionBusy = false }
                }
            }) { Text(uiText("Save correction")) } },
        dismissButton = { TextButton({ editingMemory = false }, enabled = !detailBusy) { Text(uiText("Cancel")) } })

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
            text = { Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                Text(uiText("halo will stop using this memory. Your notes are kept. Copies in existing backups follow your storage settings."))
                if (memoryIdentity != forgetIdentity) Text(changedMessage, color = MaterialTheme.colorScheme.error)
            } },
            confirmButton = {
                TextButton(
                    enabled = !detailBusy && memory != null && memoryIdentity == forgetIdentity,
                    onClick = {
                        actionBusy = true
                        scope.launch {
                            val forgotten = try {
                                if (workspace.getMemory(id)?.let(::memoryEditIdentity) != forgetIdentity) {
                                    actionMessage = changedMessage; false
                                } else workspace.forgetMemory(id)
                            } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
                            catch (_: Exception) { false }
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
    HaloWorkspaceCard {
        Text(uiText("What halo can read"), style = MaterialTheme.typography.titleMedium)
        if (file.mimeType.startsWith("image/")) {
            HaloWorkspaceButton(text = uiText("Attach image to Home"), enabled = canOpen && !busy, onClick = {
                mediaScope.launch {
                    try {
                        com.halo.app.di.ManualDependencyGraph.visualAttachments.attach(file.uri, "Workspace",
                            com.halo.sdk.runtime.multimodal.MediaProvenance.WORKSPACE, file.objectId.value)
                        mediaMessage = "Image attached. Open Home to ask about it or remove it."
                    } catch (_: Exception) { mediaMessage = "Image could not be attached." }
                }
            })
        }
        if (file.mimeType.startsWith("image/") || file.mimeType.startsWith("text/") || file.mimeType in setOf("application/pdf", "application/json")) {
            Text(uiText("File sharing follows the Private / Full Power switch at the top of Halo."),
                style = MaterialTheme.typography.bodySmall)
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
            text = uiText(when {
                busy -> "Working…"
                canOpen -> "Open file"
                else -> "File unavailable"
            }),
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
private fun memoryDetailOwner(id: String): MemoryDetailViewModel {
    val graph = com.halo.app.di.ManualDependencyGraph
    return androidx.lifecycle.viewmodel.compose.viewModel(key = "memory-detail-$id", factory = object : androidx.lifecycle.ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST") override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T = MemoryDetailViewModel(id, graph.memoryRepository,
            scopeName = { scope, sourceId -> if (scope == com.halo.app.data.memory.MemoryScope.DEVICE) graph.deviceRegistry.getDevice(sourceId)?.identity?.displayName else graph.workspaceUseCases.getObject(sourceId)?.takeIf { it.lifecycle == com.halo.app.data.notebook.WorkspaceObjectLifecycle.ACTIVE }?.title },
            exists = graph.personalLearning::exists, sourceObject = graph.workspaceUseCases::getObject) as T
    })
}

private fun memoryEditIdentity(record: MemoryRecord): String =
    "${record.id}:${record.updatedAt}:${record.content.hashCode()}:${record.kind}:${record.scope}:${record.scopeId}"

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MemoryDetailCard(detail: MemoryDetailUi, busy: Boolean, onEdit: () -> Unit, onConfirm: () -> Unit,
    onForget: () -> Unit, onOpenSource: (WorkspaceObject) -> Unit) {
    val item = detail.item ?: return
    val copy = memoryDetailCopy(item)
    var history by rememberSaveable(item.record.id) { mutableStateOf(false) }
    HaloWorkspaceCard {
        Text(copy.kind, style = MaterialTheme.typography.labelMedium)
        Text(item.record.content, style = MaterialTheme.typography.bodyLarge)
        com.halo.app.ui.components.HaloReadAloud(item.record.content)
        if (item.stale) Text(uiText("Needs review · This memory may be outdated."))
        Text(uiText("Why Halo remembers this"), style = MaterialTheme.typography.titleMedium)
        Text(copy.origin)
        Text(copy.confirmation)
        copy.lastConfirmed?.let { Text(it) }
        Text(copy.confidence, style = MaterialTheme.typography.bodySmall)
        Text(uiText("Where it applies"), style = MaterialTheme.typography.titleMedium)
        Text(copy.scope)
        Text(uiText("Usage"), style = MaterialTheme.typography.titleMedium)
        Text(copy.usage)
        Text(uiText("Used means this memory was available as context. It does not prove that it changed an answer or caused an action."), style = MaterialTheme.typography.bodySmall)
        Text(uiText("Sources"), style = MaterialTheme.typography.titleMedium)
        if (detail.sources.isEmpty()) Text(uiText("No source detail available"))
        detail.sources.forEach { source ->
            Text(uiText(source.label))
            if (source.available == false) Text(uiText("Original source is no longer available"), style = MaterialTheme.typography.bodySmall)
            source.objectRef?.let { objectRef -> TextButton({ onOpenSource(objectRef) }) { Text(uiText("Open source: %1\$s", objectRef.title)) } }
        }
        TextButton({ history = !history }) { Text(uiText(if (history) "Hide history" else "History: %1\$d changes", detail.revisions.size)) }
        if (history) detail.revisions.forEach { revision ->
            Text(MemoryPresentation.date(revision.changedAt))
            Text(uiText(if (revision.reason == "User accepted updated memory") "You approved an updated suggestion" else "You corrected this"))
            Text(uiText("Before")); Text(revision.previousContent); Text(uiText("After")); Text(revision.newContent)
        }
        detail.message?.let { Text(it) }
        // NB07: memory confirmation, correction and Forget stay individually
        // reachable at 200% text; none of them implies another action.
        FlowRow(Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (!item.record.userConfirmed || item.stale)
                TextButton(enabled = !busy && !detail.busy, onClick = onConfirm,
                    modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(uiText(if (item.stale) "Confirm still true" else "Confirm"))
                }
            TextButton(enabled = !busy, onClick = onEdit,
                modifier = Modifier.heightIn(min = 48.dp)) { Text(uiText("Correct")) }
            TextButton(enabled = !busy, onClick = onForget,
                modifier = Modifier.heightIn(min = 48.dp)) { Text(uiText("Forget")) }
        }
        if (item.stale) Text(uiText("Confirmation keeps the original expiry rules. Correct the information if it has changed."), style = MaterialTheme.typography.bodySmall)
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
