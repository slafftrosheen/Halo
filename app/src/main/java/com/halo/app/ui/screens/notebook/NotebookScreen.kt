package com.halo.app.ui.screens.notebook

import com.halo.app.ui.presence.BindShellPageActions
import com.halo.app.ui.presence.ShellPageAction
import com.halo.app.ui.presence.ShellPageOwner

import androidx.compose.runtime.LaunchedEffect

import com.halo.app.ui.components.HaloTextField
import com.halo.app.ui.components.VoiceFieldKind
import com.halo.app.language.uiText
import com.halo.app.ui.components.HaloVoiceTextActions
import androidx.activity.compose.BackHandler
import com.halo.app.R
import androidx.compose.ui.res.stringResource

import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LifecycleResumeEffect

import android.text.format.DateUtils
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.DashboardCustomize
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.NoteAlt
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.halo.app.data.notebook.WorkspaceObject
import com.halo.app.data.notebook.WorkspaceObjectType
import com.halo.app.ui.design.HaloScreenHeader
import com.halo.app.ui.design.HaloSpacing
import com.halo.app.ui.design.HaloEmptyState
import com.halo.app.ui.design.HaloFocusAction
import com.halo.app.ui.design.HaloHeaderAction
import com.halo.app.ui.design.HaloProgressPresentation
import androidx.compose.runtime.saveable.rememberSaveable
import com.halo.app.ui.icons.HaloGlyph

private val primaryFilters = listOf(
    NotebookFilter.ALL,
    NotebookFilter.FILES,
    NotebookFilter.NOTES,
    NotebookFilter.CHATS,
    NotebookFilter.CANVASES,
)
private val secondaryFilters = NotebookFilter.entries.filterNot(primaryFilters::contains)

