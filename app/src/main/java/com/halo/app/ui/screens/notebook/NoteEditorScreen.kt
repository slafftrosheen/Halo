package com.halo.app.ui.screens.notebook

import com.halo.app.language.uiText

import androidx.lifecycle.compose.collectAsStateWithLifecycle

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.FormatListBulleted
import androidx.compose.material.icons.automirrored.rounded.Redo
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.BrokenImage
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.FormatBold
import androidx.compose.material.icons.rounded.FormatItalic
import androidx.compose.material.icons.rounded.FormatStrikethrough
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.halo.app.data.notebook.DocumentOperation
import com.halo.app.data.notebook.DocumentSelection
import com.halo.app.data.notebook.InlineStyle
import com.halo.app.data.notebook.NoteBlock
import com.halo.app.data.notebook.NoteBlockType
import com.halo.app.data.notebook.NotebookSectionTools
import com.halo.app.data.notebook.WorkspaceLifecycle
import com.halo.app.data.notebook.WorkspaceObject
import com.halo.app.data.workspace.NoteAttachment
import com.halo.app.ui.presence.HaloSignalGlyph
import com.halo.app.ui.presence.HaloSignalKind
import com.halo.app.ui.theme.HaloPastelBlue
import com.halo.app.ui.theme.HaloPastelMint
import com.halo.app.ui.theme.HaloPastelPink
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun NoteEditorScreen(
    viewModel: NoteEditorViewModel,
    onBack: () -> Unit,
    onOpenStudy: (String) -> Unit = {},
    onReviewMemory: (String) -> Unit = {},
) {
    LaunchedEffect(viewModel) { viewModel.memoryReviewEvents.collect { onReviewMemory(it) } }
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    val referenceCandidates by viewModel.referenceCandidates.collectAsStateWithLifecycle()

    var showOutline by remember { mutableStateOf(false) }
    var showReferencePicker by remember { mutableStateOf(false) }
    // Non-null while the picker is repairing an existing reference block instead of inserting one.
    var relinkBlockId by remember { mutableStateOf<String?>(null) }
    var showFindBar by remember { mutableStateOf(false) }
    var showImageStudio by remember { mutableStateOf(false) }
    var showAssistMenu by remember { mutableStateOf(false) }
    var studyError by remember { mutableStateOf<String?>(null) }

    if (showImageStudio) viewModel.imageStudio?.let { studio ->
        com.halo.app.ui.workspace.WorkspaceImageDialog(studio) { showImageStudio = false }
    }

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(viewModel::addAttachment)
    }

    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) viewModel.flush()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            viewModel.flush()
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Predicted-back and system back go through the same durable flush before leaving the note.
    BackHandler(enabled = true) {
        viewModel.flush()
        onBack()
    }

    val startStudy: (String?) -> Unit = remember(viewModel, onOpenStudy) {
        { passage ->
            scope.launch {
                when (val result = viewModel.startStudy(passage)) {
                    is StudyHandoff.Started -> onOpenStudy(result.sessionId)
                    is StudyHandoff.Unavailable -> studyError = result.message
                }
            }
        }
    }

    if (showOutline) {
        OutlineSheet(
            state = state,
            onJump = { blockId ->
                showOutline = false
                viewModel.setSelection(DocumentSelection(blockId, 0, 0))
            },
            onDismiss = { showOutline = false },
        )
    }
    if (showReferencePicker) {
        val targetBlockId = relinkBlockId
        com.halo.app.ui.workspace.WorkspaceObjectPicker(
            candidates = referenceCandidates,
            onPick = { objectRef ->
                showReferencePicker = false
                relinkBlockId = null
                if (targetBlockId != null) {
                    viewModel.relinkReference(targetBlockId, objectRef.id.value, objectRef.title)
                } else {
                    viewModel.insertReference(objectRef.id.value, objectRef.title)
                }
            },
            onDismiss = {
                showReferencePicker = false
                relinkBlockId = null
            },
        )
    }
    studyError?.let { message ->
        AlertDialog(
            onDismissRequest = { studyError = null },
            title = { Text(uiText("Study unavailable")) },
            text = { Text(message) },
            confirmButton = { TextButton(onClick = { studyError = null }) { Text(uiText("OK")) } },
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 20.dp, vertical = 10.dp)
            // Keep the selection toolbar directly above the keyboard instead of under it.
            .imePadding()
    ) {
        EditorToolbar(
            state = state,
            onBack = {
                viewModel.flush()
                onBack()
            },
            onArchive = viewModel::archive,
            onTrash = viewModel::moveToTrash,
            onRestore = viewModel::restore,
            onRemember = viewModel::rememberNote,
            onToggleOutline = { showOutline = true },
            onToggleFind = { showFindBar = !showFindBar },
            onModeChange = viewModel::setMode,
            onUndo = viewModel::undo,
            onRedo = viewModel::redo,
            onStudy = { startStudy(null) },
        )

        if (state.status == SaveStatus.MISSING) {
            MissingNoteState(onBack, Modifier.weight(1f))
            return@Column
        }

        Spacer(Modifier.height(10.dp))
        TitleRow(state, onTitleChange = viewModel::renameTitle)
        Spacer(Modifier.height(8.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        state.notice?.let { notice ->
            StatusBanner(notice, MaterialTheme.colorScheme.secondaryContainer) { viewModel.clearNotice() }
        }
        state.recoveryCandidate?.let { candidate ->
            RecoveryBanner(
                preview = candidate,
                onApply = viewModel::applyRecoveryCandidate,
                onDiscard = viewModel::discardRecoveryCandidate,
            )
        }
        state.conflict?.let { conflict ->
            ConflictBanner(
                storedRevision = conflict.storedRevision,
                localRevision = state.revision,
                onKeepLocal = viewModel::keepLocalOnConflict,
                onUseStored = viewModel::useStoredOnConflict,
            )
        }
        if (state.status == SaveStatus.FAILED) {
            StatusBanner("Your draft is kept locally. Retry when storage is available.", MaterialTheme.colorScheme.errorContainer, onRetry = viewModel::retrySave)
        }
        state.proposalError?.let { error ->
            StatusBanner(error, MaterialTheme.colorScheme.errorContainer) { viewModel.clearNotice() }
        }

        if (showFindBar) {
            FindReplaceBar(state, viewModel, onClose = { showFindBar = false })
        }

        when (state.mode) {
            EditorMode.SOURCE -> SourceModeEditor(state, viewModel)
            EditorMode.READ -> ReadModeView(state, Modifier.weight(1f))
            EditorMode.EDIT -> EditModeView(
                state = state,
                viewModel = viewModel,
                onImage = { attachment -> viewModel.imageStudio?.prepare(attachment); showImageStudio = true },
                onAddAttachment = { filePicker.launch(arrayOf("image/*", "application/pdf", "text/*", "audio/*", "video/*")) },
                onPickReference = {
                    relinkBlockId = null
                    showReferencePicker = true
                },
                onPickExistingReference = { blockId ->
                    relinkBlockId = blockId
                    showReferencePicker = true
                },
                onAssist = { showAssistMenu = true },
                onDismissAssist = { showAssistMenu = false },
                onStudyPassage = { passage -> startStudy(passage) },
                showAssistMenu = showAssistMenu,
                context = context,
            )
        }

        EditorStatusBar(state)
    }
}

// ---------------------------------------------------------------------------------------------
// Toolbar and headers
// ---------------------------------------------------------------------------------------------

@Composable
private fun EditorToolbar(
    state: NoteEditorUiState,
    onBack: () -> Unit,
    onArchive: () -> Unit,
    onTrash: () -> Unit,
    onRestore: () -> Unit,
    onRemember: () -> Unit,
    onToggleOutline: () -> Unit,
    onToggleFind: () -> Unit,
    onModeChange: (EditorMode) -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onStudy: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = uiText("Back"),
                tint = MaterialTheme.colorScheme.onBackground,
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onUndo, enabled = state.canUndo) {
                Icon(Icons.AutoMirrored.Rounded.Undo, contentDescription = uiText("Undo"))
            }
            IconButton(onClick = onRedo, enabled = state.canRedo) {
                Icon(Icons.AutoMirrored.Rounded.Redo, contentDescription = uiText("Redo"))
            }
            IconButton(onClick = onToggleOutline) {
                Icon(Icons.AutoMirrored.Rounded.FormatListBulleted, contentDescription = uiText("Outline"))
            }
            IconButton(onClick = onToggleFind) {
                Icon(Icons.Rounded.Search, contentDescription = uiText("Find and replace"))
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            ModeToggle(state.mode, onModeChange)
            Box {
                TextButton(onClick = { menuExpanded = true }) { Text(uiText("More")) }
                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                    DropdownMenuItem(
                        text = { Text(uiText("Study this note")) },
                        onClick = {
                            menuExpanded = false
                            onStudy()
                        },
                    )
                    if (state.lifecycle == WorkspaceLifecycle.ACTIVE) {
                        DropdownMenuItem(
                            text = { Text(uiText("Remember in Halo")) },
                            onClick = {
                                menuExpanded = false
                                onRemember()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(uiText("Archive")) },
                            onClick = {
                                menuExpanded = false
                                onArchive()
                            },
                        )
                    } else {
                        DropdownMenuItem(
                            text = { Text(uiText("Restore")) },
                            onClick = {
                                menuExpanded = false
                                onRestore()
                            },
                        )
                    }
                    if (state.lifecycle != WorkspaceLifecycle.TRASHED) {
                        DropdownMenuItem(
                            text = { Text(uiText("Move to trash")) },
                            onClick = {
                                menuExpanded = false
                                onTrash()
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ModeToggle(mode: EditorMode, onModeChange: (EditorMode) -> Unit) {
    Row(
        Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        EditorMode.entries.forEach { entry ->
            val active = entry == mode
            Surface(
                onClick = { onModeChange(entry) },
                shape = RoundedCornerShape(8.dp),
                color = if (active) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                },
            ) {
                Text(
                    text = when (entry) {
                        EditorMode.EDIT -> "Edit"
                        EditorMode.READ -> "Read"
                        EditorMode.SOURCE -> "Source"
                    },
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (active) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        }
    }
}

@Composable
private fun TitleRow(state: NoteEditorUiState, onTitleChange: (String) -> Unit) {
    var editing by remember { mutableStateOf(false) }
    var draft by remember(state.title) { mutableStateOf(state.title) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        HaloSignalGlyph(kind = HaloSignalKind.NOTE, size = 30.dp)
        Spacer(Modifier.size(10.dp))
        Column(Modifier.weight(1f)) {
            if (editing) {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row {
                    TextButton(onClick = {
                        editing = false
                        if (draft.isNotBlank() && draft != state.title) onTitleChange(draft.trim())
                    }) { Text(uiText("Save title")) }
                    TextButton(onClick = { editing = false; draft = state.title }) { Text(uiText("Cancel")) }
                }
            } else {
                Text(
                    text = state.title.ifBlank { "Untitled note" },
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.clickable(enabled = state.isEditable) { editing = true },
                )
                Text(
                    text = when (state.lifecycle) {
                        WorkspaceLifecycle.ACTIVE -> "Note · revision ${state.revision}"
                        WorkspaceLifecycle.ARCHIVED -> "Archived note"
                        WorkspaceLifecycle.TRASHED -> "Trashed note"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun StatusBanner(
    message: String,
    container: androidx.compose.ui.graphics.Color,
    onRetry: (() -> Unit)? = null,
    onDismiss: (() -> Unit)? = null,
) {
    Surface(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        shape = RoundedCornerShape(12.dp),
        color = container,
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(message, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
            onRetry?.let { retry -> TextButton(onClick = retry) { Text(uiText("Retry")) } }
            onDismiss?.let { dismiss ->
                IconButton(onClick = dismiss) { Icon(Icons.Rounded.Close, contentDescription = uiText("Dismiss")) }
            }
        }
    }
}

@Composable
private fun RecoveryBanner(preview: String, onApply: () -> Unit, onDiscard: () -> Unit) {
    Surface(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        shape = RoundedCornerShape(12.dp),
        color = HaloPastelBlue.copy(alpha = 0.18f),
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(uiText("Unsaved text found"), style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(4.dp))
            Text(preview.take(240), style = MaterialTheme.typography.bodySmall, maxLines = 4)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onApply) { Text(uiText("Apply")) }
                TextButton(onClick = onDiscard) { Text(uiText("Discard")) }
            }
        }
    }
}

@Composable
private fun ConflictBanner(
    storedRevision: Long,
    localRevision: Long,
    onKeepLocal: () -> Unit,
    onUseStored: () -> Unit,
) {
    Surface(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.errorContainer,
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(uiText("Save conflict"), style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(4.dp))
            Text(
                "This note was saved elsewhere (stored revision $storedRevision, this screen was on $localRevision). " +
                    "Your unsaved changes are still here.",
                style = MaterialTheme.typography.bodySmall,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onKeepLocal) { Text(uiText("Keep mine")) }
                TextButton(onClick = onUseStored) { Text(uiText("Use stored")) }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Modes
// ---------------------------------------------------------------------------------------------

@Composable
private fun ColumnScope.EditModeView(
    state: NoteEditorUiState,
    viewModel: NoteEditorViewModel,
    onAddAttachment: () -> Unit,
    onImage: (NoteAttachment?) -> Unit,
    onPickReference: () -> Unit,
    onPickExistingReference: (String) -> Unit,
    onAssist: () -> Unit,
    onDismissAssist: () -> Unit,
    onStudyPassage: (String?) -> Unit,
    showAssistMenu: Boolean,
    context: android.content.Context,
) {
    var viewingImage by remember { mutableStateOf<NoteAttachment?>(null) }
    viewingImage?.let { image -> AlertDialog(onDismissRequest = { viewingImage = null },
        title = { Text(image.displayName ?: "Image") }, text = { com.halo.app.ui.workspace.WorkspaceAttachmentPreview(image, Modifier.fillMaxWidth().height(320.dp)) },
        confirmButton = { TextButton(onClick = { viewingImage = null }) { Text(uiText("Close")) } }) }
    val listState = rememberLazyListState()
    val enabled = state.isEditable

    SelectionToolbar(
        state = state,
        viewModel = viewModel,
        onPickReference = onPickReference,
        onAssist = onAssist,
        onStudyPassage = {
            val selection = state.selection
            val block = selection?.let { selected -> state.blocks.firstOrNull { it.id == selected.blockId } }
            val passage = if (selection != null && block != null) {
                val range = selection.normalized(block.text.length)
                block.text.substring(range.start, range.end).trim()
            } else {
                ""
            }
            onStudyPassage(
                passage.takeIf { it.isNotBlank() }
                    ?.let { "Understand this passage of my note: ${it.take(600)}" }
            )
        },
    )

    if (showAssistMenu) {
        AssistMenu(
            hasSelection = state.selection != null,
            onPick = { operation ->
                onDismissAssist()
                viewModel.requestAssistance(operation)
            },
            onDismiss = onDismissAssist,
        )
    }

    if (viewModel.imageStudio != null) TextButton(onClick = { onImage(null) }, enabled = enabled && !state.isGenerating) { Text(uiText("Create an image")) }
    if (state.isGenerating) TextButton(onClick = viewModel::cancelAssistance) { Text(uiText("Cancel AI writing")) }
    state.proposal?.let { proposal ->
        ProposalCard(
            operationLabel = proposal.operation.label,
            content = proposal.content,
            baseRevision = proposal.baseRevision,
            currentRevision = state.revision,
            isGenerating = state.isGenerating,
            onApply = viewModel::applyProposal,
            onSaveAsNote = viewModel::saveProposalAsNewNote,
            onDiscard = viewModel::discardProposal,
            onRegenerate = viewModel::regenerateProposal,
            onEdit = viewModel::editProposal,
        )
    }

    TextButton(onClick=viewModel::useInConversation) { Text(uiText("Use in conversation")) }
    if (state.attachments.count { it.type == "IMAGE" } == 1) {
        TextButton(onClick = { viewModel.reviewCaptureImage() }) { Text(uiText("Review image with Halo")) }
    }
    if (state.attachments.isNotEmpty()) {
        Spacer(Modifier.height(8.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            items(state.attachments, key = NoteAttachment::id) { attachment ->
                AttachmentCard(
                    attachment = attachment,
                    onOpen = {
                        if (attachment.type == "IMAGE") viewingImage = attachment
                        else runCatching {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW)
                                    .setDataAndType(Uri.parse(attachment.pathOrUri), attachment.mimeType)
                                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            )
                        }
                    },
                    onRemove = { viewModel.removeAttachment(attachment.id) },
                    onEdit = { onImage(attachment) },
                )
            }
        }
    }
    state.attachmentError?.let { error ->
        Text(error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
    }

    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Surface(
            onClick = onAddAttachment,
            enabled = enabled && !state.isAddingAttachment,
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
        ) {
            Row(Modifier.padding(horizontal = 10.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.AttachFile, contentDescription = uiText("Add file"), modifier = Modifier.size(16.dp))
                Spacer(Modifier.size(6.dp))
                Text(uiText("Attach"), style = MaterialTheme.typography.labelMedium)
            }
        }
        Spacer(Modifier.width(8.dp))
        Surface(
            onClick = onPickReference,
            enabled = enabled,
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
        ) {
            Row(Modifier.padding(horizontal = 10.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Link, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.size(6.dp))
                Text(uiText("Insert object"), style = MaterialTheme.typography.labelMedium)
            }
        }
        Spacer(Modifier.weight(1f))
        if (state.isGenerating) {
            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(8.dp))
            Text(uiText("Halo is writing…"), style = MaterialTheme.typography.labelSmall)
        }
    }

    if (state.status == SaveStatus.LOADING) {
        Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxWidth().weight(1f),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(state.blocks, key = NoteBlock::id) { block ->
            NoteBlockEditor(
                block = block,
                enabled = enabled,
                canMoveUp = block.position > 0,
                canMoveDown = block.position < state.blocks.lastIndex,
                canRemove = state.blocks.size > 1,
                focusRequest = state.focusRequest?.takeIf { it.blockId == block.id },
                onFocusHandled = viewModel::consumeFocusRequest,
                onTextChange = { viewModel.updateBlock(block.id, it) },
                onSelectionChange = { start, end ->
                    viewModel.setSelection(DocumentSelection(block.id, start, end))
                },
                onNewlineSplit = { offset -> viewModel.splitBlock(block.id, offset) },
                onBackspaceAtStart = { viewModel.mergeIntoPrevious(block.id) },
                onTypeChange = { viewModel.changeBlockType(block.id, it) },
                onCheckedChange = { viewModel.setBlockChecked(block.id, it) },
                onMoveUp = { viewModel.moveBlock(block.id, -1) },
                onMoveDown = { viewModel.moveBlock(block.id, 1) },
                onIndent = { viewModel.indentBlock(block.id, it) },
                onRemove = { viewModel.removeBlock(block.id) },
                onDuplicate = { viewModel.duplicateBlock(block.id) },
                onAddBelow = { viewModel.insertBlock(block.id) },
                unlinkedReference = block.id in state.unlinkedReferenceIds,
                onRelink = { onPickExistingReference(block.id) },
            )
        }
    }
}

@Composable
private fun SelectionToolbar(
    state: NoteEditorUiState,
    viewModel: NoteEditorViewModel,
    onPickReference: () -> Unit,
    onAssist: () -> Unit,
    onStudyPassage: () -> Unit,
) {
    var linkDialog by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val hasSelection = state.selection != null
        IconButton(onClick = { viewModel.toggleInlineStyle(InlineStyle.BOLD) }, enabled = hasSelection) {
            Icon(Icons.Rounded.FormatBold, contentDescription = uiText("Bold"))
        }
        IconButton(onClick = { viewModel.toggleInlineStyle(InlineStyle.ITALIC) }, enabled = hasSelection) {
            Icon(Icons.Rounded.FormatItalic, contentDescription = uiText("Italic"))
        }
        IconButton(onClick = { viewModel.toggleInlineStyle(InlineStyle.STRIKE) }, enabled = hasSelection) {
            Icon(Icons.Rounded.FormatStrikethrough, contentDescription = uiText("Strikethrough"))
        }
        IconButton(onClick = { linkDialog = true }, enabled = hasSelection) {
            Icon(Icons.Rounded.Link, contentDescription = uiText("Add link"))
        }
        Spacer(Modifier.width(4.dp))
        BlockTypeQuickButtons(enabled = hasSelection) { type ->
            viewModel.convertSelectionTo(type)
        }
        Spacer(Modifier.width(4.dp))
        TextButton(onClick = onPickReference) { Text(uiText("Object")) }
        TextButton(onClick = onAssist) { Text(uiText("Ask Halo")) }
        TextButton(onClick = onStudyPassage) { Text(uiText("Study")) }
    }
    if (linkDialog) {
        LinkDialog(
            onConfirm = { url ->
                linkDialog = false
                viewModel.insertLink(url)
            },
            onDismiss = { linkDialog = false },
        )
    }
}

@Composable
private fun BlockTypeQuickButtons(enabled: Boolean, onPick: (NoteBlockType) -> Unit) {
    val quick = listOf(
        NoteBlockType.HEADING to "H1",
        NoteBlockType.HEADING_2 to "H2",
        NoteBlockType.BULLET_LIST to "•",
        NoteBlockType.NUMBERED_LIST to "1.",
        NoteBlockType.CHECKLIST to "☑",
        NoteBlockType.QUOTE to "❝",
        NoteBlockType.CODE to "</>",
    )
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        quick.forEach { (type, label) ->
            Surface(
                onClick = { onPick(type) },
                enabled = enabled,
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
            ) {
                Text(
                    label,
                    Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun LinkDialog(onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var url by remember { mutableStateOf("https://") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(uiText("Add link")) },
        text = {
            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                singleLine = true,
                label = { Text(uiText("URL")) },
            )
        },
        confirmButton = {
            TextButton(onClick = { if (url.isNotBlank()) onConfirm(url.trim()) }) { Text(uiText("Insert")) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(uiText("Cancel")) } },
    )
}

@Composable
private fun AssistMenu(
    hasSelection: Boolean,
    onPick: (DocumentOperation) -> Unit,
    onDismiss: () -> Unit,
) {
    DropdownMenu(expanded = true, onDismissRequest = onDismiss) {
        DocumentOperation.entries.forEach { operation ->
            val needsSelection = operation in setOf(
                DocumentOperation.REWRITE,
                DocumentOperation.TURN_INTO_TASKS,
                DocumentOperation.EXPLAIN,
                DocumentOperation.TRANSLATE,
            )
            DropdownMenuItem(
                text = {
                    Text(
                        if (needsSelection && !hasSelection) "${operation.label} (select text first)"
                        else operation.label
                    )
                },
                enabled = !(needsSelection && !hasSelection),
                onClick = { onPick(operation) },
            )
        }
    }
}

@Composable
private fun ProposalCard(
    operationLabel: String,
    content: String,
    baseRevision: Long,
    currentRevision: Long,
    isGenerating: Boolean,
    onApply: () -> Unit,
    onSaveAsNote: () -> Unit,
    onDiscard: () -> Unit,
    onRegenerate: () -> Unit,
    onEdit: (String) -> Unit,
) {
    var editing by remember { mutableStateOf(false) }
    val stale = baseRevision != currentRevision
    Surface(
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                HaloSignalGlyph(kind = HaloSignalKind.HALO, size = 20.dp)
                Spacer(Modifier.width(8.dp))
                Text(
                    "Halo · $operationLabel · for revision $baseRevision",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.weight(1f),
                )
                if (isGenerating) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
            }
            Spacer(Modifier.height(6.dp))
            if (editing) OutlinedTextField(content, onEdit, modifier = Modifier.fillMaxWidth().heightIn(max = 220.dp), enabled = !isGenerating)
            else Text(
                content,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.heightIn(max = 220.dp).verticalScroll(rememberScrollState()),
            )
            if (stale) {
                Spacer(Modifier.height(6.dp))
                Text(
                    "This proposal is for revision $baseRevision but the note is at $currentRevision. " +
                        "Regenerate before applying.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onApply, enabled = !stale && !isGenerating) { Text(uiText("Apply")) }
                TextButton(onClick = onSaveAsNote, enabled = !isGenerating) { Text(uiText("Save as new note")) }
                TextButton(onClick = onDiscard) { Text(uiText("Discard")) }
            }
            Row {
                TextButton(onClick = onRegenerate, enabled = !isGenerating) { Text(uiText("Regenerate")) }
                TextButton(onClick = { editing = !editing }, enabled = !isGenerating) { Text(if (editing) "Done editing" else "Edit draft") }
            }
        }
    }
}

@Composable
private fun FindReplaceBar(state: NoteEditorUiState, viewModel: NoteEditorViewModel, onClose: () -> Unit) {
    Surface(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Column(Modifier.padding(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = state.findQuery,
                    onValueChange = viewModel::setFindQuery,
                    singleLine = true,
                    label = { Text(uiText("Find")) },
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = viewModel::findPrevious, enabled = state.matches.isNotEmpty()) {
                    Icon(Icons.Rounded.ArrowUpward, contentDescription = uiText("Previous match"))
                }
                IconButton(onClick = viewModel::findNext, enabled = state.matches.isNotEmpty()) {
                    Icon(Icons.Rounded.ArrowDownward, contentDescription = uiText("Next match"))
                }
                IconButton(onClick = onClose) { Icon(Icons.Rounded.Close, contentDescription = uiText("Close find")) }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = state.replaceText,
                    onValueChange = viewModel::setReplaceText,
                    singleLine = true,
                    label = { Text(uiText("Replace with")) },
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = viewModel::replaceActiveMatch, enabled = state.matches.isNotEmpty()) {
                    Text(uiText("Replace"))
                }
                TextButton(onClick = viewModel::replaceAllMatches, enabled = state.matches.isNotEmpty()) {
                    Text(uiText("All"))
                }
            }
            Text(
                if (state.findQuery.isBlank()) {
                    "Literal, case-insensitive search"
                } else {
                    "${state.matches.size} match${if (state.matches.size == 1) "" else "es"} · " +
                        "showing ${state.activeMatchIndex + 1}"
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ColumnScope.SourceModeEditor(state: NoteEditorUiState, viewModel: NoteEditorViewModel) {
    Column(Modifier.fillMaxWidth().weight(1f)) {
        Text(
            uiText("Markdown source. Applying converts it back into blocks."),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (state.sourceLossy) {
            Surface(
                Modifier.fillMaxWidth().padding(vertical = 6.dp),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.errorContainer,
            ) {
                Column(Modifier.padding(10.dp)) {
                    Text(uiText("Some syntax is not representable"), style = MaterialTheme.typography.titleSmall)
                    Text(
                        state.sourcePreservedLines.take(4).joinToString("\n").ifBlank { "Tables, math and raw HTML are kept as literal text." },
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 6,
                    )
                    Text(uiText("Applying will keep the text but normalise its formatting."), style = MaterialTheme.typography.labelSmall)
                }
            }
        }
        BasicTextField(
            value = state.sourceDraft,
            onValueChange = viewModel::updateSourceDraft,
            textStyle = MaterialTheme.typography.bodyMedium.copy(
                color = MaterialTheme.colorScheme.onBackground,
                fontFamily = FontFamily.Monospace,
            ),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.secondary),
            modifier = Modifier.fillMaxWidth().weight(1f),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { viewModel.applySource(force = false) }) { Text(uiText("Apply to note")) }
            TextButton(onClick = { viewModel.applySource(force = true) }) { Text(uiText("Apply anyway")) }
            TextButton(onClick = { viewModel.setMode(EditorMode.EDIT) }) { Text(uiText("Cancel")) }
        }
    }
}

@Composable
private fun ReadModeView(state: NoteEditorUiState, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom = 20.dp)) {
        /*
         * MINING-P2-001 Part O: when this object was saved from a structured assistant answer, show
         * how it looked at capture time. It is a read-only view of the original presentation; the note
         * below it stays an ordinary editable document, and the payload never enters the body.
         */
        state.structuredResult?.let { presentation ->
            Column(Modifier.padding(bottom = 12.dp)) {
                Text(
                    uiText("Saved from Halo · original presentation"),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(6.dp))
                com.halo.app.interaction.StructuredPresentationRenderer(presentation = presentation)
            }
        }
        if (state.blocks.isEmpty()) {
            Text(uiText("This note is empty."), style = MaterialTheme.typography.bodyMedium)
            return@Column
        }
        state.blocks.forEach { block -> ReadBlock(block) }
    }
}

@Composable
private fun ReadBlock(block: NoteBlock) {
    val indent = 16.dp * block.indent
    when (block.type) {
        NoteBlockType.DIVIDER -> {
            Spacer(Modifier.height(6.dp))
            HorizontalDivider()
            Spacer(Modifier.height(6.dp))
        }
        NoteBlockType.CODE -> Surface(
            Modifier.fillMaxWidth().padding(start = indent, top = 4.dp, bottom = 4.dp),
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
        ) {
            Text(
                block.text,
                Modifier.padding(10.dp),
                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
            )
        }
        NoteBlockType.QUOTE -> Row(Modifier.padding(start = indent, top = 4.dp, bottom = 4.dp)) {
            Box(Modifier.width(3.dp).height(38.dp).background(MaterialTheme.colorScheme.primary))
            Spacer(Modifier.width(8.dp))
            Text(renderInline(block.text), style = MaterialTheme.typography.bodyMedium, fontStyle = FontStyle.Italic)
        }
        NoteBlockType.REFERENCE -> Surface(
            Modifier.padding(start = indent, top = 4.dp, bottom = 4.dp),
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
        ) {
            Row(Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                HaloSignalGlyph(kind = HaloSignalKind.FILE, size = 16.dp)
                Spacer(Modifier.width(6.dp))
                Text(
                    block.text.ifBlank { block.refObjectId.orEmpty() },
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
        NoteBlockType.CHECKLIST -> Row(
            Modifier.padding(start = indent, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                if (block.checked) Icons.Rounded.Check else Icons.Rounded.Close,
                contentDescription = if (block.checked) "Checked" else "Not checked",
                modifier = Modifier.size(16.dp),
                tint = if (block.checked) HaloPastelMint else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(8.dp))
            Text(renderInline(block.text), style = MaterialTheme.typography.bodyMedium)
        }
        NoteBlockType.BULLET_LIST, NoteBlockType.NUMBERED_LIST -> Row(
            Modifier.padding(start = indent, top = 3.dp, bottom = 3.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Text(
                if (block.type == NoteBlockType.BULLET_LIST) "•" else "${block.position + 1}.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(8.dp))
            Text(renderInline(block.text), style = MaterialTheme.typography.bodyMedium)
        }
        else -> Text(
            text = renderInline(block.text.ifBlank { " " }),
            style = when (block.type) {
                NoteBlockType.HEADING -> MaterialTheme.typography.headlineSmall
                NoteBlockType.HEADING_2 -> MaterialTheme.typography.titleLarge
                NoteBlockType.HEADING_3 -> MaterialTheme.typography.titleMedium
                else -> MaterialTheme.typography.bodyLarge
            },
            fontWeight = if (block.type.isHeading) FontWeight.SemiBold else FontWeight.Normal,
            modifier = Modifier.padding(top = 4.dp, bottom = 4.dp),
        )
    }
}

// ---------------------------------------------------------------------------------------------
// Block editor
// ---------------------------------------------------------------------------------------------

@Composable
private fun NoteBlockEditor(
    block: NoteBlock,
    enabled: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    canRemove: Boolean,
    focusRequest: EditorFocusRequest?,
    onFocusHandled: () -> Unit,
    onTextChange: (String) -> Unit,
    onSelectionChange: (Int, Int) -> Unit,
    onNewlineSplit: (Int) -> Unit,
    onBackspaceAtStart: () -> Unit,
    onTypeChange: (NoteBlockType) -> Unit,
    onCheckedChange: (Boolean) -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onIndent: (Int) -> Unit,
    onRemove: () -> Unit,
    onDuplicate: () -> Unit,
    onAddBelow: () -> Unit,
    unlinkedReference: Boolean,
    onRelink: () -> Unit,
) {
    var typeMenu by remember(block.id) { mutableStateOf(false) }
    val focusRequester = remember(block.id) { FocusRequester() }
    val haptics = LocalHapticFeedback.current
    var fieldValue by remember(block.id) { mutableStateOf(TextFieldValue(block.text)) }

    // External updates (AI apply, undo, source conversion) replace the field content, but a local
    // edit echo must not reset the caret.
    LaunchedEffect(block.text) {
        if (fieldValue.text != block.text) {
            val caret = fieldValue.selection.end.coerceIn(0, block.text.length)
            fieldValue = TextFieldValue(block.text, TextRange(caret))
        }
    }
    LaunchedEffect(focusRequest?.token) {
        val request = focusRequest ?: return@LaunchedEffect
        runCatching {
            focusRequester.requestFocus()
            fieldValue = TextFieldValue(
                block.text,
                TextRange(request.offset.coerceIn(0, block.text.length)),
            )
        }
        onFocusHandled()
    }

    Surface(
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp * block.indent),
        shape = RoundedCornerShape(14.dp),
        color = when (block.type) {
            NoteBlockType.CODE -> MaterialTheme.colorScheme.surfaceVariant
            NoteBlockType.QUOTE -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.34f)
            else -> MaterialTheme.colorScheme.background
        },
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.72f)),
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box {
                    TextButton(onClick = { typeMenu = true }, enabled = enabled) {
                        Text(block.type.label)
                    }
                    DropdownMenu(expanded = typeMenu, onDismissRequest = { typeMenu = false }) {
                        NoteBlockType.entries.forEach { type ->
                            DropdownMenuItem(
                                text = { Text(type.label) },
                                onClick = {
                                    typeMenu = false
                                    onTypeChange(type)
                                },
                            )
                        }
                    }
                }
                if (block.type.isList) {
                    IconButton(onClick = { onIndent(-1) }, enabled = enabled && block.indent > 0, modifier = Modifier.size(34.dp)) {
                        Text("⇤", style = MaterialTheme.typography.labelMedium)
                    }
                    IconButton(
                        onClick = { onIndent(1) },
                        enabled = enabled && block.indent < 3,
                        modifier = Modifier.size(34.dp),
                    ) {
                        Text("⇥", style = MaterialTheme.typography.labelMedium)
                    }
                }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onMoveUp, enabled = enabled && canMoveUp, modifier = Modifier.size(34.dp)) {
                    Icon(Icons.Rounded.ArrowUpward, contentDescription = uiText("Move block up"), modifier = Modifier.size(17.dp))
                }
                IconButton(onClick = onMoveDown, enabled = enabled && canMoveDown, modifier = Modifier.size(34.dp)) {
                    Icon(Icons.Rounded.ArrowDownward, contentDescription = uiText("Move block down"), modifier = Modifier.size(17.dp))
                }
                IconButton(onClick = onDuplicate, enabled = enabled, modifier = Modifier.size(34.dp)) {
                    Icon(Icons.Rounded.Add, contentDescription = uiText("Duplicate block"), modifier = Modifier.size(17.dp))
                }
                IconButton(onClick = onRemove, enabled = enabled && canRemove, modifier = Modifier.size(34.dp)) {
                    Icon(Icons.Rounded.DeleteOutline, contentDescription = uiText("Delete block"), modifier = Modifier.size(17.dp))
                }
            }
            if (unlinkedReference) {
                Surface(
                    Modifier.fillMaxWidth().padding(bottom = 6.dp),
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.errorContainer,
                ) {
                    Row(
                        Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            uiText("This reference has no target in the workspace."),
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = onRelink, enabled = enabled) { Text(uiText("Relink")) }
                    }
                }
            }
            Row(verticalAlignment = Alignment.Top) {
                if (block.type == NoteBlockType.CHECKLIST) {
                    Checkbox(
                        checked = block.checked,
                        onCheckedChange = { checked ->
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            onCheckedChange(checked)
                        },
                        enabled = enabled,
                    )
                }
                BasicTextField(
                    value = fieldValue,
                    onValueChange = { value ->
                        val caret = value.selection.end
                        val insertedNewline = value.text.length == fieldValue.text.length + 1 &&
                            caret > 0 && value.text.getOrNull(caret - 1) == '\n'
                        if (insertedNewline) {
                            // Enter splits the block instead of embedding a hard break.
                            val splitAt = caret - 1
                            val withoutNewline = value.text.removeRange(splitAt, splitAt + 1)
                            fieldValue = TextFieldValue(withoutNewline, TextRange(splitAt))
                            onTextChange(withoutNewline)
                            onNewlineSplit(splitAt)
                            return@BasicTextField
                        }
                        val deletedEverythingFromStart = value.text.isEmpty() &&
                            fieldValue.text.isNotEmpty() &&
                            fieldValue.selection.start == 0 && fieldValue.selection.end == 0
                        if (deletedEverythingFromStart) {
                            onBackspaceAtStart()
                            return@BasicTextField
                        }
                        fieldValue = value
                        onTextChange(value.text)
                        onSelectionChange(value.selection.start, value.selection.end)
                    },
                    enabled = enabled,
                    textStyle = when (block.type) {
                        NoteBlockType.HEADING -> MaterialTheme.typography.headlineSmall.copy(color = MaterialTheme.colorScheme.onBackground)
                        NoteBlockType.HEADING_2 -> MaterialTheme.typography.titleLarge.copy(color = MaterialTheme.colorScheme.onBackground)
                        NoteBlockType.HEADING_3 -> MaterialTheme.typography.titleMedium.copy(color = MaterialTheme.colorScheme.onBackground)
                        NoteBlockType.CODE -> MaterialTheme.typography.bodyMedium.copy(
                            color = MaterialTheme.colorScheme.onSurface,
                            fontFamily = FontFamily.Monospace,
                        )
                        else -> MaterialTheme.typography.bodyLarge.copy(
                            color = MaterialTheme.colorScheme.onBackground,
                            lineHeight = 25.sp,
                        )
                    },
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.secondary),
                    modifier = Modifier.weight(1f).padding(horizontal = 4.dp, vertical = 8.dp)
                        .focusRequester(focusRequester),
                    decorationBox = { inner ->
                        if (block.text.isEmpty()) {
                            Text(
                                placeholderFor(block.type),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        inner()
                    },
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onAddBelow, enabled = enabled) {
                    Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.size(6.dp))
                    Text(uiText("Add block"))
                }
            }
        }
    }
}

private fun placeholderFor(type: NoteBlockType): String = when (type) {
    NoteBlockType.HEADING -> "Heading"
    NoteBlockType.HEADING_2 -> "Section"
    NoteBlockType.HEADING_3 -> "Sub-section"
    NoteBlockType.CHECKLIST -> "To-do"
    NoteBlockType.BULLET_LIST, NoteBlockType.NUMBERED_LIST -> "List item"
    NoteBlockType.QUOTE -> "Quote"
    NoteBlockType.CODE -> "Code"
    NoteBlockType.REFERENCE -> "Reference"
    NoteBlockType.DIVIDER -> "—"
    NoteBlockType.PARAGRAPH -> "Write something…"
}

// ---------------------------------------------------------------------------------------------
// Attachments and pickers
// ---------------------------------------------------------------------------------------------

@Composable
private fun AttachmentCard(attachment: NoteAttachment, onOpen: () -> Unit, onRemove: () -> Unit, onEdit: () -> Unit) {
    Surface(
        modifier = Modifier.width(if (attachment.type == "IMAGE") 248.dp else 220.dp),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column {
            if (attachment.type == "IMAGE") {
                AttachmentImage(attachment, onOpen)
            }
            if (attachment.type == "IMAGE") TextButton(onClick = onEdit) { Text(uiText("Edit with AI")) } else {
                Row(
                    modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen).padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(Icons.Rounded.Description, contentDescription = null, modifier = Modifier.size(32.dp))
                    Column(Modifier.weight(1f)) {
                        Text(attachment.displayName ?: "Attached file", style = MaterialTheme.typography.titleSmall, maxLines = 2)
                        Text(
                            attachment.mimeType.substringAfterLast('/').uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    attachment.caption ?: attachment.displayName ?: "Attachment",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onRemove, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Rounded.DeleteOutline, contentDescription = uiText("Remove attachment"), modifier = Modifier.size(19.dp))
                }
            }
        }
    }
}

@Composable
private fun AttachmentImage(attachment: NoteAttachment, onOpen: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val bitmap by produceState<Bitmap?>(initialValue = null, attachment.pathOrUri) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val uri = Uri.parse(attachment.pathOrUri)
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
                var sample = 1
                while (bounds.outWidth / sample > 1200 || bounds.outHeight / sample > 1200) sample *= 2
                val options = BitmapFactory.Options().apply { inSampleSize = sample }
                context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
            }.getOrNull()
        }
    }
    Box(
        modifier = Modifier.fillMaxWidth().height(170.dp).clickable(onClick = onOpen),
        contentAlignment = Alignment.Center,
    ) {
        val loaded = bitmap
        if (loaded != null) {
            Image(
                bitmap = loaded.asImageBitmap(),
                contentDescription = attachment.caption ?: attachment.displayName ?: "Attached image",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Rounded.BrokenImage, contentDescription = null, modifier = Modifier.size(32.dp))
                Spacer(Modifier.height(6.dp))
                Text(uiText("Preview unavailable"), style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
private fun OutlineSheet(
    state: NoteEditorUiState,
    onJump: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(uiText("Outline")) },
        text = {
            if (state.outline.isEmpty()) {
                Text(uiText("Add a heading to build an outline of this note."))
            } else {
                val sections = remember(state.blocks) { NotebookSectionTools.sections(state.blocks) }
                val sectionById = remember(sections) { sections.associateBy { it.headingId } }
                var expandedId by remember { mutableStateOf<String?>(null) }
                val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    state.outline.forEach { entry ->
                        val section = sectionById[entry.blockId]
                        Surface(
                            onClick = { expandedId = if (expandedId == entry.blockId) null else entry.blockId },
                            color = MaterialTheme.colorScheme.background,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                Modifier.padding(start = (12 * (entry.level - 1)).dp, top = 8.dp, bottom = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(entry.title, Modifier.weight(1f),
                                    style = when (entry.level) {
                                        1 -> MaterialTheme.typography.titleMedium
                                        2 -> MaterialTheme.typography.titleSmall
                                        else -> MaterialTheme.typography.bodyMedium
                                    })
                                Text(section?.let { "${it.blockCount} blocks" }.orEmpty(),
                                    style = MaterialTheme.typography.labelSmall)
                            }
                        }
                        if (expandedId == entry.blockId && section != null) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.padding(start = 12.dp, bottom = 8.dp)) {
                                TextButton(onClick = { onJump(entry.blockId) }) {
                                    Text(uiText("Go to section"))
                                }
                                TextButton(onClick = {
                                    val extracted = NotebookSectionTools.extract(state.blocks, entry.blockId)
                                    val plainText = extracted.joinToString("\\n\\n") { it.text }
                                    clipboard.setText(AnnotatedString(plainText))
                                }) { Text(uiText("Copy section")) }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(uiText("Close")) } },
    )
}

// ---------------------------------------------------------------------------------------------
// Status bar and helpers
// ---------------------------------------------------------------------------------------------

@Composable
private fun EditorStatusBar(state: NoteEditorUiState) {
    val label = when (state.status) {
        SaveStatus.LOADING -> "Loading…"
        SaveStatus.SAVING -> "Saving…"
        SaveStatus.SAVED -> "Saved locally"
        SaveStatus.CONFLICT -> "Conflict — your edits are kept"
        SaveStatus.FAILED -> "Could not save"
        SaveStatus.MISSING -> "Unavailable"
        SaveStatus.TRASHED -> "In trash — editing disabled"
    }
    val color = when (state.status) {
        SaveStatus.LOADING -> MaterialTheme.colorScheme.onSurfaceVariant
        SaveStatus.SAVING -> HaloPastelBlue
        SaveStatus.SAVED -> HaloPastelMint
        SaveStatus.CONFLICT, SaveStatus.FAILED, SaveStatus.MISSING, SaveStatus.TRASHED ->
            MaterialTheme.colorScheme.error
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).background(color, RoundedCornerShape(3.dp)))
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = color,
                // Announce save-state changes to TalkBack without stealing focus.
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
        Text(
            "${state.wordCount} words",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun MissingNoteState(onBack: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Surface(onClick = onBack, shape = RoundedCornerShape(18.dp), color = HaloPastelPink.copy(alpha = 0.16f)) {
            Column(modifier = Modifier.padding(18.dp)) {
                Text(uiText("Note unavailable"), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(6.dp))
                Text(uiText("This note was deleted or is no longer available. Tap to return."),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Minimal inline markdown renderer for the block surface. */
private fun renderInline(text: String): AnnotatedString = buildAnnotatedString {
    var index = 0
    while (index < text.length) {
        when {
            text.startsWith("**", index) -> {
                val end = text.indexOf("**", index + 2)
                if (end > index) {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(text.substring(index + 2, end)) }
                    index = end + 2
                } else {
                    append(text[index]); index++
                }
            }
            text.startsWith("~~", index) -> {
                val end = text.indexOf("~~", index + 2)
                if (end > index) {
                    withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) {
                        append(text.substring(index + 2, end))
                    }
                    index = end + 2
                } else {
                    append(text[index]); index++
                }
            }
            text[index] == '`' -> {
                val end = text.indexOf('`', index + 1)
                if (end > index) {
                    withStyle(SpanStyle(fontFamily = FontFamily.Monospace)) { append(text.substring(index + 1, end)) }
                    index = end + 1
                } else {
                    append(text[index]); index++
                }
            }
            text[index] == '*' -> {
                val end = text.indexOf('*', index + 1)
                if (end > index) {
                    withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(text.substring(index + 1, end)) }
                    index = end + 1
                } else {
                    append(text[index]); index++
                }
            }
            text[index] == '[' -> {
                val labelEnd = text.indexOf("](", index)
                val urlEnd = if (labelEnd > index) text.indexOf(')', labelEnd) else -1
                if (labelEnd > index && urlEnd > labelEnd) {
                    withStyle(SpanStyle(color = androidx.compose.ui.graphics.Color(0xFF4A78C2))) {
                        append(text.substring(index + 1, labelEnd))
                    }
                    index = urlEnd + 1
                } else {
                    append(text[index]); index++
                }
            }
            else -> {
                append(text[index])
                index++
            }
        }
    }
}
