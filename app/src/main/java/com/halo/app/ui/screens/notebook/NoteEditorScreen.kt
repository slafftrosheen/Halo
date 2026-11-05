package com.halo.app.ui.screens.notebook

import com.halo.app.ui.presence.BindShellPageActions
import com.halo.app.ui.presence.ShellPageAction
import com.halo.app.ui.presence.ShellPageOwner
import com.halo.app.ui.zone5.Zone5InstrumentHeading
import com.halo.app.ui.zone5.Zone5ActionButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import com.halo.app.ui.zone5.Zone5Icon

import com.halo.app.ui.components.HaloTextField
import com.halo.app.ui.components.VoiceFieldKind
import com.halo.app.ui.fx.haloDraftOutline
import com.halo.app.language.uiText
import com.halo.app.ui.components.HaloVoiceTextActions
import com.halo.app.R
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.halo.app.ui.icons.HaloGlyph
import com.halo.app.ui.icons.HaloIcon
import com.halo.app.ui.presence.HaloSignalFace
import com.halo.app.ui.presence.HaloSignalPresentation

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
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
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
import com.halo.app.ui.components.HaloAlertDialog as AlertDialog
import androidx.compose.material3.Button
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.res.painterResource
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
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
import androidx.compose.foundation.layout.widthIn

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun NoteEditorScreen(
    viewModel: NoteEditorViewModel,
    onBack: () -> Unit,
    onOpenStudy: (String) -> Unit = {},
    onOpenCanvas: (String, String) -> Unit = { _, _ -> },
    onStartStudySetup: ((String?, com.halo.sdk.runtime.study.StudySessionSource) -> Unit)? = null,
    onReviewMemory: (String) -> Unit = {},
    onOpenStudio: ((String, String) -> Unit)? = null,
    onOpenOffice: ((String) -> Unit)? = null,
    onExportCopy: (suspend (String, Long, com.halo.app.data.workspace.WorkspaceTextFormat) -> com.halo.app.data.workspace.WorkspaceFile)? = null,
    onRichCopy: (suspend (String, Long) -> Unit)? = null,
) {
    LaunchedEffect(viewModel) { viewModel.memoryReviewEvents.collect { onReviewMemory(it) } }
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    var toolsExpanded by androidx.compose.runtime.saveable.rememberSaveable(viewModel) { mutableStateOf(false) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val editScroll = rememberSaveable(viewModel.noteId, saver = androidx.compose.foundation.lazy.LazyListState.Saver) {
        androidx.compose.foundation.lazy.LazyListState()
    }
    val readScroll = rememberSaveable(viewModel.noteId, saver = androidx.compose.foundation.ScrollState.Saver) {
        androidx.compose.foundation.ScrollState(0)
    }
    var exportDialog by rememberSaveable(viewModel.noteId) { mutableStateOf(false) }
    var exporting by remember { mutableStateOf(false) }
    var exportFailed by remember { mutableStateOf(false) }
    var exportedRevision by rememberSaveable(viewModel.noteId) { mutableStateOf<Long?>(null) }
    var exported by remember { mutableStateOf<com.halo.app.data.workspace.WorkspaceFile?>(null) }
    var copying by remember { mutableStateOf(false) }
    var copyFailed by remember { mutableStateOf(false) }
    val exitScope = rememberCoroutineScope()
    var leaving by remember(viewModel) { mutableStateOf(false) }
    val leaveSafely: () -> Unit = {
        if (!leaving) {
            leaving = true
            exitScope.launch {
                try { if (viewModel.keepBeforeLeaving()) onBack() }
                finally { leaving = false }
            }
        }
    }

    val literalFileFormat = state.blocks.singleOrNull()?.takeIf { it.type == NoteBlockType.CODE }
        ?.let { com.halo.app.data.workspace.WorkspaceTextFormat.fromLanguage(it.language) }
    val lifecycleOwner = LocalLifecycleOwner.current
    val referenceCandidates by viewModel.referenceCandidates.collectAsStateWithLifecycle()

    var changeBlockStyle by remember { mutableStateOf(false) }
    var showOutline by remember { mutableStateOf(false) }
    var insertTarget by remember(viewModel.noteId) { mutableStateOf<Pair<String?, Int>?>(null) }
    var showReferencePicker by remember { mutableStateOf(false) }
    // Non-null while the picker is repairing an existing reference block instead of inserting one.
    var relinkBlockId by remember { mutableStateOf<String?>(null) }
    var showFindBar by remember { mutableStateOf(false) }
    var showImageStudio by remember { mutableStateOf(false) }
    var showAssistMenu by remember { mutableStateOf(false) }
    var showHaloSheet by remember { mutableStateOf(false) }
    var showRename by remember { mutableStateOf(false) }
    var titleDraft by remember(state.title) { mutableStateOf(state.title) }
    var confirmTrash by remember { mutableStateOf(false) }
    var studyError by remember { mutableStateOf<String?>(null) }
    val shellOwnsOrb = com.halo.app.ui.presence.LocalShellHaloOrb.current
    var pendingMode by remember(viewModel.noteId) { mutableStateOf<Triple<EditorMode, String, Long>?>(null) }
    val requestMode: (EditorMode) -> Unit = { mode ->
        val current = viewModel.uiState.value
        if (current.sourceDirty && mode != EditorMode.SOURCE) pendingMode = Triple(mode, current.sourceDraft, current.revision)
        else viewModel.setMode(mode)
    }
    val insertEnabled = state.isEditable && state.mode == EditorMode.EDIT && literalFileFormat == null && !state.isGenerating
    val pageOwner = ShellPageOwner("note", viewModel.noteId, "${state.revision}:${state.status}:${state.blocks.hashCode()}:${state.mode}:${state.selection}",
        canonicalSourceVersion = state.revision.toString().takeIf { state.status == SaveStatus.SAVED },
        selectionIds = listOfNotNull(state.selection?.blockId),
        helpDescription = stringResource(R.string.zone5_help_note),
        guidanceBlocked = pendingMode != null || state.isGenerating || exporting || exportDialog || exported != null || state.status in setOf(SaveStatus.LOADING, SaveStatus.FAILED, SaveStatus.CONFLICT, SaveStatus.MISSING, SaveStatus.TRASHED) ||
            insertTarget != null || showRename || toolsExpanded || showHaloSheet || showAssistMenu || showReferencePicker || showFindBar || showImageStudio,
        completedGuideIds = if (state.displayBody.isNotBlank() && (state.draftKept || state.status == SaveStatus.SAVED)) setOf("note-insert.v1", "library-new.v1") else emptySet(),
        helpSteps = listOf(
            com.halo.app.ui.presence.ShellGuideStep("note-insert.v1", "page.note.insert", stringResource(R.string.zone5_guide_note), state.displayBody.isNotBlank() && (state.draftKept || state.status == SaveStatus.SAVED)),
            com.halo.app.ui.presence.ShellGuideStep("note-read.v1", "page.note.view-read", uiText("Read"), state.mode == EditorMode.READ),
            com.halo.app.ui.presence.ShellGuideStep("note-export.v1", "page.note.export", stringResource(R.string.zone5_help_make_copy), exportedRevision == state.revision && state.status == SaveStatus.SAVED)),
        nextStep = if (insertEnabled && state.displayBody.isBlank() && insertTarget == null && !showRename && !toolsExpanded &&
            !showHaloSheet && !showAssistMenu && !showReferencePicker && !showFindBar && !showImageStudio)
            com.halo.app.ui.presence.ShellNextStep("note-insert.v1", "page.note.insert", stringResource(R.string.zone5_guide_note)) else null,
        canonicalToolIds = listOf("halo_text_rich_copy", "halo_note_get", "halo_note_read_block", "halo_note_insert_block", "halo_note_edit_block",
            "halo_note_replace_content", "halo_note_rename"),
        defaultDisabledActionReason = stringResource(when {
            state.status == SaveStatus.LOADING -> R.string.action_loading
            state.status == SaveStatus.MISSING -> R.string.action_missing
            state.status == SaveStatus.CONFLICT -> R.string.action_conflict
            state.status == SaveStatus.TRASHED -> R.string.action_readonly
            state.mode == EditorMode.SOURCE -> R.string.action_source_mode
            state.mode == EditorMode.READ -> R.string.zone5_edit_first
            else -> R.string.action_wait
        }),
        disabledActionReasons = buildMap {
            if (state.isEditable) put("page.note.insert", stringResource(when {
                literalFileFormat != null -> R.string.zone5_literal_format
                state.isGenerating -> R.string.action_wait
                else -> R.string.zone5_edit_first
            }))
            put("page.note.export", stringResource(when {
                onExportCopy == null -> R.string.zone5_host_unavailable
                state.status == SaveStatus.LOADING -> R.string.action_loading
                state.status == SaveStatus.MISSING -> R.string.action_missing
                exporting || state.isGenerating -> R.string.action_wait
                state.mode == EditorMode.SOURCE -> R.string.action_source_mode
                else -> R.string.zone5_save_first
            }))
            put("page.note.undo", stringResource(if (state.isGenerating) R.string.action_wait else R.string.action_no_undo))
            put("page.note.redo", stringResource(if (state.isGenerating) R.string.action_wait else R.string.action_no_redo))
        })
    BindShellPageActions(pageOwner, listOf(
        ShellPageAction("page.note.insert", stringResource(R.string.zone5_insert), HaloGlyph.NOTE, insertEnabled) {
            changeBlockStyle = false
            val current = viewModel.uiState.value
            if (current.isEditable && current.mode == EditorMode.EDIT && !current.isGenerating)
                insertTarget = (current.selection?.blockId ?: current.blocks.lastOrNull()?.id) to noteInsertSignature(current)
        },
        ShellPageAction("page.note.outline", "Outline", HaloGlyph.NOTE, state.status !in setOf(SaveStatus.LOADING, SaveStatus.MISSING)) { showOutline = true },
        ShellPageAction("page.note.find", "Find and replace", HaloGlyph.SEARCH, state.status !in setOf(SaveStatus.LOADING, SaveStatus.MISSING)) { showFindBar = !showFindBar },
        ShellPageAction("page.note.rename", "Rename", HaloGlyph.NOTE, state.isEditable) { titleDraft = state.title; showRename = true },
        ShellPageAction("page.note.assist", "Think with halo", HaloGlyph.TOOL, state.isEditable) { showHaloSheet = true },
        ShellPageAction("page.note.export", stringResource(R.string.note_export_copy), HaloGlyph.FILE,
            onExportCopy != null && state.status == SaveStatus.SAVED && state.mode != EditorMode.SOURCE && !state.isGenerating && !exporting) {
            val current = viewModel.uiState.value
            if (current.status == SaveStatus.SAVED && current.mode != EditorMode.SOURCE && !current.isGenerating) exportDialog = true
        },
        ShellPageAction("page.note.tools", "More tools", HaloGlyph.TOOL) { toolsExpanded = true },
        ShellPageAction("page.note.undo", "Undo", HaloGlyph.PREVIOUS, state.canUndo && !state.isGenerating, viewModel::undo),
        ShellPageAction("page.note.redo", "Redo", HaloGlyph.NEXT, state.canRedo && !state.isGenerating, viewModel::redo),
        ShellPageAction("page.note.view-read", "Read", HaloGlyph.READ_MODE, state.status !in setOf(SaveStatus.LOADING, SaveStatus.MISSING)) { requestMode(EditorMode.READ) },
        ShellPageAction("page.note.view-edit", "Edit", HaloGlyph.NOTE,
            state.status !in setOf(SaveStatus.LOADING, SaveStatus.MISSING, SaveStatus.TRASHED) && !state.isGenerating) { requestMode(EditorMode.EDIT) },
    ))

    pendingMode?.let { target ->
        val current = state.mode == EditorMode.SOURCE && state.sourceDraft == target.second && state.revision == target.third
        AlertDialog(onDismissRequest = { pendingMode = null },
            title = { Text(stringResource(R.string.note_discard_source_title)) },
            text = { Text(stringResource(if (current) R.string.note_discard_source_effect else R.string.action_source_changed)) },
            confirmButton = { TextButton(onClick = {
                if (viewModel.uiState.value.let { it.mode == EditorMode.SOURCE && it.sourceDraft == target.second && it.revision == target.third })
                    viewModel.setMode(target.first)
                pendingMode = null
            }, enabled = current) { Text(uiText("Discard")) } },
            dismissButton = { TextButton(onClick = { pendingMode = null }) { Text(uiText("Cancel")) } })
    }
    if (shellOwnsOrb) com.halo.app.ui.presence.BindShellUtilityBand(pageOwner) {
        ToolZoneHeader(workspaceDisplayTitle(state.title, state.displayBody, uiText("Untitled note")),
            onBack = leaveSafely,
            subtitle = uiText(noteSaveLabel(state)),
            onTitleClick = if (state.isEditable) ({ titleDraft = state.title; showRename = true }) else null) {
            val renameLabel = uiText("Rename")
            IconButton(onClick = { titleDraft = state.title; showRename = true }, enabled = state.isEditable, modifier = Modifier.size(48.dp)) {
                Box(Modifier.semantics { contentDescription = renameLabel }) { Zone5Icon("action_rename", HaloGlyph.NOTE, Modifier.size(24.dp)) }
            }
            if (state.displayBody.isNotBlank()) com.halo.app.ui.components.HaloReadAloud(state.displayBody, compact = true)
        }
    }

    if (showImageStudio) viewModel.imageStudio?.let { studio ->
        com.halo.app.ui.workspace.WorkspaceImageDialog(studio) { showImageStudio = false }
    }

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(viewModel::addAttachment)
    }
    insertTarget?.let { target ->
        val valid = insertEnabled && noteInsertSignature(state) == target.second
        ModalBottomSheet(onDismissRequest = { insertTarget = null }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
            Column(Modifier.fillMaxWidth().weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.zone5_insert), style = MaterialTheme.typography.titleLarge)
                if (!valid) Text(stringResource(R.string.zone5_document_changed), color = MaterialTheme.colorScheme.onSurfaceVariant)
                listOf(NoteBlockType.PARAGRAPH, NoteBlockType.HEADING, NoteBlockType.BULLET_LIST,
                    NoteBlockType.NUMBERED_LIST, NoteBlockType.CHECKLIST, NoteBlockType.QUOTE,
                    NoteBlockType.CODE, NoteBlockType.DIVIDER).forEach { type ->
                    Zone5ActionButton(uiText(type.label), "action_add", valid, Modifier.fillMaxWidth()) {
                        val current = viewModel.uiState.value
                        if (noteInsertSignature(current) == target.second && current.isEditable) {
                            val block = current.blocks.find { it.id == target.first }
                            if (block != null && (changeBlockStyle || block.type == NoteBlockType.PARAGRAPH && block.text.isBlank()) && type != NoteBlockType.DIVIDER)
                                viewModel.changeBlockType(block.id, type)
                            else viewModel.insertBlock(target.first, type)
                        }
                        insertTarget = null
                    }
                }
                Zone5ActionButton(uiText("Attach"), "action_attach", valid, Modifier.fillMaxWidth()) {
                    insertTarget = null; filePicker.launch(arrayOf("image/*", "application/pdf", "text/*", "audio/*", "video/*"))
                }
                Zone5ActionButton(uiText("Link a workspace item"), "action_link", valid, Modifier.fillMaxWidth()) {
                    insertTarget = null; relinkBlockId = null; showReferencePicker = true
                }
            }
        }
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
    BackHandler(enabled = true, onBack = leaveSafely)

    val startStudy: (String?) -> Unit = remember(viewModel, onOpenStudy, onStartStudySetup) {
        { passage ->
            if (onStartStudySetup != null) {
                // The source must be durably saved before Study reads it.
                // A failed/conflicted note stays in the editor for recovery.
                scope.launch {
                    val source = viewModel.studySetupSource()
                    if (source != null) onStartStudySetup(passage, source)
                    else studyError = "Your selected passage changed. Select it again before starting Study."
                }
            } else {
                scope.launch {
                    when (val result = viewModel.startStudy(passage)) {
                        is StudyHandoff.Started -> onOpenStudy(result.sessionId)
                        is StudyHandoff.Unavailable -> studyError = result.message
                    }
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
            text = { Text(uiText(message)) },
            confirmButton = { TextButton(onClick = { studyError = null }) { Text(uiText("OK")) } },
        )
    }

    if (showRename) AlertDialog(
        onDismissRequest = { showRename = false },
        title = { Text(uiText("Rename")) },
        text = {
            HaloTextField(
                value = titleDraft,
                onValueChange = { titleDraft = it.take(160) },
                singleLine = true,
                label = { Text(uiText("Title")) },
                modifier = Modifier.fillMaxWidth(), kind = VoiceFieldKind.SHORT, maxChars = 160)
        },
        confirmButton = {
            TextButton(
                enabled = state.isEditable && titleDraft.trim().isNotBlank(),
                onClick = {
                    val candidate = titleDraft.trim()
                    showRename = false
                    if (candidate != state.title) viewModel.renameTitle(candidate)
                }
            ) { Text(uiText("Save")) }
        },
        dismissButton = {
            TextButton(onClick = { showRename = false }) { Text(uiText("Cancel")) }
        }
    )
    if (confirmTrash) AlertDialog(onDismissRequest = { confirmTrash = false },
        title = { Text(uiText("Move to trash")) },
        text = { Text(uiText("You can restore them from the Trash filter.")) },
        confirmButton = { TextButton(onClick = { confirmTrash = false; viewModel.moveToTrash() }) { Text(uiText("Move to trash")) } },
        dismissButton = { TextButton(onClick = { confirmTrash = false }) { Text(uiText("Cancel")) } })
    if (showHaloSheet) androidx.compose.material3.ModalBottomSheet(onDismissRequest = { showHaloSheet = false }) {
        Column(Modifier.fillMaxWidth().heightIn(max = 680.dp).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                // Neutral Open Ring identifies Halo; it does not imply that inference started.
                HaloSignalFace(HaloSignalPresentation(reducedMotion = true), size = 48.dp,
                    contentDescription = uiText("halo"))
                Text(uiText("Think with halo"), style = MaterialTheme.typography.titleLarge)
            }
            NoteHaloAction("Ask halo", HaloGlyph.THINK,
                enabled = state.isEditable && !state.isGenerating) {
                showHaloSheet = false
                showAssistMenu = true
            }
            if (state.lifecycle == WorkspaceLifecycle.ACTIVE) com.halo.app.ui.workspace.WorkspaceContinueButton(viewModel.noteId,
                enabled = state.status == SaveStatus.SAVED && !state.isGenerating)
            NoteHaloAction("Study this note", HaloGlyph.NOTE) {
                showHaloSheet = false
                startStudy(null)
            }
            if (state.lifecycle == WorkspaceLifecycle.ACTIVE) {
                NoteHaloAction("Remember in halo", HaloGlyph.MEMORY) {
                    showHaloSheet = false
                    viewModel.rememberNote()
                }
            }
            if (viewModel.imageStudio != null) NoteHaloAction(
                "Create an image", HaloGlyph.CAMERA, enabled = !state.isGenerating) {
                showHaloSheet = false
                viewModel.imageStudio?.prepare()
                showImageStudio = true
            }
            NoteHaloAction("Use in conversation", HaloGlyph.CONVERSATION) {
                showHaloSheet = false
                viewModel.useInConversation()
            }
            HorizontalDivider(Modifier.padding(vertical = 8.dp),
                color = MaterialTheme.colorScheme.outlineVariant)
            if (state.isEditable) NoteHaloAction("Find and replace", HaloGlyph.SEARCH) {
                showHaloSheet = false
                viewModel.setMode(EditorMode.EDIT)
                showFindBar = true
            }
            if (state.lifecycle == WorkspaceLifecycle.ACTIVE) {
                NoteHaloAction("Archive", HaloGlyph.STORAGE) {
                    showHaloSheet = false
                    viewModel.archive()
                }
            } else {
                NoteHaloAction("Restore", HaloGlyph.STORAGE) {
                    showHaloSheet = false
                    viewModel.restore()
                }
            }
            if (state.lifecycle != WorkspaceLifecycle.TRASHED) {
                NoteHaloAction("Move to trash", HaloGlyph.TRASH, destructive = true) {
                    showHaloSheet = false
                    confirmTrash = true
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
    if (showAssistMenu) androidx.compose.material3.ModalBottomSheet(onDismissRequest = { showAssistMenu = false }) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp)) {
            Text(uiText("Ask halo"), style = MaterialTheme.typography.titleLarge)
            DocumentOperation.entries.forEach { operation ->
                val needsSelection = operation in setOf(DocumentOperation.REWRITE,
                    DocumentOperation.TURN_INTO_TASKS, DocumentOperation.EXPLAIN, DocumentOperation.TRANSLATE)
                TextButton(onClick = { showAssistMenu = false; viewModel.requestAssistance(operation) },
                    enabled = state.isEditable && !state.isGenerating && (!needsSelection || state.selection != null)) {
                    Text(uiText(operation.label))
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
    if (toolsExpanded) androidx.compose.material3.ModalBottomSheet(onDismissRequest = { toolsExpanded = false }) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.zone5_group_view), style = MaterialTheme.typography.titleSmall)
            ModeToggle(state.mode, requestMode, allowSource = literalFileFormat == null)
            HorizontalDivider()
            Text(stringResource(R.string.zone5_group_edit), style = MaterialTheme.typography.titleSmall)
            EditorToolbar(state, { toolsExpanded = false; showOutline = true }, { toolsExpanded = false; showFindBar = !showFindBar },
                viewModel::undo, viewModel::redo)
            if (state.mode == EditorMode.EDIT && state.isEditable) NoteFocusDock(state, viewModel,
                onAdd = { filePicker.launch(arrayOf("image/*", "application/pdf", "text/*", "audio/*", "video/*")) },
                onHalo = { toolsExpanded = false; showHaloSheet = true },
                onExport = onExportCopy?.let { ({
                    if (viewModel.uiState.value.status == SaveStatus.SAVED) { toolsExpanded = false; exportDialog = true }
                }) })
            if (state.mode == EditorMode.EDIT && state.isEditable && state.selection?.let { it.start != it.end } == true)
                SelectionToolbar(state, viewModel,
                    onPickReference = { toolsExpanded = false; relinkBlockId = null; showReferencePicker = true },
                    onAssist = { toolsExpanded = false; showHaloSheet = true }, onStudyPassage = { startStudy(null) },
                    onAttach = { filePicker.launch(arrayOf("image/*", "application/pdf", "text/*", "audio/*", "video/*")) })
            Spacer(Modifier.height(24.dp))
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(com.halo.app.ui.theme.haloWorkspaceBackground())
            .padding(horizontal = 20.dp, vertical = 10.dp)
            // Keep the selection toolbar directly above the keyboard instead of under it.
            .then(if (shellOwnsOrb) Modifier else Modifier.imePadding())
    ) {
        if (shellOwnsOrb) Zone5InstrumentHeading(
            if (literalFileFormat == null) uiText("Notes") else stringResource(R.string.file52_title),
            "tool_notes",
            workspaceDisplayTitle(state.title, state.displayBody, uiText("Untitled note")),
            state = literalFileFormat?.let { stringResource(R.string.zone5_literal_format) })
        if (!shellOwnsOrb) EditorWorkspaceHeader(workspaceDisplayTitle(state.title, state.displayBody, uiText("Untitled note")), toolsExpanded,
            onToggle = { toolsExpanded = !toolsExpanded },
            onBack = leaveSafely, onHalo = { showHaloSheet = true },
            subtitle = when (literalFileFormat) {
                com.halo.app.data.workspace.WorkspaceTextFormat.TXT -> stringResource(R.string.file52_text)
                com.halo.app.data.workspace.WorkspaceTextFormat.MARKDOWN -> stringResource(R.string.file52_markdown)
                com.halo.app.data.workspace.WorkspaceTextFormat.CSV -> stringResource(R.string.file52_csv)
                else -> uiText("Your note")
            },
            onTitleClick = if (state.isEditable) ({ titleDraft = state.title; showRename = true }) else null,
            fallbackTitle = uiText("Untitled note"), readText = state.displayBody)
        if (literalFileFormat != null && onRichCopy != null) {
            Text(uiText("This file keeps its original format. Make a separate rich note to add headings, paragraphs and lists."), style = MaterialTheme.typography.bodySmall)
            TextButton(enabled = state.status == SaveStatus.SAVED && !state.isGenerating && !copying,
                onClick = {
                    val saved = viewModel.uiState.value
                    if (saved.status == SaveStatus.SAVED && !copying) {
                        copying = true; copyFailed = false
                        scope.launch {
                            try { onRichCopy(viewModel.noteId, saved.revision) }
                            catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
                            catch (_: Exception) { copyFailed = true }
                            finally { copying = false }
                        }
                    }
                }) { Text(uiText("Make rich-note copy")) }
            if (copyFailed) Text(uiText("Copy could not be confirmed. Check your notes before trying again."), color = MaterialTheme.colorScheme.error)
        }
        if (state.status == SaveStatus.MISSING) {
            MissingNoteState(onBack, Modifier.weight(1f))
            return@Column
        }

        Spacer(Modifier.height(8.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        com.halo.app.ui.fx.HaloEventMoment(state.appliedDraftEvent, com.halo.app.ui.fx.HaloPackageMoment.APPLY_DRAFT, Modifier.size(80.dp))
        state.notice?.let { notice ->
            StatusBanner(
                notice, MaterialTheme.colorScheme.secondaryContainer,
                onUndo = if (notice == "Draft applied to your note. You can undo this change." &&
                    state.canUndo) viewModel::undo else null,
                onDismiss = viewModel::clearNotice,
            )
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
            StatusBanner(stringResource(if (state.draftKept) R.string.note_save_failed_kept else R.string.note_save_failed_unkept), MaterialTheme.colorScheme.errorContainer, onRetry = viewModel::retrySave)
        }
        state.proposalError?.let { error ->
            StatusBanner(error, MaterialTheme.colorScheme.errorContainer) { viewModel.clearNotice() }
        }

        if (showFindBar) {
            FindReplaceBar(state, viewModel, onClose = { showFindBar = false })
        }

        when (state.mode) {
            EditorMode.SOURCE -> SourceModeEditor(state, viewModel) { requestMode(EditorMode.EDIT) }
            EditorMode.READ -> ReadModeView(state, readScroll, Modifier.weight(1f))
            EditorMode.EDIT -> EditModeView(
                state = state,
                listState = editScroll,
                viewModel = viewModel,
                showTools = toolsExpanded && literalFileFormat == null,
                onImage = { attachment -> viewModel.imageStudio?.prepare(attachment); showImageStudio = true },
                onStudio = onOpenStudio,
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
                showAssistMenu = false,
                context = context,
                onOpenCanvas = onOpenCanvas,
            )
        }

        if (!shellOwnsOrb && state.mode == EditorMode.EDIT && state.isEditable && !toolsExpanded &&
            state.selection?.let { it.start != it.end } == true) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            SelectionToolbar(state, viewModel,
                onPickReference = { relinkBlockId = null; showReferencePicker = true },
                onAssist = { showHaloSheet = true }, onStudyPassage = { startStudy(null) },
                onAttach = { filePicker.launch(arrayOf("image/*", "application/pdf", "text/*", "audio/*", "video/*")) })
        }
        if (shellOwnsOrb && insertEnabled && WindowInsets.ime.getBottom(androidx.compose.ui.platform.LocalDensity.current) > 0) {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
                TextButton({ changeBlockStyle = false; insertTarget = (state.selection?.blockId ?: state.blocks.lastOrNull()?.id) to noteInsertSignature(state) },
                    Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.zone5_insert)) }
                TextButton({ changeBlockStyle = true; insertTarget = (state.selection?.blockId ?: state.blocks.lastOrNull()?.id) to noteInsertSignature(state) },
                    Modifier.heightIn(min = 48.dp)) { Text(uiText("Block style")) }
                TextButton(viewModel::undo, Modifier.heightIn(min = 48.dp), enabled = state.canUndo) { Text(uiText("Undo")) }
            }
        }
        if (!shellOwnsOrb && state.mode == EditorMode.EDIT && state.isEditable) NoteFocusDock(state, viewModel,
            onAdd = { filePicker.launch(arrayOf("image/*", "application/pdf", "text/*", "audio/*", "video/*")) },
            onHalo = { showHaloSheet = true },
            onExport = onExportCopy?.let { ({
                if (viewModel.uiState.value.status == SaveStatus.SAVED) exportDialog = true
            }) })
        if (!shellOwnsOrb || state.status in setOf(SaveStatus.CONFLICT, SaveStatus.FAILED, SaveStatus.MISSING, SaveStatus.TRASHED)) EditorStatusBar(state)
    }
    if (exportDialog && onExportCopy != null) AlertDialog(onDismissRequest = { if (!exporting) exportDialog = false },
        title = { Text(stringResource(R.string.note_export_copy)) }, text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.note_export_format))
                if (exporting) CircularProgressIndicator()
                if (exportFailed) Text(stringResource(R.string.note_export_failed), color = MaterialTheme.colorScheme.error)
                val formats = literalFileFormat?.let(::listOf) ?: listOf(
                    com.halo.app.data.workspace.WorkspaceTextFormat.MARKDOWN, com.halo.app.data.workspace.WorkspaceTextFormat.TXT)
                formats.forEach { format -> TextButton(onClick = {
                    exporting = true; exportFailed = false
                    scope.launch {
                        try {
                            check(viewModel.flushBeforeStudy())
                            val sourceRevision = viewModel.uiState.value.revision
                            exported = onExportCopy(viewModel.noteId, sourceRevision, format)
                            exportedRevision = sourceRevision
                            exportDialog = false
                        } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
                        catch (_: Exception) { exportFailed = true }
                        finally { exporting = false }
                    }
                }, enabled = !exporting, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(format.extension.uppercase()) } }
            }
        }, confirmButton = { TextButton(onClick = { exportDialog = false }, enabled = !exporting) { Text(uiText("Cancel")) } })
    exported?.let { file -> AlertDialog(onDismissRequest = { exported = null },
        title = { Text(stringResource(R.string.file52_output_saved)) },
        text = { Column(Modifier.verticalScroll(rememberScrollState())) { Text(file.displayName); com.halo.app.ui.workspace.WorkspaceExportButton(file)
            com.halo.app.ui.workspace.WorkspaceContinueButton(file.objectId.value, file.version) } },
        confirmButton = { TextButton(onClick = { exported = null }) { Text(stringResource(R.string.file52_done)) } }) }
}

