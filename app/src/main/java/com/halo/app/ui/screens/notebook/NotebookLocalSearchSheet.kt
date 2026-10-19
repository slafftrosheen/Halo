package com.halo.app.ui.screens.notebook

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.halo.providers.litert.models.LocalModelInventoryState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.halo.app.data.notebook.WorkspaceObjectLifecycle
import com.halo.app.data.notebook.WorkspaceObjectType
import com.halo.app.data.study.StudySessionRecord
import com.halo.app.di.ManualDependencyGraph
import com.halo.app.language.uiText
import com.halo.app.ui.components.HaloTextField
import com.halo.app.ui.components.VoiceFieldKind
import com.halo.providers.litert.models.LiteRtArtifactTask
import com.halo.providers.litert.models.LiteRtModelCatalog
import com.halo.runtime.cognition.workload.SemanticReference
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * A private, user-triggered Notebook feature, not a model diagnostic.
 * No source is indexed, embedded, or transferred merely by opening the sheet.
 */
internal data class LocalSearchSourceChoice(val id: String, val title: String)

internal fun notebookSearchSources(items: List<NotebookWorkspaceItem>,
    sessions: List<StudySessionRecord>): List<LocalSearchSourceChoice> {
    val workspace = items.mapNotNull { item ->
        val row = item.objectRef
        if (row.lifecycle != WorkspaceObjectLifecycle.ACTIVE) return@mapNotNull null
        val prefix = when (row.type) {
            WorkspaceObjectType.NOTE -> "note"
            WorkspaceObjectType.CANVAS -> "canvas"
            else -> return@mapNotNull null
        }
        LocalSearchSourceChoice("$prefix:${row.id.value}",row.title.ifBlank { "Untitled" })
    }
    val learning = sessions.map { session ->
        LocalSearchSourceChoice("study:${session.id}",session.goal?.takeIf { it.isNotBlank() } ?: "Study session")
    }
    return (workspace + learning).distinctBy { it.id }.take(64)
}