@Composable
@OptIn(ExperimentalLayoutApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)
fun NotebookScreen(viewModel: NotebookViewModel, onOpenStudy: () -> Unit, onSkills: () -> Unit = {},
    onOpenModels: () -> Unit = {}, requestLocalSearch: Boolean = false, onLocalSearchOpened: () -> Unit = {},
    onOpenStudio: (() -> Unit)? = null, onOpenFileTools: (() -> Unit)? = null, onStartVoiceMode: () -> Unit = {}, librarySession: NotebookLibrarySession? = null, rootRequest: Long = 0L) {
    LifecycleResumeEffect(viewModel) {
        viewModel.refreshContextPolicy()
        onPauseOrDispose {}
    }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val queryText by viewModel.queryText.collectAsStateWithLifecycle()
    val pendingMemoryReviews by viewModel.pendingMemoryReviewCount.collectAsStateWithLifecycle()
    val cloudAllowed by viewModel.cloudSharing.collectAsStateWithLifecycle()
    val selectedIds by viewModel.selectedIds.collectAsStateWithLifecycle()
    val notice by viewModel.notice.collectAsStateWithLifecycle()
    val batchNotice by viewModel.batchNotice.collectAsStateWithLifecycle()
    val mutating by viewModel.isMutating.collectAsStateWithLifecycle()
    val canUseSelection = !mutating && state.isLoaded && !state.loadError
    val trashUndoIds by viewModel.trashUndoIds.collectAsStateWithLifecycle()
    var selecting by rememberSaveable { mutableStateOf(false) }
    val shellHosted = com.halo.app.ui.presence.LocalShellHaloOrb.current
    var workspaceView by rememberSaveable { mutableStateOf("library") }
    val showLibrary = workspaceView == "library"
    val libraryList = androidx.compose.foundation.lazy.rememberLazyListState()
    val toolsList = androidx.compose.foundation.lazy.rememberLazyListState()
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    val selectView: (Boolean) -> Unit = { library ->
        focusManager.clearFocus(); workspaceView = if (library) "library" else "tools"
    }
    var consumedRootRequest by rememberSaveable { mutableStateOf(0L) }
    LaunchedEffect(rootRequest) {
        if (rootRequest != consumedRootRequest) {
            workspaceView = "library"
            consumedRootRequest = rootRequest
        }
    }
    LaunchedEffect(selectedIds) { if (selectedIds.isNotEmpty()) selecting = true }
    var sortMenu by remember { mutableStateOf(false) }
    var batchMenu by remember { mutableStateOf(false) }
    var confirmTrash by remember { mutableStateOf(false) }
    val visibleItems = remember(state.items, state.filter) {
        state.items.filterNot { it.objectRef.id.value.startsWith("learning:") || state.filter == NotebookFilter.MEMORY }
    }
    val collectionChoices = remember(state.collections) { state.collections.map { it.id to it.name } }
    var showWorkingSet by rememberSaveable { mutableStateOf(false) }
    var showNewCollection by rememberSaveable { mutableStateOf(false) }
    var showDeleteCollection by remember { mutableStateOf(false) }
    var collectionName by rememberSaveable { mutableStateOf("") }
    var showInboxCapture by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    var importFile by rememberSaveable { mutableStateOf(false) }
    var showLocalSearch by rememberSaveable { mutableStateOf(false) }
    BindNotebookLibrary(librarySession, viewModel, libraryVisible = showLibrary && !sortMenu && !batchMenu &&
        !confirmTrash && !showWorkingSet && !showNewCollection && !showDeleteCollection && !showInboxCapture && !showLocalSearch)
    val libraryActions = when {
        !showLibrary -> emptyList()
        selecting -> listOf(
            ShellPageAction("page.notebook.select-visible", "Select visible", HaloGlyph.NOTE,
                canUseSelection && visibleItems.isNotEmpty()) { viewModel.selectVisible() },
            ShellPageAction("page.notebook.selection-actions", "Actions", HaloGlyph.TOOL,
                canUseSelection && selectedIds.isNotEmpty()) { batchMenu = true },
            ShellPageAction("page.notebook.selection-done", "Done", HaloGlyph.SUCCESS) { selecting = false; viewModel.clearSelection() },
        )
        else -> listOf(
            ShellPageAction("page.notebook.new-note", "New note", HaloGlyph.NOTE, !mutating) { viewModel.createNote() },
            ShellPageAction("page.notebook.import", "Import", HaloGlyph.TOOL, !mutating) { importFile = true; showInboxCapture = true },
            ShellPageAction("page.notebook.organize", stringResource(R.string.zone5_organize), HaloGlyph.TOOL,
                canUseSelection) { selecting = true },
            ShellPageAction("page.notebook.capture", "Quick capture", HaloGlyph.NOTE, !mutating) { showInboxCapture = true },
            ShellPageAction("page.notebook.related", "Find related notes", HaloGlyph.SEARCH) { showLocalSearch = true },
            ShellPageAction("page.notebook.context", "In chat", HaloGlyph.NOTE) { showWorkingSet = true },
            ShellPageAction("page.notebook.chats", "Saved chats", HaloGlyph.NOTE) { viewModel.setFilter(NotebookFilter.CHATS) },
            ShellPageAction("page.notebook.inbox", "Inbox", HaloGlyph.NOTE) { viewModel.setFilter(NotebookFilter.INBOX) },
        ) + listOfNotNull(
            state.duePractice.firstOrNull()?.let { due -> ShellPageAction("page.notebook.review", "Review cards", HaloGlyph.QUESTION) { viewModel.openStudy(due.sessionId, review = true) } },
            state.studySessions.firstOrNull()?.let { session -> ShellPageAction("page.notebook.continue", "Continue learning", HaloGlyph.QUESTION) { viewModel.openStudy(session.id) } },
            if (pendingMemoryReviews > 0) ShellPageAction("page.notebook.memory-review", "Memory suggestions", HaloGlyph.MEMORY) { viewModel.openMemoryHub("Suggestions") } else null,
        )
    }
    BindShellPageActions(ShellPageOwner("notebook", sourceIdentity = "${state.filter}:${state.selectedCollectionId}:$queryText:${state.resolvedQuery}:${state.sort}:$workspaceView:$selecting:${selectedIds.sorted()}",
        selectionIds = if (showLibrary) selectedIds.sorted() else emptyList(), activeToolId = workspaceView,
        canonicalToolIds = if (!showLibrary) listOf("halo_list_screens", "halo_open_screen") else listOf("halo_library_view", "halo_library_set_view", "halo_library_organization", "halo_library_organize",
            "halo_library_batch", "halo_library_apply_batch", "halo_note_get", "halo_note_purge_status",
            "halo_note_purge", "halo_note_purge_retry"),
        disabledActionReasons = mapOf(
            "page.notebook.new-note" to uiText("Updating..."), "page.notebook.import" to uiText("Updating..."),
            "page.notebook.organize" to if (mutating) uiText("Updating...") else stringResource(R.string.zone5_refresh_first),
            "page.notebook.select-visible" to uiText(if (mutating) "Updating..." else "Nothing in this view"),
            "page.notebook.selection-actions" to stringResource(if (state.loadError) R.string.zone5_refresh_first else R.string.zone5_select_first))), libraryActions)
    BackHandler(enabled = !showLibrary) { workspaceView = "library" }
    LaunchedEffect(requestLocalSearch) {
        if (requestLocalSearch) { workspaceView = "library"; showLocalSearch = true; onLocalSearchOpened() }
    }
    if (!showLibrary) {
        com.halo.app.ui.presence.BindShellUtilityBand(ShellPageOwner("notebook", activeToolId = "tools")) {
            Text(stringResource(R.string.zone5_tools_controls), style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp))
        }
        LazyColumn(state = toolsList, modifier = Modifier.fillMaxSize()
            .background(com.halo.app.ui.theme.haloWorkspaceBackground()).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)) {
            item("workspace-heading") { WorkspaceHeadingAndTabs(false, selectView) }
            item("workspace-tools") { WorkspaceToolsList(onOpenStudy, { viewModel.openMemoryHub() }, onOpenStudio, onOpenFileTools) }
            item("workspace-bottom") { Spacer(Modifier.height(12.dp)) }
        }
        return
    }
    if (showInboxCapture) InboxCaptureDialog(initialImport = importFile) { showInboxCapture = false; importFile = false }
    if (showLocalSearch) NotebookLocalSearchSheet(
        viewModel = viewModel, items = state.items, studySessions = state.studySessions,
        onOpenModels = onOpenModels, onDismiss = { showLocalSearch = false },
    )
    if (batchMenu) com.halo.app.ui.presence.LocalizedMaterialBottomSheet(
        onDismissRequest = { batchMenu = false },
        sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(uiText("%1\$d selected", selectedIds.size), style = MaterialTheme.typography.titleLarge)
            if (!canUseSelection) Text(if (mutating) uiText("Updating...") else stringResource(R.string.zone5_refresh_first),
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            @Composable fun batchAction(label: String, invoke: () -> Unit) {
                TextButton(onClick = { batchMenu = false; invoke() }, enabled = canUseSelection && selectedIds.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(uiText(label)) }
            }
            if (state.filter !in listOf(NotebookFilter.TRASH, NotebookFilter.ARCHIVED)) {
                batchAction("Add to Halo context") { viewModel.applyToSelection(NotebookBatchAction.CONTEXT) }
                batchAction("Archive") { viewModel.applyToSelection(NotebookBatchAction.ARCHIVE) }
            } else batchAction("Restore") { viewModel.applyToSelection(NotebookBatchAction.RESTORE) }
            if (state.filter != NotebookFilter.TRASH) batchAction("Move to trash") { confirmTrash = true }
            state.collections.forEach { collection ->
                TextButton(onClick = { batchMenu = false; viewModel.applyToSelection(NotebookBatchAction.COLLECTION, collection.id) },
                    enabled = canUseSelection && selectedIds.isNotEmpty(), modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Text(uiText("Add to %1\$s", collection.name))
                }
            }
            TextButton(onClick = { batchMenu = false }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(uiText("Cancel")) }
        }
    }

    if (confirmTrash) AlertDialog(onDismissRequest = { confirmTrash = false },
        title = { Text(uiText("Move %1\$d items to trash?", selectedIds.size)) }, text = { Text(uiText("You can restore them from the Trash filter.")) },
        confirmButton = { TextButton(onClick = { confirmTrash = false; viewModel.applyToSelection(NotebookBatchAction.TRASH) }) { Text(uiText("Move to trash")) } },
        dismissButton = { TextButton(onClick = { confirmTrash = false }) { Text(uiText("Cancel")) } })

    if (showWorkingSet) {
        WorkingSetSheet(
            items = state.workingSetItems,
            onRemove = { viewModel.selectContext(it, false) },
            onClearAll = viewModel::clearContext,
            onDismiss = { showWorkingSet = false },
            estimatedTokens = state.workingSetTokens,
            cloudAllowed = cloudAllowed,
            remoteAllowed = viewModel::remoteAllowed,
            onOpen = { showWorkingSet = false; viewModel.openObject(it) },
        )
    }
    if (showNewCollection) {
        AlertDialog(
            onDismissRequest = { showNewCollection = false },
            title = { Text(uiText("New collection")) },
            text = {
                HaloTextField(
                    value = collectionName,
                    onValueChange = { collectionName = it.take(60) },
                    singleLine = true,
                    label = { Text(uiText("Collection name")) }, kind = VoiceFieldKind.SHORT, maxChars = 60)
            },
            confirmButton = {
                TextButton(
                    enabled = collectionName.isNotBlank() && !mutating,
                    onClick = {
                        viewModel.createCollection(collectionName)
                        collectionName = ""
                        showNewCollection = false
                    },
                ) { Text(uiText("Create")) }
            },
            dismissButton = { TextButton(onClick = { showNewCollection = false }) { Text(uiText("Cancel")) } },
        )
    }
    if (showDeleteCollection) {
        val selectedName = state.collections.firstOrNull { it.id == state.selectedCollectionId }?.name
        AlertDialog(
            onDismissRequest = { showDeleteCollection = false },
            title = { Text(uiText("Delete collection?")) },
            text = { Text(uiText("%1\$s will be removed. Its items stay in Workspace.", selectedName ?: uiText("This collection"))) },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteCollection = false
                    viewModel.deleteSelectedCollection()
                }) { Text(uiText("Delete")) }
            },
            dismissButton = { TextButton(onClick = { showDeleteCollection = false }) { Text(uiText("Cancel")) } },
        )
    }

    LazyColumn(
        state = libraryList,
        modifier = Modifier
            .fillMaxSize()
            .background(com.halo.app.ui.theme.haloWorkspaceBackground())
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "workspace-heading") { WorkspaceHeadingAndTabs(true, selectView) }
        if (!shellHosted) item(key = "standalone-actions") {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                libraryActions.forEach { action -> TextButton(onClick = action.onInvoke, enabled = action.enabled) { Text(uiText(action.title)) } }
            }
        }
        if (showLibrary) item(key = "library-controls") {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = com.halo.app.ui.design.HaloShapes.card,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            ) {
                Column(
                    Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    HaloTextField(
                        value = queryText,
                        onValueChange = viewModel::setQuery,
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        leadingIcon = { Icon(painterResource(R.drawable.halo_ic_search), contentDescription = null) },
                        placeholder = { Text(uiText("Search your workspace"), maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis) },
                        inputContentDescription = uiText("Search your workspace"),
                        shape = com.halo.app.ui.design.HaloShapes.control, kind = VoiceFieldKind.SEARCH)

                    FilterBar(state.filter, viewModel::setFilter)
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    CollectionBar(
                        state = state,
                        onSelect = viewModel::selectCollection,
                        onCreate = { showNewCollection = true },
                        onDelete = { showDeleteCollection = true },
                    )
                }
            }
        }

        if (trashUndoIds.isNotEmpty()) item(key = "trash_undo") {
            Surface(modifier = Modifier.fillMaxWidth(),
                shape = com.halo.app.ui.design.HaloShapes.card,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
                FlowRow(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(uiText("%1\$d items moved to trash", trashUndoIds.size),
                        style = MaterialTheme.typography.bodyMedium)
                    TextButton(onClick = viewModel::undoLastTrash, enabled = !mutating,
                        modifier = Modifier.heightIn(min = 48.dp)) {
                        Text(uiText("Undo"))
                    }
                    TextButton(onClick = viewModel::dismissTrashUndo, enabled = !mutating,
                        modifier = Modifier.heightIn(min = 48.dp)) {
                        Text(uiText("Dismiss"))
                    }
                }
            }
        }

        if (showLibrary) item(key = "organize") {
            // NB01: the sort/select row must wrap when labels grow (200% font).
            FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Box {
                    TextButton(onClick = { sortMenu = true },
                        modifier = Modifier.heightIn(min = 48.dp)) { Text(uiText(state.sort.label)) }
                    DropdownMenu(sortMenu, { sortMenu = false }) {
                        NotebookSort.entries.forEach { sort -> DropdownMenuItem(
                            text = { Text(uiText(sort.label)) },
                            onClick = { viewModel.setSort(sort); sortMenu = false }) }
                    }
                }
                TextButton(onClick = { selecting = !selecting; viewModel.clearSelection() }, enabled = selecting || canUseSelection,
                    modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(if (selecting) uiText("Done") else stringResource(R.string.zone5_organize))
                }
            }
            if (selecting) FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.Center
            ) {
                Text(uiText("%1\$d selected", selectedIds.size), style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = viewModel::selectVisible, enabled = canUseSelection && visibleItems.isNotEmpty(),
                    modifier = Modifier.heightIn(min = 48.dp)) { Text(uiText("Select visible")) }
                Box {
                    TextButton(onClick = { batchMenu = true }, enabled = selectedIds.isNotEmpty() && canUseSelection) { Text(uiText(if (mutating) "Updating..." else "Actions")) }
                }
            }
            if (batchNotice != null || notice != null) Row(verticalAlignment = Alignment.CenterVertically) {
                Text(batchNotice?.let { notebookBatchNotice(it) } ?: notebookNoticeText(notice.orEmpty()),
                    Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = viewModel::clearNotice) { Text(uiText("Dismiss")) }
            }
        }

        if(showLibrary && state.filter in listOf(NotebookFilter.MEMORY,NotebookFilter.INBOX)) item(key="personal-memory") {
            PersonalMemorySection(viewModel,inboxOnly=state.filter == NotebookFilter.INBOX)
        }
        if (showLibrary) item(key = "library-title") {
            val location = state.collections.firstOrNull { it.id == state.selectedCollectionId }?.name ?: uiText(state.filter.label)
            SectionTitle(location, if (state.isLoaded) uiText("Items: %1\$d", visibleItems.size) else "")
        }

        if (state.loadError) item(key = "library-error") {
            Surface(color = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.onErrorContainer,
                shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.zone5_library_unavailable), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(if (state.isLoaded) R.string.zone5_library_stale else R.string.zone5_library_error),
                        style = MaterialTheme.typography.bodyMedium)
                    TextButton(onClick = viewModel::retryLibraryLoad, enabled = !mutating,
                        modifier = Modifier.heightIn(min = 48.dp)) { Text(uiText("Try again")) }
                }
            }
        }

        if (!state.isLoaded && !state.loadError) {
            item(key = "loading") {
                HaloProgressPresentation(uiText("Loading your library…"))
            }
        } else if (!state.loadError && visibleItems.isEmpty() && state.filter !in listOf(NotebookFilter.MEMORY, NotebookFilter.INBOX)) {
            item(key = "empty") {
                NotebookEmptyGuidance(state.query, state.filter, state.selectedCollectionId != null, !mutating,
                    viewModel::createNote, viewModel::createCanvas) {
                    viewModel.setQuery(""); viewModel.setFilter(NotebookFilter.ALL); viewModel.selectCollection(null)
                }
                if (notebookEmptyAction(state.query, state.filter, state.selectedCollectionId != null) == NotebookEmptyAction.CREATE_NOTE) {
                    TextButton(onClick = { importFile = true; showInboxCapture = true }, enabled = !mutating,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                        com.halo.app.ui.zone5.Zone5Icon("action_import", HaloGlyph.FILE, Modifier.size(24.dp))
                        Spacer(Modifier.width(8.dp)); Text(uiText("Import"))
                    }
                }
            }
        } else if (showLibrary) {
            items(notebookItemsForSurface(visibleItems, showLibrary),
                key = { it.objectRef.id.value }) { item ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                if (selecting) androidx.compose.material3.Checkbox(checked = item.objectRef.id.value in selectedIds,
                    enabled = canUseSelection, onCheckedChange = { viewModel.toggleSelection(item.objectRef.id.value) })
                WorkspaceObjectRow(
                    item = item,
                    selected = item.objectRef.id.value in state.workingSet,
                    favorite = item.objectRef.id.value in state.favorites,
                    collections = collectionChoices,
                    inSelectedCollection = item.objectRef.id.value in state.selectedCollectionMembers,
                    onOpen = { if (selecting) { if (canUseSelection) viewModel.toggleSelection(item.objectRef.id.value) } else viewModel.openSearchItem(item) },
                    onStudy = { viewModel.startStudy(item.objectRef.id.value) },
                    onToggleFavorite = { viewModel.toggleFavorite(item.objectRef.id.value) },
                    onAddToCollection = { viewModel.addToCollection(item.objectRef.id.value, it) },
                    onRemoveFromSelectedCollection = { viewModel.removeFromSelectedCollection(item.objectRef.id.value) },
                    onToggleContext = {
                        viewModel.selectContext(item.objectRef.id.value, item.objectRef.id.value !in state.workingSet)
                    },
                    onMultiSelect = if (canUseSelection) ({
                        if (!selecting) { selecting = true; viewModel.clearSelection() }
                        viewModel.toggleSelection(item.objectRef.id.value)
                    }) else null,
                    onRestore = if (item.objectRef.lifecycle ==
                        com.halo.app.data.notebook.WorkspaceObjectLifecycle.TRASHED && !mutating)
                        ({ viewModel.restore(item.objectRef.id.value) }) else null,
                    selectedCollectionName = state.collections.firstOrNull {
                        it.id == state.selectedCollectionId
                    }?.name,
                )
                }
            }
        }
        item(key = "bottom-space") { Spacer(Modifier.height(12.dp)) }
    }
}