/** A single discoverable Halo action surface; destructive actions remain explicit. */
@Composable
internal fun NoteHaloAction(
    label: String,
    glyph: HaloGlyph,
    enabled: Boolean = true,
    destructive: Boolean = false,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        // A destructive menu entry is not a runtime failure. Its confirmation
        // dialog carries the destructive semantics; red remains for real errors.
        shape = com.halo.app.ui.design.HaloShapes.card,
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = androidx.compose.foundation.BorderStroke(
            if (destructive) 2.dp else 1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            HaloIcon(glyph, contentDescription = null,
                modifier = Modifier.size(22.dp), tint = MaterialTheme.colorScheme.onSurface)
            Text(uiText(label), style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Toolbar and headers
// ---------------------------------------------------------------------------------------------

@Composable
private fun EditorToolbar(
    state: NoteEditorUiState,
    onToggleOutline: () -> Unit,
    onToggleFind: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onUndo, enabled = state.canUndo, modifier = Modifier.size(48.dp)) {
            Icon(androidx.compose.ui.res.painterResource(R.drawable.halo_ic_undo),
                contentDescription = uiText("Undo"))
        }
        IconButton(onClick = onRedo, enabled = state.canRedo, modifier = Modifier.size(48.dp)) {
            Icon(androidx.compose.ui.res.painterResource(R.drawable.halo_ic_redo),
                contentDescription = uiText("Redo"))
        }
        IconButton(onClick = onToggleOutline, modifier = Modifier.size(48.dp)) {
            Icon(androidx.compose.ui.res.painterResource(R.drawable.halo_ic_bullet_list),
                contentDescription = uiText("Outline"))
        }
        IconButton(onClick = onToggleFind, modifier = Modifier.size(48.dp)) {
            Icon(androidx.compose.ui.res.painterResource(R.drawable.halo_ic_find_replace),
                contentDescription = uiText("Find and replace"))
        }
    }
}

/** Exactly one mode switch, sized for touch and screen readers. Read does not reveal editing controls. */
@Composable
internal fun ModeToggle(mode: EditorMode, onModeChange: (EditorMode) -> Unit, allowSource: Boolean = true) {
    Row(
        Modifier.fillMaxWidth().selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        EditorMode.entries.filter { allowSource || it != EditorMode.SOURCE }.forEach { entry ->
            val active = entry == mode
            Surface(
                modifier = Modifier.weight(1f).heightIn(min = 48.dp).selectable(
                    selected = active,
                    role = Role.RadioButton,
                    onClick = { onModeChange(entry) }
                ),
                shape = com.halo.app.ui.design.HaloShapes.control,
                color = if (active) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceVariant,
                border = BorderStroke(1.dp, if (active) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.outlineVariant),
            ) {
                Box(Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    contentAlignment = Alignment.Center) {
                    Text(
                        text = uiText(when (entry) {
                            EditorMode.EDIT -> "Edit"
                            EditorMode.READ -> "Read"
                            EditorMode.SOURCE -> "Plain text view"
                        }),
                        style = MaterialTheme.typography.labelLarge,
                        color = if (active) MaterialTheme.colorScheme.onPrimaryContainer
                            else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
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
    onUndo: (() -> Unit)? = null,
) {
    Surface(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        shape = com.halo.app.ui.design.HaloShapes.control,
        color = container,
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(message, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
            onRetry?.let { retry -> TextButton(onClick = retry) { Text(uiText("Retry")) } }
            onUndo?.let { undo -> TextButton(onClick = undo,
                modifier = Modifier.heightIn(min = 48.dp)) { Text(uiText("Undo")) } }
            onDismiss?.let { dismiss ->
                IconButton(onClick = dismiss) { Icon(androidx.compose.ui.res.painterResource(com.halo.app.R.drawable.halo_ic_close), contentDescription = uiText("Dismiss")) }
            }
        }
    }
}

@Composable
private fun RecoveryBanner(preview: String, onApply: () -> Unit, onDiscard: () -> Unit) {
    Surface(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        shape = com.halo.app.ui.design.HaloShapes.control,
        color = MaterialTheme.colorScheme.secondaryContainer,
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
        shape = com.halo.app.ui.design.HaloShapes.control,
        color = MaterialTheme.colorScheme.errorContainer,
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(uiText("Save conflict"), style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(4.dp))
            Text(
                uiText("This note changed elsewhere. Your unsaved changes are still here."),
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
    listState: androidx.compose.foundation.lazy.LazyListState,
    showTools: Boolean,
    viewModel: NoteEditorViewModel,
    onAddAttachment: () -> Unit,
    onImage: (NoteAttachment?) -> Unit,
    onStudio: ((String, String) -> Unit)?,
    onPickReference: () -> Unit,
    onPickExistingReference: (String) -> Unit,
    onAssist: () -> Unit,
    onDismissAssist: () -> Unit,
    onStudyPassage: (String?) -> Unit,
    showAssistMenu: Boolean,
    context: android.content.Context,
    onOpenCanvas: (String, String) -> Unit,
) {
    val enabled = state.isEditable
    val inkCanvasTarget = remember(state.body) { com.halo.app.data.notebook.InkSelectionStudyMaterial.canvasTarget(state.body) }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxWidth().weight(1f).background(MaterialTheme.colorScheme.background),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (showTools || state.isGenerating || state.attachments.isNotEmpty()) item(key = "editor-controls") {
            Column {
    inkCanvasTarget?.let { (canvasId, selectionId) ->
        TextButton(onClick = { onOpenCanvas(canvasId, selectionId) }) {
            Text(uiText("Open original Canvas"))
        }
    }
    if (showTools) SelectionToolbar(
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

    if (showTools && viewModel.imageStudio != null) TextButton(onClick = { onImage(null) }, enabled = enabled && !state.isGenerating) { Text(uiText("Create an image")) }
    if (state.isGenerating) TextButton(onClick = viewModel::cancelAssistance) { Text(uiText("Cancel AI writing")) }


    if (showTools) TextButton(onClick=viewModel::useInConversation) { Text(uiText("Use in conversation")) }
    if (showTools && state.attachments.count { it.type == "IMAGE" } == 1) {
        TextButton(onClick = { viewModel.reviewCaptureImage() }) { Text(uiText("Review image with halo")) }
    }
    if (state.attachments.isNotEmpty()) {
        Spacer(Modifier.height(8.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            items(state.attachments, key = NoteAttachment::id) { attachment ->
                AttachmentCard(
                    attachment = attachment,
                    onOpen = {
                        runCatching {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW)
                                    .setDataAndType(Uri.parse(attachment.pathOrUri), attachment.mimeType)
                                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            )
                        }
                    },
                    onRemove = { viewModel.removeAttachment(attachment.id) },
                    onEdit = { onImage(attachment) },
                    onStudio = onStudio?.let { open -> { open(attachment.noteId, attachment.id) } },
                )
            }
        }
    }
    state.attachmentError?.let { error ->
        Text(error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
    }

    if (showTools) Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Surface(
            onClick = onAddAttachment,
            enabled = enabled && !state.isAddingAttachment,
            shape = com.halo.app.ui.design.HaloShapes.control,
            color = MaterialTheme.colorScheme.surfaceVariant,
        ) {
            Row(Modifier.padding(horizontal = 10.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(androidx.compose.ui.res.painterResource(com.halo.app.R.drawable.halo_ic_attach), contentDescription = uiText("Add file"), modifier = Modifier.size(16.dp))
                Spacer(Modifier.size(6.dp))
                Text(uiText("Attach"), style = MaterialTheme.typography.labelMedium)
            }
        }
        Spacer(Modifier.width(8.dp))
        Surface(
            onClick = onPickReference,
            enabled = enabled,
            shape = com.halo.app.ui.design.HaloShapes.control,
            color = MaterialTheme.colorScheme.surfaceVariant,
        ) {
            Row(Modifier.padding(horizontal = 10.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(androidx.compose.ui.res.painterResource(com.halo.app.R.drawable.halo_ic_link), contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.size(6.dp))
                Text(uiText("Insert object"), style = MaterialTheme.typography.labelMedium)
            }
        }
        Spacer(Modifier.weight(1f))
        if (state.isGenerating) {
            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(8.dp))
            Text(uiText("halo is writing…"), style = MaterialTheme.typography.labelSmall)
        }
    }

            }
        }
        if (state.status == SaveStatus.LOADING) item(key = "loading") {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
        items(state.blocks, key = NoteBlock::id) { block ->
            NoteBlockEditor(
                block = block,
                showTools = showTools,
                enabled = enabled,
                canMoveUp = block.position > 0,
                canMoveDown = block.position < state.blocks.lastIndex,
                canRemove = state.blocks.size > 1,
                focusRequest = state.focusRequest?.takeIf { it.blockId == block.id },
                onFocusHandled = viewModel::consumeFocusRequest,
                onTextChange = { viewModel.updateBlock(block.id, it) },
                currentText = { viewModel.uiState.value.blocks.firstOrNull { it.id == block.id }?.text },
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
        item(key = "halo-draft") {
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
    onAttach: (() -> Unit)? = null,
) {
    var linkDialog by remember { mutableStateOf(false) }
    Surface(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        shape = com.halo.app.ui.design.HaloShapes.pill,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val hasSelection = state.selection != null
        IconButton(onClick = viewModel::undo, enabled = state.canUndo) { Icon(androidx.compose.ui.res.painterResource(com.halo.app.R.drawable.halo_ic_undo), uiText("Undo")) }
        IconButton(onClick = viewModel::redo, enabled = state.canRedo) { Icon(androidx.compose.ui.res.painterResource(com.halo.app.R.drawable.halo_ic_redo), uiText("Redo")) }
        if (onAttach != null) IconButton(onClick = onAttach) { Icon(androidx.compose.ui.res.painterResource(com.halo.app.R.drawable.halo_ic_attach), uiText("Attach")) }
        IconButton(onClick = { viewModel.toggleInlineStyle(InlineStyle.BOLD) }, enabled = hasSelection) {
            Icon(androidx.compose.ui.res.painterResource(com.halo.app.R.drawable.halo_ic_bold), contentDescription = uiText("Bold"))
        }
        IconButton(onClick = { viewModel.toggleInlineStyle(InlineStyle.ITALIC) }, enabled = hasSelection) {
            Icon(androidx.compose.ui.res.painterResource(com.halo.app.R.drawable.halo_ic_italic), contentDescription = uiText("Italic"))
        }
        IconButton(onClick = { viewModel.toggleInlineStyle(InlineStyle.STRIKE) }, enabled = hasSelection) {
            Icon(Icons.Rounded.FormatStrikethrough, contentDescription = uiText("Strikethrough"))
        }
        IconButton(onClick = { linkDialog = true }, enabled = hasSelection) {
            Icon(androidx.compose.ui.res.painterResource(com.halo.app.R.drawable.halo_ic_link), contentDescription = uiText("Add link"))
        }
        Spacer(Modifier.width(4.dp))
        BlockTypeQuickButtons(enabled = hasSelection) { type ->
            viewModel.convertSelectionTo(type)
        }
        Spacer(Modifier.width(4.dp))
        TextButton(onClick = onPickReference) { Text(uiText("Object")) }
        TextButton(onClick = onAssist) { Text(uiText("Ask halo")) }
        TextButton(onClick = onStudyPassage) { Text(uiText("Study")) }
    }
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
                shape = com.halo.app.ui.design.HaloShapes.control,
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
            HaloTextField(
                value = url,
                onValueChange = { url = it },
                singleLine = true,
                label = { Text(uiText("URL")) }, kind = VoiceFieldKind.URL_ID)
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

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
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
        Modifier.fillMaxWidth().padding(vertical = 8.dp).haloDraftOutline(MaterialTheme.colorScheme.primary),
        shape = com.halo.app.ui.design.HaloShapes.card,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                HaloSignalGlyph(kind = HaloSignalKind.HALO, size = 20.dp)
                Spacer(Modifier.width(8.dp))
                Text(
                    uiText("Draft by halo · not saved"),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.weight(1f),
                )
                if (isGenerating) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
            }
            Spacer(Modifier.height(6.dp))
            if (editing) HaloTextField(content, onEdit, modifier = Modifier.fillMaxWidth().heightIn(max = 220.dp), enabled = !isGenerating, kind = VoiceFieldKind.TEXT)
            else Text(
                content,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.heightIn(max = 220.dp).verticalScroll(rememberScrollState()),
            )
            if (stale) {
                Spacer(Modifier.height(6.dp))
                Text(
                    uiText("Your note changed. Ask halo to write the draft again."),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Spacer(Modifier.height(8.dp))
            // NB02: only one primary action; stale drafts can NEVER be applied.
            // Wrap instead of clipping Apply/Save/Discard at 200% text.
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (stale) com.halo.app.ui.components.HaloActionButton(onClick = onRegenerate, enabled = !isGenerating,
                    modifier = Modifier.heightIn(min = 48.dp)) { Text(uiText("Write it again")) }
                else com.halo.app.ui.components.HaloActionButton(onClick = onApply, enabled = !isGenerating,
                    modifier = Modifier.heightIn(min = 48.dp)) { Text(uiText("Apply to note")) }
                TextButton(onClick = onSaveAsNote, enabled = !isGenerating,
                    modifier = Modifier.heightIn(min = 48.dp)) { Text(uiText("Save as new note")) }
                TextButton(onClick = onDiscard, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(uiText("Discard"))
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (!stale) TextButton(onClick = onRegenerate, enabled = !isGenerating,
                    modifier = Modifier.heightIn(min = 48.dp)) { Text(uiText("Try again")) }
                TextButton(onClick = { editing = !editing }, enabled = !isGenerating,
                    modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(uiText(if (editing) "Done editing" else "Edit draft"))
                }
            }
        }
    }
}

@Composable
private fun FindReplaceBar(state: NoteEditorUiState, viewModel: NoteEditorViewModel, onClose: () -> Unit) {
    var replaceExpanded by rememberSaveable { mutableStateOf(false) }
    Surface(Modifier.fillMaxWidth().padding(vertical = 6.dp),
        shape = com.halo.app.ui.design.HaloShapes.card,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                HaloTextField(
                    value = state.findQuery,
                    onValueChange = viewModel::setFindQuery,
                    singleLine = true,
                    label = { Text(uiText("Find")) },
                    modifier = Modifier.weight(1f),
                    textStyle = MaterialTheme.typography.bodyMedium, kind = VoiceFieldKind.SEARCH)
                IconButton(onClick = onClose, modifier = Modifier.size(48.dp)) {
                    Icon(androidx.compose.ui.res.painterResource(com.halo.app.R.drawable.halo_ic_close),
                        contentDescription = uiText("Close find"))
                }
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("${if (state.matches.isEmpty()) 0 else state.activeMatchIndex.coerceAtLeast(0) + 1} / ${state.matches.size}",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                IconButton(onClick = viewModel::findPrevious, enabled = state.matches.isNotEmpty(),
                    modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Rounded.ArrowUpward, contentDescription = uiText("Previous match"))
                }
                IconButton(onClick = viewModel::findNext, enabled = state.matches.isNotEmpty(),
                    modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Rounded.ArrowDownward, contentDescription = uiText("Next match"))
                }
                TextButton(onClick = { replaceExpanded = !replaceExpanded },
                    modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(uiText("Replace with"))
                }
            }
            if (replaceExpanded) {
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    HaloTextField(
                        value = state.replaceText,
                        onValueChange = viewModel::setReplaceText,
                        singleLine = true,
                        label = { Text(uiText("Replace with")) },
                        modifier = Modifier.widthIn(min = 140.dp), kind = VoiceFieldKind.TEXT)
                    TextButton(onClick = viewModel::replaceActiveMatch,
                        enabled = state.matches.isNotEmpty() && state.isEditable,
                        modifier = Modifier.heightIn(min = 48.dp)) {
                        Text(uiText("Replace"))
                    }
                    TextButton(onClick = viewModel::replaceAllMatches,
                        enabled = state.matches.isNotEmpty() && state.isEditable,
                        modifier = Modifier.heightIn(min = 48.dp)) {
                        Text(uiText("All"))
                    }
                }
            }
        }
    }
}

@Composable
private fun ColumnScope.SourceModeEditor(state: NoteEditorUiState, viewModel: NoteEditorViewModel, onCancel: () -> Unit) {
    Column(Modifier.fillMaxWidth().weight(1f).background(MaterialTheme.colorScheme.background)) {
        Text(
            uiText("Markdown source. Applying converts it back into blocks."),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (state.sourceLossy) {
            Surface(
                Modifier.fillMaxWidth().padding(vertical = 6.dp),
                shape = com.halo.app.ui.design.HaloShapes.control,
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
        HaloVoiceTextActions(text=state.sourceDraft,onText=viewModel::updateSourceDraft,
            maxChars=12000,enabled=state.status!=SaveStatus.LOADING,
            readText=state.sourceDraft)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { viewModel.applySource(force = false) }) { Text(uiText("Apply to note")) }
            TextButton(onClick = { viewModel.applySource(force = true) }) { Text(uiText("Apply anyway")) }
            TextButton(onClick = onCancel) { Text(uiText("Cancel")) }
        }
    }
}

@Composable
private fun ReadModeView(state: NoteEditorUiState, scrollState: androidx.compose.foundation.ScrollState, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background).verticalScroll(scrollState).padding(bottom = 20.dp)) {
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
            shape = com.halo.app.ui.design.HaloShapes.control,
            color = MaterialTheme.colorScheme.surfaceVariant,
        ) {
            Text(
                block.text,
                Modifier.padding(10.dp),
                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = if (com.halo.app.data.workspace.WorkspaceTextFormat.fromLanguage(block.language) ==
                    com.halo.app.data.workspace.WorkspaceTextFormat.TXT) FontFamily.Default else FontFamily.Monospace),
            )
        }
        NoteBlockType.QUOTE -> Row(Modifier.padding(start = indent, top = 4.dp, bottom = 4.dp)) {
            Box(Modifier.width(3.dp).height(38.dp).background(MaterialTheme.colorScheme.primary))
            Spacer(Modifier.width(8.dp))
            Text(renderInline(block.text, MaterialTheme.colorScheme.primary), style = MaterialTheme.typography.bodyMedium, fontStyle = FontStyle.Italic)
        }
        NoteBlockType.REFERENCE -> Surface(
            Modifier.padding(start = indent, top = 4.dp, bottom = 4.dp),
            shape = com.halo.app.ui.design.HaloShapes.control,
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
                tint = if (block.checked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(8.dp))
            Text(renderInline(block.text, MaterialTheme.colorScheme.primary), style = MaterialTheme.typography.bodyMedium)
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
            Text(renderInline(block.text, MaterialTheme.colorScheme.primary), style = MaterialTheme.typography.bodyMedium)
        }
        else -> Text(
            text = renderInline(block.text.ifBlank { " " }, MaterialTheme.colorScheme.primary),
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
    showTools: Boolean,
    enabled: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    canRemove: Boolean,
    focusRequest: EditorFocusRequest?,
    onFocusHandled: () -> Unit,
    onTextChange: (String) -> Unit,
    currentText: () -> String?,
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
        // Lifecycle collection may deliver an older local edit after the next keystroke.
        // Only the current owner value can replace text; undo and AI edits remain authoritative.
        fieldValue = reconcileNoteField(fieldValue, block.text, currentText())
    }
    LaunchedEffect(focusRequest?.token) {
        val request = focusRequest ?: return@LaunchedEffect
        runCatching {
            focusRequester.requestFocus()
            val text = currentText() ?: fieldValue.text
            fieldValue = TextFieldValue(text, TextRange(request.offset.coerceIn(0, text.length)))
        }
        onFocusHandled()
    }

    Surface(
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp * block.indent),
        shape = com.halo.app.ui.design.HaloShapes.control,
        color = when (block.type) {
            NoteBlockType.CODE -> MaterialTheme.colorScheme.surfaceVariant
            NoteBlockType.QUOTE -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.34f)
            else -> MaterialTheme.colorScheme.background
        },
        border = if (showTools || block.type in setOf(NoteBlockType.CODE, NoteBlockType.QUOTE, NoteBlockType.REFERENCE))
            BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.72f)) else null,
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            if (showTools) Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box {
                    TextButton(onClick = { typeMenu = true }, enabled = enabled,
                        modifier = Modifier.heightIn(min = 48.dp)) {
                        Text(uiText(block.type.label))
                    }
                    DropdownMenu(expanded = typeMenu, onDismissRequest = { typeMenu = false }) {
                        NoteBlockType.entries.forEach { type ->
                            DropdownMenuItem(
                                text = { Text(uiText(type.label)) },
                                onClick = {
                                    typeMenu = false
                                    onTypeChange(type)
                                },
                            )
                        }
                    }
                }
                if (block.type.isList) {
                    IconButton(onClick = { onIndent(-1) }, enabled = enabled && block.indent > 0, modifier = Modifier.size(48.dp)) {
                        Text(uiText("⇤"), style = MaterialTheme.typography.labelMedium)
                    }
                    IconButton(
                        onClick = { onIndent(1) },
                        enabled = enabled && block.indent < 3,
                        modifier = Modifier.size(48.dp),
                    ) {
                        Text(uiText("⇥"), style = MaterialTheme.typography.labelMedium)
                    }
                }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onMoveUp, enabled = enabled && canMoveUp, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Rounded.ArrowUpward, contentDescription = uiText("Move block up"), modifier = Modifier.size(17.dp))
                }
                IconButton(onClick = onMoveDown, enabled = enabled && canMoveDown, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Rounded.ArrowDownward, contentDescription = uiText("Move block down"), modifier = Modifier.size(17.dp))
                }
                IconButton(onClick = onDuplicate, enabled = enabled, modifier = Modifier.size(48.dp)) {
                    Icon(androidx.compose.ui.res.painterResource(com.halo.app.R.drawable.halo_ic_add), contentDescription = uiText("Duplicate block"), modifier = Modifier.size(17.dp))
                }
                IconButton(onClick = onRemove, enabled = enabled && canRemove, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Rounded.DeleteOutline, contentDescription = uiText("Delete block"), modifier = Modifier.size(17.dp))
                }
            }
            if (unlinkedReference) {
                Surface(
                    Modifier.fillMaxWidth().padding(bottom = 6.dp),
                    shape = com.halo.app.ui.design.HaloShapes.control,
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
                        if (insertedNewline && block.type != NoteBlockType.CODE) {
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
                            fontFamily = if (com.halo.app.data.workspace.WorkspaceTextFormat.fromLanguage(block.language) ==
                                com.halo.app.data.workspace.WorkspaceTextFormat.TXT) FontFamily.Default else FontFamily.Monospace,
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
                                if (com.halo.app.data.workspace.WorkspaceTextFormat.fromLanguage(block.language) != null)
                                    stringResource(R.string.file52_document_text) else uiText(placeholderFor(block.type)),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        inner()
                    },
                )
            }
            if (showTools) {
                HaloVoiceTextActions(text=block.text,onText=onTextChange,
                    enabled=enabled,readText=block.text,maxChars=12000,
                    appendSeparator=" ")
            }
            if (showTools) Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onAddBelow, enabled = enabled) {
                    Icon(androidx.compose.ui.res.painterResource(com.halo.app.R.drawable.halo_ic_add), contentDescription = null, modifier = Modifier.size(16.dp))
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
private fun AttachmentCard(attachment: NoteAttachment, onOpen: () -> Unit, onRemove: () -> Unit, onEdit: () -> Unit, onStudio: (() -> Unit)?) {
    Surface(
        modifier = Modifier.width(if (attachment.type == "IMAGE") 248.dp else 220.dp),
        shape = com.halo.app.ui.design.HaloShapes.control,
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column {
            if (attachment.type == "IMAGE") {
                AttachmentImage(attachment)
            }
            if (attachment.type == "IMAGE") {
                onStudio?.let { open -> TextButton(onClick = open) { Text(androidx.compose.ui.res.stringResource(com.halo.app.R.string.studio_open_in)) } }
                TextButton(onClick = onEdit) { Text(uiText("Edit with AI")) }
            } else {
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
                    attachment.displayName ?: uiText("Attachment"),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onRemove, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Rounded.DeleteOutline, contentDescription = uiText("Remove attachment"), modifier = Modifier.size(19.dp))
                }
            }
        }
    }
}

