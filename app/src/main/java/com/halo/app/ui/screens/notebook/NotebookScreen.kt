package com.halo.app.ui.screens.notebook

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
@OptIn(ExperimentalLayoutApi::class)
fun NotebookScreen(viewModel: NotebookViewModel, onOpenStudy: () -> Unit, onSkills: () -> Unit = {},
    onOpenModels: () -> Unit = {}, requestLocalSearch: Boolean = false, onLocalSearchOpened: () -> Unit = {}) {
    LifecycleResumeEffect(viewModel) {
        viewModel.refreshContextPolicy()
        onPauseOrDispose {}
    }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val pendingMemoryReviews by viewModel.pendingMemoryReviewCount.collectAsStateWithLifecycle()
    val cloudAllowed by viewModel.cloudSharing.collectAsStateWithLifecycle()
    val selectedIds by viewModel.selectedIds.collectAsStateWithLifecycle()
    val notice by viewModel.notice.collectAsStateWithLifecycle()
    val mutating by viewModel.isMutating.collectAsStateWithLifecycle()
    val trashUndoIds by viewModel.trashUndoIds.collectAsStateWithLifecycle()
    var selecting by rememberSaveable { mutableStateOf(false) }
    var libraryFocused by rememberSaveable { mutableStateOf(false) }
    val showLibrary = libraryFocused || state.query.isNotBlank() ||
        state.filter != NotebookFilter.ALL || state.selectedCollectionId != null
    fun leaveLibrary() {
        libraryFocused = false
        selecting = false
        viewModel.clearSelection()
        viewModel.setQuery("")
        viewModel.setFilter(NotebookFilter.ALL)
        viewModel.selectCollection(null)
    }
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
    LaunchedEffect(requestLocalSearch) {
        if (requestLocalSearch) { showLocalSearch = true; onLocalSearchOpened() }
    }
    BackHandler(enabled = showLibrary && !sortMenu && !batchMenu &&
        !confirmTrash && !showWorkingSet && !showNewCollection &&
        !showDeleteCollection && !showInboxCapture) { leaveLibrary() }
    if (showInboxCapture) InboxCaptureDialog(initialImport = importFile) { showInboxCapture = false; importFile = false }
    if (showLocalSearch) NotebookLocalSearchSheet(
        viewModel = viewModel, items = state.items, studySessions = state.studySessions,
        onOpenModels = onOpenModels, onDismiss = { showLocalSearch = false },
    )

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
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "header") {
            HaloScreenHeader(
                title = uiText(if (showLibrary) "Library" else "Notebook"),
                subtitle = if (showLibrary) uiText("Items: %1\$d", visibleItems.size) else null,
                navigation = if (showLibrary) ::leaveLibrary else null,
                // The Round 2 Notebook home has a word-only header.
                glyph = if (showLibrary) HaloGlyph.NOTE else null,
                trailing = {
                    if (!showLibrary) {
                        IconButton(onClick = { libraryFocused = true },
                            modifier = Modifier.size(48.dp)) {
                            Icon(painterResource(R.drawable.halo_ic_search),
                                contentDescription = uiText("Search your workspace"),
                                modifier = Modifier.size(24.dp))
                        }
                    }
                }
            )
        }

        if (showLibrary) item(key = "local-search") {
            TextButton(onClick = { showLocalSearch = true },
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Icon(painterResource(R.drawable.halo_ic_search), null, Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(uiText("Find related notes"))
            }
        }
        if (!showLibrary) item(key = "create") {
            val actions = listOf<Triple<String, Int, () -> Unit>>(
                Triple("Note", R.drawable.halo_ic_new_note, viewModel::createNote),
                Triple("Canvas", R.drawable.halo_ic_canvas, viewModel::createCanvas),
                Triple("Import", R.drawable.halo_ic_import, { importFile = true; showInboxCapture = true }),
                Triple("Inbox", R.drawable.halo_ic_inbox, { showInboxCapture = true }),
            )
            val columns = if (androidx.compose.ui.platform.LocalDensity.current.fontScale >= 1.4f) 2 else 4
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                actions.chunked(columns).forEach { group ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        group.forEach { (label, icon, action) ->
                            NotebookCreateAction(label, icon, !mutating, action, Modifier.weight(1f))
                        }
                    }
                }
            }
        }

        // The reference places the learning continuation FIRST, followed by
        // one recent board/note — never a dense two-column wall of previews.
        if (!showLibrary && state.query.isBlank() &&
            state.filter == NotebookFilter.ALL && state.selectedCollectionId == null &&
            visibleItems.isNotEmpty()) {
            item(key = "continue") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(uiText("Continue"), style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    visibleItems.take(2).forEach { item ->
                        Surface(
                            onClick = { viewModel.openObject(item.objectRef) },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 76.dp),
                            shape = com.halo.app.ui.design.HaloShapes.card,
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        ) {
                            Row(Modifier.fillMaxWidth().padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Box(Modifier.size(40.dp).background(
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = .22f),
                                    RoundedCornerShape(12.dp)),
                                    contentAlignment = Alignment.Center) {
                                    Icon(painterResource(workspaceIconResource(item.objectRef)),
                                        contentDescription = null, modifier = Modifier.size(22.dp),
                                        tint = MaterialTheme.colorScheme.primary)
                                }
                                Column(Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                    Text(workspaceDisplayTitle(item.objectRef.title, item.snippet,
                                        uiText(workspaceTypeLabel(item.objectRef))),
                                        style = MaterialTheme.typography.titleSmall,
                                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    Text(uiText(workspaceTypeLabel(item.objectRef)),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
        }

        if (!showLibrary && state.duePractice.isEmpty()) {
            state.studySessions.firstOrNull()?.let { session ->
                item(key = "continue-lesson") {
                    Surface(onClick = { viewModel.openStudy(session.id) },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 76.dp),
                        shape = com.halo.app.ui.design.HaloShapes.card, color = MaterialTheme.colorScheme.surfaceContainerLow,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Icon(painterResource(R.drawable.halo_ic_study), null, Modifier.size(28.dp), tint = MaterialTheme.colorScheme.primary)
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(session.goal?.takeIf { it.isNotBlank() } ?: uiText("My lesson"), style = MaterialTheme.typography.titleSmall)
                                Text(uiText("Continue learning"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Icon(Icons.Default.ChevronRight, null, Modifier.size(20.dp))
                        }
                    }
                }
            }
        }
        if (!showLibrary && (state.duePractice.isNotEmpty() || pendingMemoryReviews > 0)) item(key = "waiting") {
            Text(uiText("Waiting for you").uppercase(), style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
        }
        if (!showLibrary && state.duePractice.isNotEmpty()) {
            item(key = "due-review") {
                val due = state.duePractice.first()
                Surface(
                    onClick = { viewModel.openStudy(due.sessionId, review = true) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = com.halo.app.ui.design.HaloShapes.card,
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary),
                ) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(painterResource(R.drawable.halo_ic_study), contentDescription = null)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                uiText("Review cards: %1\$d", state.duePractice.size),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                due.question,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Text(uiText("Start"), style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }

        if (!showLibrary && pendingMemoryReviews > 0) {
            item(key = "memory-review") {
                NotebookPendingMemoryReview(
                    count = pendingMemoryReviews,
                    onReview = { viewModel.openMemoryHub("Suggestions") },
                )
            }
        }

        // Keep Study and Memory accessible from Notebook, without placing two
        // utility rows above Continue (BrandBook Round 2, p.39).
        // They stay reachable even in Library/search mode.
        item(key = "notebook-tools") {
            NotebookLearningTools(
                onStudy = onOpenStudy,
                onMemory = viewModel::openMemoryHub,
            )
        }

        item(key = "library-heading") {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = { if (showLibrary) leaveLibrary() else libraryFocused = true }, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(uiText(if (showLibrary) "Back to Notebook" else "Open Library"))
                }
                TextButton(onClick = { libraryFocused = true; viewModel.setFilter(NotebookFilter.CHATS) }, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(uiText("Saved chats"))
                }
            }
            if (showLibrary) TextButton(onClick = { showWorkingSet = true }, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(uiText("In chat (%1\$d)", state.workingSet.size))
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

        if (!showLibrary) notice?.let { message ->
            item(key = "hub_notice") {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(message, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    TextButton(onClick = viewModel::clearNotice) { Text(uiText("Dismiss")) }
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
                TextButton(onClick = { selecting = !selecting; viewModel.clearSelection() },
                    modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(uiText(if (selecting) "Done" else "Select"))
                }
            }
            if (selecting) FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.Center
            ) {
                Text(uiText("%1\$d selected", selectedIds.size), style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = viewModel::selectVisible, enabled = !mutating,
                    modifier = Modifier.heightIn(min = 48.dp)) { Text(uiText("Select visible")) }
                Box {
                    TextButton(onClick = { batchMenu = true }, enabled = selectedIds.isNotEmpty() && !mutating) { Text(uiText(if (mutating) "Updating..." else "Actions")) }
                    DropdownMenu(batchMenu, { batchMenu = false }) {
                        if (state.filter !in listOf(NotebookFilter.TRASH, NotebookFilter.ARCHIVED)) {
                            DropdownMenuItem(text = { Text(uiText("Add to Halo context")) }, onClick = { batchMenu = false; viewModel.applyToSelection(NotebookBatchAction.CONTEXT) })
                            DropdownMenuItem(text = { Text(uiText("Archive")) }, onClick = { batchMenu = false; viewModel.applyToSelection(NotebookBatchAction.ARCHIVE) })
                        } else DropdownMenuItem(text = { Text(uiText("Restore")) }, onClick = { batchMenu = false; viewModel.applyToSelection(NotebookBatchAction.RESTORE) })
                        if (state.filter != NotebookFilter.TRASH) DropdownMenuItem(text = { Text(uiText("Move to trash")) }, onClick = { batchMenu = false; confirmTrash = true })
                        state.collections.forEach { collection -> DropdownMenuItem(text = { Text(uiText("Add to %1\$s", collection.name)) }, onClick = {
                            batchMenu = false; viewModel.applyToSelection(NotebookBatchAction.COLLECTION, collection.id)
                        }) }
                    }
                }
            }
            notice?.let { message -> Row(verticalAlignment = Alignment.CenterVertically) {
                Text(message, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = viewModel::clearNotice) { Text(uiText("Dismiss")) }
            } }
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
                        value = state.query,
                        onValueChange = viewModel::setQuery,
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        leadingIcon = { Icon(painterResource(R.drawable.halo_ic_search), contentDescription = null) },
                        placeholder = { Text(uiText("Search your workspace")) },
                        shape = com.halo.app.ui.design.HaloShapes.control, kind = VoiceFieldKind.SEARCH)

                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { showLocalSearch = true }) {
                            Text(uiText("Find related notes"))
                        }
                    }
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
        if(showLibrary && state.filter in listOf(NotebookFilter.MEMORY,NotebookFilter.INBOX)) item(key="personal-memory") {
            PersonalMemorySection(viewModel,inboxOnly=state.filter == NotebookFilter.INBOX)
        }
        if (showLibrary) item(key = "library-title") {
            val location = state.collections.firstOrNull { it.id == state.selectedCollectionId }?.name ?: uiText(state.filter.label)
            SectionTitle(location, uiText("Items: %1\$d", visibleItems.size))
        }

        if (!state.isLoaded) {
            item(key = "loading") {
                HaloProgressPresentation(uiText("Loading your library…"))
            }
        } else if (visibleItems.isEmpty() && state.filter !in listOf(NotebookFilter.MEMORY, NotebookFilter.INBOX)) {
            item(key = "empty") {
                NotebookEmptyGuidance(state.query, state.filter, state.selectedCollectionId != null, !mutating,
                    viewModel::createNote, viewModel::createCanvas) {
                    viewModel.setQuery(""); viewModel.setFilter(NotebookFilter.ALL); viewModel.selectCollection(null)
                }
            }
        } else if (showLibrary) {
            items(notebookItemsForSurface(visibleItems, showLibrary),
                key = { it.objectRef.id.value }) { item ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                if (selecting) androidx.compose.material3.Checkbox(checked = item.objectRef.id.value in selectedIds,
                    enabled = !mutating, onCheckedChange = { viewModel.toggleSelection(item.objectRef.id.value) })
                WorkspaceObjectRow(
                    item = item,
                    selected = item.objectRef.id.value in state.workingSet,
                    favorite = item.objectRef.id.value in state.favorites,
                    collections = collectionChoices,
                    inSelectedCollection = item.objectRef.id.value in state.selectedCollectionMembers,
                    onOpen = { if (selecting) viewModel.toggleSelection(item.objectRef.id.value) else viewModel.openSearchItem(item) },
                    onStudy = { viewModel.startStudy(item.objectRef.id.value) },
                    onToggleFavorite = { viewModel.toggleFavorite(item.objectRef.id.value) },
                    onAddToCollection = { viewModel.addToCollection(item.objectRef.id.value, it) },
                    onRemoveFromSelectedCollection = { viewModel.removeFromSelectedCollection(item.objectRef.id.value) },
                    onToggleContext = {
                        viewModel.selectContext(item.objectRef.id.value, item.objectRef.id.value !in state.workingSet)
                    },
                    onMultiSelect = {
                        if (!selecting) { selecting = true; viewModel.clearSelection() }
                        viewModel.toggleSelection(item.objectRef.id.value)
                    },
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
                Text(workspaceDisplayTitle(row.title, item.snippet, uiText(workspaceTypeLabel(row))), style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
                    tint = if (favorite && isDark) Color(0xFFFF9F6B)
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

private fun workspaceTypeLabel(row: WorkspaceObject): String = when (row.type) {
    WorkspaceObjectType.NOTE -> "Note"
    WorkspaceObjectType.CANVAS -> "Canvas"
    WorkspaceObjectType.FILE -> "File"
    WorkspaceObjectType.MEDIA -> "Media"
    WorkspaceObjectType.CONVERSATION -> "Saved chat"
    WorkspaceObjectType.MEMORY -> "Memory"
    WorkspaceObjectType.ARTIFACT -> if (row.contentRef.startsWith("canvas:")) "Canvas" else "Artifact"
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
                Text(uiText(title), style = MaterialTheme.typography.titleSmall, maxLines = 1,
                    overflow = TextOverflow.Ellipsis)
            }
            // Kept as a visible, localizable action label for Study; clicking
            // anywhere on the tile opens the same real learning flow.
            Text(uiText(actionLabel ?: description), style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
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
    WorkspaceObjectType.NOTE -> com.halo.app.R.drawable.halo_ic_new_note
    WorkspaceObjectType.CANVAS -> com.halo.app.R.drawable.halo_ic_canvas
    WorkspaceObjectType.CONVERSATION -> com.halo.app.R.drawable.halo_ic_saved_chat
    WorkspaceObjectType.MEMORY -> com.halo.app.R.drawable.halo_ic_memory
    WorkspaceObjectType.FILE, WorkspaceObjectType.MEDIA -> com.halo.app.R.drawable.halo_ic_file_doc
    WorkspaceObjectType.ARTIFACT -> if (row.contentRef.startsWith("canvas:")) com.halo.app.R.drawable.halo_ic_canvas else com.halo.app.R.drawable.halo_ic_file_doc
}
