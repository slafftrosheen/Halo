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
import com.halo.app.data.notebook.CanvasImageSearchSource
import com.halo.app.data.study.StudySessionRecord
import com.halo.app.data.workspace.ContentOwnership
import com.halo.app.data.workspace.FilePermissionMode
import com.halo.app.data.workspace.WorkspaceFile
import com.halo.app.data.workspace.WorkspaceFileAvailability
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
    sessions: List<StudySessionRecord>,
    imageAttachments: List<com.halo.app.data.workspace.NoteAttachment> = emptyList(),
    audioAttachments: List<com.halo.app.data.workspace.NoteAttachment> = emptyList(),
    videoAttachments: List<com.halo.app.data.workspace.NoteAttachment> = emptyList(),
    canvasImages: List<CanvasImageSearchSource> = emptyList(),
    workspaceAudioFiles: List<WorkspaceFile> = emptyList(),
    captureAuthority: String? = null,
    audioFallbackLabel: String = "Audio memo",
    voiceMemoLabel: String = "Voice memo",
    inboxLabel: String = "Inbox",
    videoFallbackLabel: String = "Video clip",
    canvasImageFallbackLabel: String = "Picture"): List<LocalSearchSourceChoice> {
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
    val activeNotes = items.mapNotNull { item ->
        val row = item.objectRef
        row.takeIf { row.lifecycle == WorkspaceObjectLifecycle.ACTIVE && row.type == WorkspaceObjectType.NOTE }
    }.associateBy { it.id.value }
    val pictures = imageAttachments.mapNotNull { attachment ->
        val note = activeNotes[attachment.noteId] ?: return@mapNotNull null
        if (attachment.type != "IMAGE" || !attachment.mimeType.startsWith("image/")) return@mapNotNull null
        val label = attachment.caption?.takeIf { it.isNotBlank() }
            ?: attachment.displayName?.takeIf { it.isNotBlank() }
            ?: "Picture"
        LocalSearchSourceChoice("attachment:${attachment.noteId}:${attachment.id}",
            "${note.title.ifBlank { "Untitled" }} · $label")
    }
    val audio = audioAttachments.mapNotNull { attachment ->
        val note = activeNotes[attachment.noteId] ?: return@mapNotNull null
        if (!attachment.mimeType.startsWith("audio/")) return@mapNotNull null
        val label = attachment.caption?.takeIf { it.isNotBlank() }
            ?: attachment.displayName?.takeIf { it.isNotBlank() }
            ?: audioFallbackLabel
        LocalSearchSourceChoice("attachment:${attachment.noteId}:${attachment.id}",
            "${note.title.ifBlank { "Untitled" }} · $label")
    }
    val videos = videoAttachments.mapNotNull { attachment ->
        val note = activeNotes[attachment.noteId] ?: return@mapNotNull null
        if (!attachment.mimeType.startsWith("video/")) return@mapNotNull null
        val label = attachment.caption?.takeIf { it.isNotBlank() }
            ?: attachment.displayName?.takeIf { it.isNotBlank() }
            ?: videoFallbackLabel
        LocalSearchSourceChoice("attachment:${attachment.noteId}:${attachment.id}",
            "${note.title.ifBlank { "Untitled" }} · $label")
    }
    val activeCanvases = items.mapNotNull { item ->
        item.objectRef.takeIf { it.lifecycle == WorkspaceObjectLifecycle.ACTIVE && it.type == WorkspaceObjectType.CANVAS }
    }.associateBy { it.id.value }
    val canvasPictures = canvasImages.mapNotNull { source ->
        val canvas = activeCanvases[source.canvasId] ?: return@mapNotNull null
        if (source.objectId.isBlank() || source.mimeType.lowercase() !in setOf("image/png", "image/jpeg")) return@mapNotNull null
        val label = source.displayName.takeIf { it.isNotBlank() } ?: canvasImageFallbackLabel
        LocalSearchSourceChoice("canvas-image:${source.canvasId}:${source.nodeId}",
            "${canvas.title.ifBlank { "Untitled" }} · $label")
    }
    val learning = sessions.map { session ->
        LocalSearchSourceChoice("study:${session.id}",session.goal?.takeIf { it.isNotBlank() } ?: "Study session")
    }
    val activeFileOwners = items.mapNotNull { item ->
        item.objectRef.takeIf {
            it.lifecycle == WorkspaceObjectLifecycle.ACTIVE &&
                it.type in setOf(WorkspaceObjectType.FILE, WorkspaceObjectType.MEDIA, WorkspaceObjectType.ARTIFACT)
        }
    }.associateBy { it.id.value }
    val workspaceAudio = workspaceAudioFiles.mapNotNull { file ->
        val owner = activeFileOwners[file.objectId.value] ?: return@mapNotNull null
        if (!file.mimeType.startsWith("audio/") || file.availability !in
            setOf(WorkspaceFileAvailability.AVAILABLE, WorkspaceFileAvailability.OWNED_LOCAL)) return@mapNotNull null
        val persistedExternal = file.ownership == ContentOwnership.EXTERNAL_REFERENCE &&
            file.permissionMode == FilePermissionMode.PERSISTABLE_URI &&
            file.availability == WorkspaceFileAvailability.AVAILABLE
        val ownedCopy = file.ownership == ContentOwnership.HALO_OWNED &&
            file.availability == WorkspaceFileAvailability.OWNED_LOCAL
        val appVoiceMemo = file.ownership == ContentOwnership.EXTERNAL_REFERENCE &&
            file.uri.scheme == "content" && file.uri.authority == captureAuthority &&
            file.uri.pathSegments.let { it.size == 3 && it[0] == "workspace-owned" &&
                it[1] == "voice-inbox" && it[2].startsWith("memo_") && it[2].endsWith(".m4a") }
        if (!persistedExternal && !ownedCopy && !appVoiceMemo) return@mapNotNull null
        val label = if (appVoiceMemo) voiceMemoLabel else file.displayName.ifBlank { audioFallbackLabel }
        val ownerLabel = if (appVoiceMemo) inboxLabel else owner.title.ifBlank { inboxLabel }
        LocalSearchSourceChoice("workspace-audio:${file.objectId.value}",
            "$ownerLabel · $label")
    }
    val groups = listOf(workspace, learning, pictures, audio, workspaceAudio, videos, canvasPictures)
    val sources = ArrayList<LocalSearchSourceChoice>(64)
    val seen = HashSet<String>()
    val maxGroupSize = groups.maxOfOrNull { it.size } ?: 0
    for (index in 0 until maxGroupSize) {
        for (group in groups) {
            val choice = group.getOrNull(index) ?: continue
            if (seen.add(choice.id)) sources += choice
            if (sources.size == 64) return sources
        }
    }
    return sources
}