@Composable
private fun AttachmentImage(attachment: NoteAttachment) {
    com.halo.app.ui.workspace.WorkspaceAttachmentPreview(attachment,
        Modifier.fillMaxWidth().height(170.dp))
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

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun noteSaveLabel(state: NoteEditorUiState): String = when (state.status) {
        SaveStatus.LOADING -> "Loading…"
        SaveStatus.SAVING -> stringResource(if (state.draftKept) R.string.work_draft_kept else R.string.work_editing)
        SaveStatus.SAVED -> stringResource(R.string.brand_note_saved_on_phone)
        SaveStatus.CONFLICT -> uiText("Conflict")
        SaveStatus.FAILED -> "Could not save"
        SaveStatus.MISSING -> "Unavailable"
        SaveStatus.TRASHED -> "In trash — editing disabled"
    }

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun EditorStatusBar(state: NoteEditorUiState) {
    val label = noteSaveLabel(state)
    val color = when (state.status) {
        SaveStatus.LOADING -> MaterialTheme.colorScheme.onSurfaceVariant
        SaveStatus.SAVING -> MaterialTheme.colorScheme.primary
        SaveStatus.SAVED -> MaterialTheme.colorScheme.primary
        SaveStatus.CONFLICT, SaveStatus.FAILED, SaveStatus.MISSING, SaveStatus.TRASHED ->
            MaterialTheme.colorScheme.error
    }
    FlowRow(
        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (state.status == SaveStatus.SAVED) {
                HaloIcon(HaloGlyph.LOCAL, contentDescription = null,
                    modifier = Modifier.size(18.dp), tint = color)
            } else {
                Box(Modifier.size(8.dp).background(color, RoundedCornerShape(3.dp)))
            }
            Text(
                uiText(label),
                style = MaterialTheme.typography.labelMedium,
                color = color,
                // Announce save-state changes to TalkBack without stealing focus.
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
        Text(
            pluralStringResource(R.plurals.note_word_count, state.wordCount, state.wordCount),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun MissingNoteState(onBack: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Surface(onClick = onBack, shape = com.halo.app.ui.design.HaloShapes.card, color = MaterialTheme.colorScheme.tertiaryContainer) {
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
private fun renderInline(text: String, linkColor: androidx.compose.ui.graphics.Color): AnnotatedString = buildAnnotatedString {
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
                    withStyle(SpanStyle(color = linkColor)) {
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


/** NB02: the six daily editing actions remain above the keyboard. */
@Composable
private fun NoteFocusDock(state: NoteEditorUiState, viewModel: NoteEditorViewModel,
    onAdd: () -> Unit, onHalo: () -> Unit, onExport: (() -> Unit)? = null) {
    Surface(shape = com.halo.app.ui.design.HaloShapes.pill,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp)) {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(6.dp), horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Zone5ActionButton(uiText("Undo"), "action_undo", state.canUndo, onClick = viewModel::undo)
            Zone5ActionButton(uiText("Redo"), "action_redo", state.canRedo, onClick = viewModel::redo)
            onExport?.let { export -> Zone5ActionButton(uiText("Export"), "action_export",
                state.status == SaveStatus.SAVED && !state.isGenerating, onClick = export) }
            val literal = state.blocks.singleOrNull()?.let { com.halo.app.data.workspace.WorkspaceTextFormat.fromLanguage(it.language) } != null
            if (!literal) {
            ToolZoneHeaderAction("page.note.insert") { NoteBlockAddMenu(onBlock = { type ->
                viewModel.insertBlock(state.selection?.blockId ?: state.blocks.lastOrNull()?.id, type)
            }, onAttachment = onAdd) }
            TextButton(onClick = { viewModel.toggleInlineStyle(InlineStyle.BOLD) }, enabled = state.selection != null,
                modifier = Modifier.heightIn(min = 48.dp)) {
                Icon(painterResource(R.drawable.halo_ic_bold), null, Modifier.size(24.dp)); Spacer(Modifier.width(8.dp)); Text(uiText("Bold"))
            }
            Zone5ActionButton(uiText("List"), "office_bullets", state.selection != null) { viewModel.convertSelectionTo(NoteBlockType.BULLET_LIST) }
            }
            HaloVoiceTextActions(text = "", onText = viewModel::addDictatedParagraph,
                enabled = !state.isGenerating, maxChars = 12000,
                fieldContent = { control -> control() })
            Zone5ActionButton(uiText("Ask halo"), "cognition_think", onClick = onHalo)
        }
    }
}

/** Existing document commands own insertion; opening the menu never mutates the note. */
@Composable
internal fun NoteBlockAddMenu(onBlock: (NoteBlockType) -> Unit, onAttachment: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val label = uiText("Add block")
    Box {
        IconButton(onClick = { expanded = true }) {
            Box(Modifier.semantics { contentDescription = label }) { Zone5Icon("action_add", HaloGlyph.NOTE, Modifier.size(24.dp)) }
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            listOf(NoteBlockType.PARAGRAPH, NoteBlockType.HEADING, NoteBlockType.BULLET_LIST,
                NoteBlockType.NUMBERED_LIST, NoteBlockType.CHECKLIST, NoteBlockType.QUOTE,
                NoteBlockType.CODE, NoteBlockType.DIVIDER).forEach { type ->
                DropdownMenuItem(text = { Text(uiText(type.label)) }, onClick = {
                    expanded = false
                    onBlock(type)
                })
            }
            DropdownMenuItem(text = { Text(uiText("Attach")) }, onClick = {
                expanded = false
                onAttachment()
            })
        }
    }
}

/** Ignore storage timestamps; a changed insertion scope must be deliberately reopened. */
private fun noteInsertSignature(state: NoteEditorUiState): Int = state.blocks.map { Triple(it.id, it.type, it.text) }.hashCode()