/** Maps technical error codes to user-facing English messages. No @Composable calls here. */
internal fun localSearchUserErrorMessage(error: Throwable): String = when (error.message) {
    "VOICE_INTERACTION_ACTIVE", "NATIVE_RESOURCE_BUSY", "UNLOAD_RESIDENT_LITERT_FIRST" ->
        "Finish the current voice or model response, then try local search again."
    "SEARCH_MODEL_NOT_VERIFIED", "SEARCH_MODEL_NOT_IN_CATALOG", "ARTIFACT_NOT_INSTALLED", "ARTIFACT_CHANGED" ->
        "The selected search model is not ready. Check the model download in On-device models."
    "SEARCH_MODEL_NOT_SUPPORTED_ON_DEVICE", "SEARCH_MODEL_DIMENSION_UNAVAILABLE", "NOT_AN_EMBEDDING_MODEL",
    "DIMENSION_UNSUPPORTED", "CPU_UNSUPPORTED", "TASK_NOT_EMBEDDING" ->
        "This model cannot run local search on this phone. Choose another embedding model."
    "SELECT_LOCAL_SEARCH_MODEL", "EMBEDDING_NATIVE_INSPECTION_REQUIRED" ->
        "Choose and prepare an installed embedding model first."
    "SELECT_LOCAL_SEARCH_SOURCES", "SEMANTIC_INDEX_OPT_IN_REQUIRED" ->
        "Choose which sources Halo may search, then build your local index."
    "MEMORY_NOT_ADMITTED", "INDEX_MEMORY_LIMIT" ->
        "Not enough free working memory. Close other models or select fewer sources."
    else -> "Local search could not finish. Your notes were not sent to any cloud service."
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun NotebookLocalSearchSheet(
    viewModel: NotebookViewModel,
    items: List<NotebookWorkspaceItem>,
    studySessions: List<StudySessionRecord>,
    onOpenModels: () -> Unit,
    onDismiss: () -> Unit,
) {
    val service = remember { ManualDependencyGraph.semanticWorkspace }
    val scope = rememberCoroutineScope()
    val choices = remember(items,studySessions) { notebookSearchSources(items,studySessions) }
    val access = remember { service.localSearchAccess() }
    var approved by remember { mutableStateOf(access.enabled) }
    var assistantContext by remember { mutableStateOf(access.assistantContextEnabled) }
    var approvedIds by remember { mutableStateOf(access.sourceIds) }
    var draftIds by remember { mutableStateOf(access.sourceIds) }
    var modelChoice by remember { mutableStateOf(service.selectedSearchModel()) }
    var modelId by remember { mutableStateOf(modelChoice?.modelId) }
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<SemanticReference>>(emptyList()) }
    var indexCount by remember { mutableIntStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var job by remember { mutableStateOf<Job?>(null) }
    val modelInventory by ManualDependencyGraph.modelManager.inventoryState.collectAsStateWithLifecycle()
    val installedEmbedding = remember(modelInventory) {
        (modelInventory as? LocalModelInventoryState.Ready)?.models.orEmpty().filter { descriptor ->
            val model = LiteRtModelCatalog.byId(descriptor.id)
            model != null && descriptor.valid && descriptor.verified &&
                descriptor.sha256 == model.sha256 && model.task == LiteRtArtifactTask.EMBEDDING
        }
    }
    LaunchedEffect(Unit) { indexCount = service.readyIndexSize() }
    DisposableEffect(Unit) { onDispose { job?.cancel() } }

    fun launchAction(action: suspend () -> Unit) {
        if (busy) return
        job = scope.launch {
            busy = true
            error = null
            message = null
            try { action() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { error = localSearchUserErrorMessage(failure) }
            finally { busy = false; job = null }
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().heightIn(max = 620.dp).imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(uiText("Find related notes"), style = MaterialTheme.typography.titleLarge)
            Text(uiText("Search your chosen Notebook, Canvas and Study text privately on this phone. Halo will not index anything until you approve the sources and tap Build local search."),
                style = MaterialTheme.typography.bodyMedium)

            Text(uiText("Local search model"), style = MaterialTheme.typography.titleSmall)
            if (installedEmbedding.isEmpty()) {
                Text(uiText("No verified local search model is installed."),
                    style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = { onDismiss(); onOpenModels() }) {
                    Text(uiText("Browse on-device models"))
                }
            } else {
                installedEmbedding.forEach { installed ->
                    val selected = modelId == installed.id
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = selected, enabled = !busy, onClick = {
                            modelId = installed.id
                            results = emptyList()
                        })
                        Text(installed.displayName, Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyMedium)
                    }
                }
                if (modelId != null && modelChoice?.modelId != modelId) {
                    Button(enabled = !busy, onClick = {
                        val target = modelId ?: return@Button
                        launchAction {
                            modelChoice = service.prepareSearchModel(target)
                            indexCount = 0
                            results = emptyList()
                            message = "Local search model ready. Choose your sources next."
                        }
                    }) { Text(uiText("Use for local search")) }
                }
            }

            Text(uiText("Choose what Halo can search"), style = MaterialTheme.typography.titleSmall)
            if (choices.isEmpty()) {
                Text(uiText("No available notes or study sessions. Create a note or return to the full Library to choose sources."),
                    style = MaterialTheme.typography.bodySmall)
            } else {
                Text(uiText("Only checked sources can enter the private search index."),
                    style = MaterialTheme.typography.bodySmall)
                choices.forEach { choice ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = choice.id in draftIds, enabled = !busy, onCheckedChange = { checked ->
                            draftIds = if (checked) {
                                if (draftIds.size < 64) draftIds + choice.id else draftIds
                            } else draftIds - choice.id
                            results = emptyList()
                        })
                        Text(choice.title, Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyMedium, maxLines = 2)
                    }
                }
            }

            val ready = modelChoice != null && modelChoice?.modelId == modelId
            val changed = !approved || draftIds != approvedIds
            Button(enabled = !busy && ready && draftIds.isNotEmpty(), onClick = {
                launchAction {
                    // Permission is stored before native indexing, and may be revoked below.
                    service.configure(true,draftIds)
                    approved = true
                    approvedIds = draftIds
                    // The service revokes previous assistant-context consent whenever
                    // the source allowlist changes. Keep the visible toggle truthful.
                    assistantContext = service.localSearchAccess().assistantContextEnabled
                    indexCount = 0
                    results = emptyList()
                    service.buildLocalSearchIndex()
                    indexCount = service.readyIndexSize()
                    results = emptyList()
                    message = "Local search is ready for ${draftIds.size} selected sources."
                }
            }, modifier = Modifier.fillMaxWidth()) {
                Text(uiText(if (changed) "Allow sources and build local search" else "Update local search index"))
            }

            if (approved && !busy && indexCount == 0) Text(
                uiText("Choose which sources Halo may search, then build your local index."),
                style = MaterialTheme.typography.bodySmall
            )
            if (approved) TextButton(enabled = !busy, onClick = {
                launchAction {
                    service.configure(false,emptySet())
                    approved = false
                    assistantContext = false
                    approvedIds = emptySet()
                    draftIds = emptySet()
                    indexCount = 0
                    results = emptyList()
                    message = "Local search is off and its stored index was cleared."
                }
            }) { Text(uiText("Turn off local search and clear index")) }

            if (approved && approvedIds.isNotEmpty() && ready && !changed && indexCount > 0) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(uiText("Use matches in local Halo replies"),
                            style = MaterialTheme.typography.bodyMedium)
                        Text(uiText("Optional. Selected passages may help on-device replies; remote providers do not receive this automatic context."),
                            style = MaterialTheme.typography.bodySmall)
                    }
                    Switch(checked = assistantContext, enabled = !busy, onCheckedChange = { enabled ->
                        launchAction {
                            service.configureAssistantContext(enabled)
                            assistantContext = enabled
                        }
                    })
                }
                Text(uiText("Search selected sources"), style = MaterialTheme.typography.titleSmall)
                HaloTextField(value = query, onValueChange = { query = it.take(4096) },
                    modifier = Modifier.fillMaxWidth(), kind = VoiceFieldKind.SEARCH,
                    label = { Text(uiText("What are you looking for?")) }, singleLine = true)
                Button(enabled = !busy && query.isNotBlank(), onClick = {
                    launchAction {
                        results = service.searchLocalSources(query)
                        message = if (results.isEmpty()) "No related passages found. Build or update the index if the source changed." else null
                    }
                },modifier = Modifier.fillMaxWidth()) { Text(uiText("Find related content")) }
                results.distinctBy { it.sourceId }.take(8).forEach { hit ->
                    Surface(shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.surfaceContainerLow) {
                        Column(Modifier.fillMaxWidth().padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            val title = choices.firstOrNull { it.id == hit.sourceId }?.title
                                ?: hit.provenance.substringAfter(':')
                            Text(title, style = MaterialTheme.typography.titleSmall)
                            Text(hit.excerpt.take(260), style = MaterialTheme.typography.bodySmall)
                            TextButton(onClick = {
                                onDismiss()
                                val id = hit.sourceId.substringAfter(':')
                                if (hit.sourceId.startsWith("study:")) viewModel.openStudy(id)
                                else viewModel.openReferencedObject(id)
                            }) { Text(uiText("Open source")) }
                        }
                    }
                }
            }
            if (busy) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(24.dp))
                    Spacer(Modifier.width(12.dp))
                    Text(uiText("Working locally…"))
                }
                TextButton(onClick = { job?.cancel() }) { Text(uiText("Stop")) }
            }
            message?.let { Text(uiText(it), style = MaterialTheme.typography.bodySmall) }
            error?.let { Text(uiText(it),color=MaterialTheme.colorScheme.error) }
            TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
                Text(uiText("Done"))
            }
        }
    }
}