/** Maps technical error codes to user-facing English messages. No @Composable calls here. */
internal fun localSearchUserErrorMessage(error: Throwable): String = when (error.message) {
    "VOICE_INTERACTION_ACTIVE", "NATIVE_RESOURCE_BUSY", "UNLOAD_RESIDENT_LITERT_FIRST" ->
        "Finish the current voice or model response, then try local search again."
    "SEARCH_MODEL_NOT_VERIFIED", "SEARCH_MODEL_NOT_IN_CATALOG", "ARTIFACT_NOT_INSTALLED", "ARTIFACT_CHANGED", "ARTIFACT_LEASE_UNAVAILABLE" ->
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
    "SEARCH_IMAGE_MODEL_UNSUPPORTED" ->
        "Picture search needs the verified EmbeddingGemma 2 model. Deselect pictures to use text-only local search."
    "VISION_CPU_UNSUPPORTED" ->
        "This phone cannot run the image encoder on CPU. Deselect pictures to continue with text-only local search."
    "IMAGE_INPUT_INVALID", "IMAGE_DIMENSIONS_UNSUPPORTED" ->
        "This picture format or size is not supported for local search. Use a PNG or JPEG up to 4 MiB."
    "IMAGE_SOURCE_PERMISSION_EXPIRED", "IMAGE_SOURCE_OUTSIDE_APP_STORAGE", "IMAGE_SOURCE_UNSUPPORTED" ->
        "A selected picture is unavailable or no longer permitted. Reattach it or remove it from local search."
    "SEARCH_AUDIO_MODEL_UNSUPPORTED" ->
        "Audio search needs the verified EmbeddingGemma 2 model. Deselect audio to use text-only local search."
    "AUDIO_CPU_UNSUPPORTED" ->
        "This phone cannot run the audio encoder on CPU. Deselect audio to continue with text-only local search."
    "AUDIO_INPUT_INVALID", "AUDIO_SOURCE_TOO_LARGE", "AUDIO_DURATION_UNSUPPORTED" ->
        "This recording is not supported for local search. Use an audio file up to two minutes and 12 MB."
    "AUDIO_SOURCE_COUNT_LIMIT" ->
        "Select up to eight audio recordings for local search."
    "AUDIO_SOURCE_CHANGED" ->
        "A selected recording changed while it was being read. Build local search again."
    "AUDIO_SOURCE_PERMISSION_EXPIRED", "AUDIO_SOURCE_OUTSIDE_APP_STORAGE", "AUDIO_SOURCE_UNSUPPORTED" ->
        "A selected recording is unavailable or no longer permitted. Reattach it or remove it from local search."
    "SEARCH_VIDEO_MODEL_UNSUPPORTED" ->
        "Video frame search needs the verified EmbeddingGemma 2 model. Deselect videos to use text-only local search."
    "VIDEO_CPU_UNSUPPORTED" ->
        "This phone cannot run the image encoder on CPU. Deselect videos to continue with text-only local search."
    "VIDEO_SOURCE_TOO_LARGE", "VIDEO_DURATION_UNSUPPORTED", "VIDEO_FRAME_UNAVAILABLE", "VIDEO_DECODE_UNSUPPORTED" ->
        "This video is not supported for local frame search. Use a video up to two minutes and 24 MB."
    "VIDEO_SOURCE_COUNT_LIMIT" ->
        "Select up to four video clips for local search."
    "VIDEO_SOURCE_CHANGED" ->
        "A selected video changed while it was being read. Build local search again."
    "VIDEO_SOURCE_PERMISSION_EXPIRED", "VIDEO_SOURCE_OUTSIDE_APP_STORAGE", "VIDEO_SOURCE_UNSUPPORTED" ->
        "A selected video is unavailable or no longer permitted. Reattach it or remove it from local search."
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
    val context = androidx.compose.ui.platform.LocalContext.current
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val imageAttachments by ManualDependencyGraph.notebookAssetRepository.observeImageAttachments()
        .collectAsStateWithLifecycle(emptyList())
    val audioAttachments by ManualDependencyGraph.notebookAssetRepository.observeAudioAttachments()
        .collectAsStateWithLifecycle(emptyList())
    val videoAttachments by ManualDependencyGraph.notebookAssetRepository.observeVideoAttachments()
        .collectAsStateWithLifecycle(emptyList())
    val canvasImages by ManualDependencyGraph.notebookCanvasRepository.observeImageSearchSources()
        .collectAsStateWithLifecycle(emptyList())
    val workspaceAudioFiles by ManualDependencyGraph.workspaceFileRepository.observeFiles()
        .collectAsStateWithLifecycle(emptyList())
    val audioFallbackLabel = uiText("Audio memo")
    val voiceMemoLabel = uiText("Voice memo")
    val inboxLabel = uiText("Inbox")
    val videoFallbackLabel = uiText("Video clip")
    val canvasImageFallbackLabel = uiText("Picture")
    val choices = remember(items,studySessions,imageAttachments,audioAttachments,videoAttachments,canvasImages,
        workspaceAudioFiles,configuration,audioFallbackLabel,voiceMemoLabel,inboxLabel,videoFallbackLabel,canvasImageFallbackLabel) {
        notebookSearchSources(items,studySessions,imageAttachments,audioAttachments,videoAttachments,canvasImages,
            workspaceAudioFiles = workspaceAudioFiles,
            captureAuthority = "${context.packageName}.captures",
            audioFallbackLabel = audioFallbackLabel,
            voiceMemoLabel = voiceMemoLabel,
            inboxLabel = inboxLabel,
            videoFallbackLabel = videoFallbackLabel, canvasImageFallbackLabel = canvasImageFallbackLabel)
    }
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
            Text(uiText("Search selected Notebook, Canvas, and Study text, pictures, audio, and short video clips locally on this phone. For videos, only four sampled frames are used; audio tracks are not processed. Halo reads only the sources you approve when you build local search."),
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
                Text(uiText("No available Notebook, Canvas, Study or media sources. Add one or return to the Library to choose sources."),
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
                if (approvedIds.any { it.startsWith("note:") || it.startsWith("canvas:") || it.startsWith("study:") }) {
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
                                else if (hit.sourceId.startsWith("attachment:"))
                                    viewModel.openReferencedObject(id.substringBefore(':'))
                                else if (hit.sourceId.startsWith("canvas-image:")) {
                                    val canvasImage = hit.sourceId.removePrefix("canvas-image:").split(':', limit = 2)
                                    if (canvasImage.size == 2) viewModel.openCanvasNode(canvasImage[0], canvasImage[1])
                                }
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