/** Show one recent workspace item when Study continuation is present.
 * A second recent item is useful only when there is no learning session.
 * The full library remains unchanged.
 */
internal fun notebookRecentContinuationCount(hasStudySession: Boolean): Int =
    if (hasStudySession) 1 else 2

/** Study source admission remains authoritative in StudyUseCases.start().
 * Only active, supported Workspace *classes* offer a shortcut; a corrupt or
 * inaccessible PDF must still produce the backend's actual Unavailable reason.
 */
internal fun notebookStudySourceAction(
    type: WorkspaceObjectType,
    lifecycle: com.halo.app.data.notebook.WorkspaceObjectLifecycle,
): String? {
    if (lifecycle != com.halo.app.data.notebook.WorkspaceObjectLifecycle.ACTIVE) return null
    return when (type) {
        WorkspaceObjectType.NOTE -> "Study this note"
        WorkspaceObjectType.FILE, WorkspaceObjectType.MEDIA -> "Study this material"
        else -> null
    }
}

/** The hub previews the latest item; the full Library reuses the exact same source list. */
internal fun <T> notebookItemsForSurface(items: List<T>, library: Boolean): List<T> =
    if (library) items else items.take(1)

@Composable
private fun SectionTitle(title: String, trailing: String) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Text(trailing, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun WorkspaceToolCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    emphasized: Boolean = false,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.heightIn(min = 112.dp),
        shape = com.halo.app.ui.design.HaloShapes.card,
        color = if (emphasized) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 0.dp,
        border = BorderStroke(
            1.dp,
            if (emphasized) MaterialTheme.colorScheme.primary.copy(alpha = 0.30f)
            else MaterialTheme.colorScheme.outlineVariant,
        ),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Surface(
                shape = com.halo.app.ui.design.HaloShapes.control,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(40.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(22.dp))
                }
            }
            Column {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun FilterBar(selected: NotebookFilter, onSelect: (NotebookFilter) -> Unit) {
    var moreExpanded by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        primaryFilters.forEach { filter ->
            FilterChip(selected == filter, { onSelect(filter) }, label = { Text(uiText(filter.label)) })
        }
        Box {
            FilterChip(
                selected = selected in secondaryFilters,
                onClick = { moreExpanded = true },
                label = { Text(uiText(if (selected in secondaryFilters) selected.label else "More")) },
            )
            DropdownMenu(expanded = moreExpanded, onDismissRequest = { moreExpanded = false }) {
                secondaryFilters.forEach { filter ->
                    DropdownMenuItem(
                        text = { Text(uiText(filter.label)) },
                        onClick = { moreExpanded = false; onSelect(filter) },
                    )
                }
            }
        }
    }
}

@Composable
private fun CollectionBar(
    state: NotebookUiState,
    onSelect: (String?) -> Unit,
    onCreate: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FilterChip(
            selected = state.selectedCollectionId == null,
            onClick = { onSelect(null) },
            leadingIcon = { Icon(painterResource(R.drawable.halo_ic_inbox), null, Modifier.size(18.dp)) },
            label = { Text(uiText("Library")) },
        )
        state.collections.forEach { collection ->
            FilterChip(
                selected = state.selectedCollectionId == collection.id,
                onClick = { onSelect(collection.id) },
                leadingIcon = { Icon(painterResource(R.drawable.halo_ic_collections), null, Modifier.size(18.dp)) },
                label = { Text(collection.name) },
            )
        }
        TextButton(onClick = onCreate) { Text(uiText("New collection")) }
        if (state.selectedCollectionId != null) {
            IconButton(onClick = onDelete) { Icon(painterResource(R.drawable.halo_ic_trash), uiText("Delete selected collection")) }
        }
    }
}

internal enum class NotebookEmptyAction { CREATE_NOTE, CREATE_CANVAS, SHOW_LIBRARY }

internal fun notebookEmptyAction(query: String, filter: NotebookFilter, inCollection: Boolean): NotebookEmptyAction = when {
    query.isNotBlank() || inCollection -> NotebookEmptyAction.SHOW_LIBRARY
    filter == NotebookFilter.CANVASES -> NotebookEmptyAction.CREATE_CANVAS
    filter in listOf(NotebookFilter.ALL, NotebookFilter.NOTES) -> NotebookEmptyAction.CREATE_NOTE
    else -> NotebookEmptyAction.SHOW_LIBRARY
}

@Composable
internal fun NotebookEmptyGuidance(query: String, filter: NotebookFilter, inCollection: Boolean,
    enabled: Boolean, onCreateNote: () -> Unit, onCreateCanvas: () -> Unit, onShowLibrary: () -> Unit) {
    val action = notebookEmptyAction(query, filter, inCollection)
    val title = when {
        query.isNotBlank() -> "No matches"
        inCollection -> "This collection is empty"
        filter == NotebookFilter.CANVASES -> "Your first canvas"
        filter == NotebookFilter.TRASH -> "Nothing in trash"
        filter == NotebookFilter.ALL || filter == NotebookFilter.NOTES -> "Make room for your ideas"
        else -> "Nothing in this view"
    }
    val message = when (action) {
        NotebookEmptyAction.CREATE_NOTE -> "Create a note, import a file, or capture something for later. Find it here when you return."
        NotebookEmptyAction.CREATE_CANVAS -> "Create a board, then add text, media or links to your existing notes."
        NotebookEmptyAction.SHOW_LIBRARY -> "Choose the whole library to see your items. Filters do not delete anything."
    }
    val label = when (action) {
        NotebookEmptyAction.CREATE_NOTE -> "Create note"
        NotebookEmptyAction.CREATE_CANVAS -> "New canvas"
        NotebookEmptyAction.SHOW_LIBRARY -> "Show whole library"
    }
    val onClick = when (action) {
        NotebookEmptyAction.CREATE_NOTE -> onCreateNote
        NotebookEmptyAction.CREATE_CANVAS -> onCreateCanvas
        NotebookEmptyAction.SHOW_LIBRARY -> onShowLibrary
    }
    HaloEmptyState(uiText(title), uiText(message), HaloGlyph.NOTE,
        action = HaloHeaderAction(uiText(label), onClick), enabled = enabled)
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
internal fun WorkspaceObjectRow(
    item: NotebookWorkspaceItem,
    selected: Boolean,
    favorite: Boolean,
    collections: List<Pair<String, String>>,
    inSelectedCollection: Boolean,
    onOpen: () -> Unit,
    onStudy: () -> Unit,
    onToggleFavorite: () -> Unit,
    onAddToCollection: (String) -> Unit,
    onRemoveFromSelectedCollection: () -> Unit,
    onToggleContext: () -> Unit,
    onMultiSelect: (() -> Unit)? = null,
    selectedCollectionName: String? = null,
    onRestore: (() -> Unit)? = null,
) {
    val row = item.objectRef
    var menuExpanded by remember(row.id.value) { mutableStateOf(false) }
    Surface(
        modifier = Modifier.fillMaxWidth().combinedClickable(
            role = androidx.compose.ui.semantics.Role.Button,
            onClick = onOpen,
            onLongClick = onMultiSelect,
            onLongClickLabel = if (onMultiSelect != null) uiText("Select") else null,
        ),
        color = MaterialTheme.colorScheme.surface,
        shape = com.halo.app.ui.design.HaloShapes.control,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.72f)),
    ) {
        Row(
            Modifier.padding(start = 12.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(shape = com.halo.app.ui.design.HaloShapes.control, color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.size(38.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(androidx.compose.ui.res.painterResource(workspaceIconResource(row)), null, Modifier.size(23.dp), MaterialTheme.colorScheme.onSecondaryContainer)
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(workspaceDisplayTitle(row.title, item.snippet, uiText(workspaceTypeLabel(row))), style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                val typeLabel = uiText(workspaceTypeLabel(row))
                val dateLabel = java.text.DateFormat.getDateTimeInstance(
                    java.text.DateFormat.MEDIUM, java.text.DateFormat.SHORT,
                    androidx.compose.ui.platform.LocalConfiguration.current.locales[0]
                ).format(java.util.Date(row.updatedAt))
                val collectionLabel = selectedCollectionName?.takeIf { inSelectedCollection }
                    ?: if (row.inInbox) uiText("Inbox") else null
                Text(listOfNotNull(typeLabel, collectionLabel, dateLabel).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1,
                    overflow = TextOverflow.Ellipsis)
                item.snippet?.trim()?.takeIf {
                    it.isNotBlank() && it != row.title
                }?.let { excerpt ->
                    Text(excerpt, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1,
                        overflow = TextOverflow.Ellipsis)
                }
                if (selected) MetadataLabel("In chat", emphasized = true)
            }
            IconButton(onClick = onToggleFavorite, modifier = Modifier.size(48.dp)) {
                val isDark = MaterialTheme.colorScheme.background.luminance() < .5f
                Icon(
                    if (favorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                    contentDescription = uiText(if (favorite) "Remove favorite" else "Add favorite"),
                    tint = if (favorite) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Box {
                IconButton(onClick = { menuExpanded = true }, modifier = Modifier.size(48.dp)) {
                    Icon(painterResource(R.drawable.halo_ic_more),
                        uiText("Organize %1\$s", row.title.ifBlank { uiText("item") }))
                }
                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                    onRestore?.let { restore ->
                        DropdownMenuItem(
                            text = { Text(uiText("Restore")) },
                            onClick = { menuExpanded = false; restore() }
                        )
                    }
                    notebookStudySourceAction(row.type, row.lifecycle)?.let { label ->
                        DropdownMenuItem(
                            leadingIcon = { Icon(painterResource(R.drawable.halo_ic_study), null) },
                            text = { Text(uiText(label)) },
                            onClick = { menuExpanded = false; onStudy() },
                        )
                    }

                    if (row.lifecycle == com.halo.app.data.notebook.WorkspaceObjectLifecycle.ACTIVE) DropdownMenuItem(
                        leadingIcon = { Icon(painterResource(R.drawable.halo_ic_memory), null) },
                        text = { Text(uiText(if (selected) "Remove from Halo context" else "Add to Halo context")) },
                        onClick = { menuExpanded = false; onToggleContext() },
                    )
                    if (inSelectedCollection) {
                        DropdownMenuItem(
                            text = { Text(uiText("Remove from this collection")) },
                            onClick = { menuExpanded = false; onRemoveFromSelectedCollection() },
                        )
                    }
                    collections.forEach { (id, name) ->
                        DropdownMenuItem(
                            leadingIcon = { Icon(painterResource(R.drawable.halo_ic_collections), null) },
                            text = { Text(uiText("Add to %1\$s", name)) },
                            onClick = { menuExpanded = false; onAddToCollection(id) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MetadataLabel(text: String, emphasized: Boolean = false) {
    if (emphasized) {
        Surface(shape = com.halo.app.ui.design.HaloShapes.control,
            color = MaterialTheme.colorScheme.primaryContainer) {
            Text(uiText(text), Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer)
        }
    } else {
        Text(uiText(text), style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

internal fun workspaceTypeLabel(row: WorkspaceObject): String = when (row.type) {
    WorkspaceObjectType.NOTE -> "Note"
    WorkspaceObjectType.CANVAS -> "Canvas"
    WorkspaceObjectType.FILE -> "File"
    WorkspaceObjectType.MEDIA -> "Media"
    WorkspaceObjectType.CONVERSATION -> "Saved chat"
    WorkspaceObjectType.MEMORY -> "Memory"
    WorkspaceObjectType.ARTIFACT -> if (row.contentRef.startsWith("studio:")) "Studio" else if (row.contentRef.startsWith("canvas:")) "Canvas" else "Artifact"
}

/** Display only. A memory suggestion is never an approved personal fact.
 * The same MemoryHub Suggestions page owns review, editing and acceptance.
 */
@Composable
internal fun NotebookPendingMemoryReview(count: Int, onReview: () -> Unit) {
    if (count <= 0) return
    val scheme = MaterialTheme.colorScheme
    Surface(
        onClick = onReview,
        modifier = Modifier.fillMaxWidth().heightIn(min = 76.dp),
        shape = com.halo.app.ui.design.HaloShapes.card,
        color = scheme.surfaceContainerLow,
        border = BorderStroke(1.dp, scheme.outlineVariant),
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(42.dp)
                    .background(scheme.primaryContainer.copy(alpha = .3f), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(painterResource(R.drawable.halo_ic_memory), contentDescription = null,
                    modifier = Modifier.size(24.dp), tint = scheme.primary)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(uiText("%1\$d things halo could remember", count), style = MaterialTheme.typography.titleSmall)
                Text(uiText("Only if you say yes"), style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant)
            }
            Text(uiText("Review"), style = MaterialTheme.typography.labelLarge,
                color = scheme.primary)
        }
    }
}

/** Brand Book extension: Study remains an actual, always-visible Notebook
 * workflow. The four Round 2 creation actions remain unchanged.
 */
@Composable
internal fun NotebookLearningTools(onStudy: () -> Unit, onMemory: () -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(uiText("Tools"), style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            WorkspaceTool("Study", "Explore a topic, continue a lesson or review",
                R.drawable.halo_ic_study, onStudy, actionLabel = "Open Study",
                onAction = onStudy, modifier = Modifier.weight(1f))
            WorkspaceTool("Memory", "What Halo remembers and learns",
                R.drawable.halo_ic_memory, onMemory, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun WorkspaceTool(
    title: String,
    description: String,
    icon: Int,
    onOpen: () -> Unit,
    actionLabel: String? = null,
    onAction: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onOpen,
        modifier = modifier.heightIn(min = 72.dp),
        shape = com.halo.app.ui.design.HaloShapes.card,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(painterResource(icon), contentDescription = null,
                    modifier = Modifier.size(22.dp), tint = MaterialTheme.colorScheme.primary)
                Text(uiText(title), modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
            }
            // Kept as a visible, localizable action label for Study; clicking
            // anywhere on the tile opens the same real learning flow.
            Text(uiText(actionLabel ?: description), style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
internal fun NotebookCreateAction(
    label: String,
    icon: Int,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    emphasized: Boolean = false,
) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = 84.dp),
        shape = com.halo.app.ui.design.HaloShapes.card,
        color = if (emphasized) scheme.primaryContainer.copy(alpha = 0.25f)
            else scheme.surfaceContainerLow,
        border = BorderStroke(1.dp, if (emphasized) scheme.primary
            else scheme.outlineVariant),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(painterResource(icon), contentDescription = null,
                modifier = Modifier.size(24.dp), tint = scheme.primary)
            Text(uiText(label), style = MaterialTheme.typography.labelLarge,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        }
    }
}

/** Display only: never rewrites the stored title or the user's source text. */
internal fun workspaceDisplayTitle(title: String, snippet: String?, fallback: String): String {
    val generated = title.isBlank() || title.startsWith("memo_") || title in setOf("Untitled", "Untitled note", "Untitled canvas")
    if (!generated) return title
    return snippet?.lineSequence()?.map { it.trim().trimStart('#', '*', '-', ' ') }
        ?.firstOrNull { it.isNotBlank() }?.take(80) ?: fallback
}

private fun workspaceIconResource(row: WorkspaceObject): Int = when (row.type) {
    WorkspaceObjectType.NOTE -> R.drawable.ic_halo_type_note
    WorkspaceObjectType.CANVAS -> R.drawable.ic_halo_type_canvas
    WorkspaceObjectType.CONVERSATION -> R.drawable.ic_halo_library_saved_chat
    WorkspaceObjectType.MEMORY -> R.drawable.ic_halo_tool_memory
    WorkspaceObjectType.FILE -> R.drawable.ic_halo_type_file
    WorkspaceObjectType.MEDIA -> R.drawable.ic_halo_type_file
    WorkspaceObjectType.ARTIFACT -> if (row.contentRef.startsWith("studio:")) R.drawable.ic_halo_tool_studio else if (row.contentRef.startsWith("canvas:")) R.drawable.ic_halo_type_canvas else R.drawable.ic_halo_type_file
}
