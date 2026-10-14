package com.halo.app.ui.screens.notebook

import com.halo.app.language.uiText

import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LifecycleResumeEffect

import android.text.format.DateUtils
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.rounded.EditNote
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
fun NotebookScreen(viewModel: NotebookViewModel, onOpenStudy: () -> Unit, onSkills: () -> Unit = {}) {
    LifecycleResumeEffect(viewModel) {
        viewModel.refreshContextPolicy()
        onPauseOrDispose {}
    }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val cloudAllowed by viewModel.cloudSharing.collectAsStateWithLifecycle()
    val selectedIds by viewModel.selectedIds.collectAsStateWithLifecycle()
    val notice by viewModel.notice.collectAsStateWithLifecycle()
    val mutating by viewModel.isMutating.collectAsStateWithLifecycle()
    var selecting by rememberSaveable { mutableStateOf(false) }
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
    var showTools by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    var importFile by rememberSaveable { mutableStateOf(false) }
    if (showInboxCapture) InboxCaptureDialog(initialImport = importFile) { showInboxCapture = false; importFile = false }

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
                OutlinedTextField(
                    value = collectionName,
                    onValueChange = { collectionName = it.take(60) },
                    singleLine = true,
                    label = { Text(uiText("Collection name")) },
                )
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
                title = uiText("Notebook"),
                subtitle = uiText("Your notes, conversations and ideas, together."),
                glyph = HaloGlyph.NOTE,
            )
        }

        item(key = "toolbox") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    HaloFocusAction(uiText(if (state.filter == NotebookFilter.CANVASES) "New canvas" else "New note"), HaloGlyph.NOTE,
                        enabled = !mutating, onClick = if (state.filter == NotebookFilter.CANVASES) viewModel::createCanvas else viewModel::createNote,
                        active = state.isLoaded && visibleItems.isNotEmpty(),
                        style = if (visibleItems.isEmpty()) com.halo.app.ui.components.HaloActionStyle.SECONDARY else com.halo.app.ui.components.HaloActionStyle.PRIMARY)
                    com.halo.app.ui.components.HaloActionButton(onClick = { importFile = true; showInboxCapture = true },
                        enabled = !mutating, style = com.halo.app.ui.components.HaloActionStyle.SECONDARY) { Text(uiText("Import file")) }
                    com.halo.app.ui.components.HaloActionButton(onClick = { showInboxCapture = true },
                        style = com.halo.app.ui.components.HaloActionStyle.SECONDARY) { Text(uiText("Save to Inbox")) }
                    TextButton(onClick = { showTools = !showTools }) { Text(uiText(if (showTools) "Hide tools" else "More tools")) }
                }
                if (showTools) {
                    WorkspaceTool(
                        "Notebook", "Your notes, conversations and ideas, together.", Icons.Rounded.EditNote,
                        onOpen = { viewModel.setFilter(NotebookFilter.NOTES); showTools = false },
                        actionLabel = "New note", onAction = viewModel::createNote,
                    )
                    WorkspaceTool(
                        "Skills", "Manage workspace and phone actions", Icons.Rounded.AutoAwesome,
                        onOpen = onSkills,
                    )
                    WorkspaceTool(
                        "Canvas", "Connect ideas and create with AI", Icons.Rounded.DashboardCustomize,
                        onOpen = { viewModel.setFilter(NotebookFilter.CANVASES) },
                        actionLabel = "New canvas", onAction = viewModel::createCanvas,
                    )
                    WorkspaceTool(
                        "Memory", "What Halo remembers and learns", Icons.Rounded.Psychology,
                        onOpen = { viewModel.openMemoryHub() },
                    )
                    WorkspaceTool(
                        "Study", "Explore a topic, continue a lesson or review", Icons.Rounded.AutoStories,
                        onOpen = onOpenStudy,
                    )
                }
                if (showTools || (state.isLoaded && visibleItems.isEmpty() && state.query.isBlank())) Text(uiText("Notes are documents. Canvas connects them. Inbox holds new captures. Memory stores information you approve."),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (showTools || (state.isLoaded && visibleItems.isEmpty() && state.query.isBlank())) Text(uiText("Saved on this device. Adding context does not change your sharing permissions."),
                    style = MaterialTheme.typography.labelSmall)
            }
        }

        item(key = "library-heading") {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(uiText("Your library"), Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                TextButton(onClick = { showWorkingSet = true }) { Text(uiText("Context (%1\$d)", state.workingSet.size)) }
            }
        }

        item(key = "organize") {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box {
                    TextButton(onClick = { sortMenu = true }) { Text(uiText(state.sort.label)) }
                    DropdownMenu(sortMenu, { sortMenu = false }) {
                        NotebookSort.entries.forEach { sort -> DropdownMenuItem(text = { Text(uiText(sort.label)) }, onClick = { viewModel.setSort(sort); sortMenu = false }) }
                    }
                }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = { selecting = !selecting; viewModel.clearSelection() }) { Text(uiText(if (selecting) "Done" else "Select")) }
            }
            if (selecting) Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(uiText("%1\$d selected", selectedIds.size), Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = viewModel::selectVisible, enabled = !mutating) { Text(uiText("Select visible")) }
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

        if (state.duePractice.isNotEmpty()) {
            item(key = "due-review") {
                val due = state.duePractice.first()
                Surface(
                    onClick = { viewModel.openStudy(due.sessionId, review = true) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = com.halo.app.ui.design.HaloShapes.card,
                    color = MaterialTheme.colorScheme.primaryContainer,
                ) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.AutoStories, contentDescription = null)
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

        item(key = "library-controls") {
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
                    OutlinedTextField(
                        value = state.query,
                        onValueChange = viewModel::setQuery,
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                        placeholder = { Text(uiText("Search your workspace")) },
                        shape = com.halo.app.ui.design.HaloShapes.control,
                    )
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
        if(state.filter in listOf(NotebookFilter.MEMORY,NotebookFilter.INBOX)) item(key="personal-memory") {
            PersonalMemorySection(viewModel,inboxOnly=state.filter == NotebookFilter.INBOX)
        }
        item(key = "library-title") {
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
        } else {
            items(visibleItems, key = { it.objectRef.id.value }) { item ->
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
                )
                }
            }
        }
        item(key = "bottom-space") { Spacer(Modifier.height(12.dp)) }
    }
}

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
            leadingIcon = { Icon(Icons.Rounded.Inventory2, null, Modifier.size(18.dp)) },
            label = { Text(uiText("Library")) },
        )
        state.collections.forEach { collection ->
            FilterChip(
                selected = state.selectedCollectionId == collection.id,
                onClick = { onSelect(collection.id) },
                leadingIcon = { Icon(Icons.Rounded.Folder, null, Modifier.size(18.dp)) },
                label = { Text(collection.name) },
            )
        }
        TextButton(onClick = onCreate) { Text(uiText("New collection")) }
        if (state.selectedCollectionId != null) {
            IconButton(onClick = onDelete) { Icon(Icons.Rounded.DeleteOutline, uiText("Delete selected collection")) }
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

@Composable
private fun WorkspaceObjectRow(
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
) {
    val row = item.objectRef
    var menuExpanded by remember(row.id.value) { mutableStateOf(false) }
    Surface(
        onClick = onOpen,
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shape = com.halo.app.ui.design.HaloShapes.control,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.72f)),
    ) {
        Row(
            Modifier.padding(start = 12.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(shape = com.halo.app.ui.design.HaloShapes.control, color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.size(44.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(workspaceIcon(row), null, Modifier.size(23.dp), MaterialTheme.colorScheme.onSecondaryContainer)
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(row.title.ifBlank { uiText("Untitled") }, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val detail = item.snippet?.takeIf { it.isNotBlank() } ?: uiText(workspaceTypeLabel(row))
                Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        DateUtils.getRelativeTimeSpanString(row.updatedAt, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (row.inInbox) MetadataLabel("Inbox")
                    if (selected) MetadataLabel("In context")
                    if (favorite) Icon(Icons.Rounded.Star, uiText("Favorite"), Modifier.size(14.dp), MaterialTheme.colorScheme.primary)
                }
            }
            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(Icons.Rounded.MoreVert, uiText("Organize %1\$s", row.title.ifBlank { uiText("item") }))
                }
                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                    if (row.type == com.halo.app.data.notebook.WorkspaceObjectType.NOTE && row.lifecycle == com.halo.app.data.notebook.WorkspaceObjectLifecycle.ACTIVE) {
                        DropdownMenuItem(text = { Text(uiText("Study this note")) }, onClick = { menuExpanded = false; onStudy() })
                    }

                    DropdownMenuItem(
                        leadingIcon = { Icon(if (favorite) Icons.Rounded.Star else Icons.Rounded.StarBorder, null) },
                        text = { Text(uiText(if (favorite) "Remove favorite" else "Add favorite")) },
                        onClick = { menuExpanded = false; onToggleFavorite() },
                    )
                    DropdownMenuItem(
                        leadingIcon = { Icon(Icons.Rounded.Psychology, null) },
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
                            leadingIcon = { Icon(Icons.Rounded.Folder, null) },
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
private fun MetadataLabel(text: String) {
    Text(uiText(text), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
}

private fun workspaceIcon(row: WorkspaceObject): ImageVector = when (row.type) {
    WorkspaceObjectType.NOTE -> Icons.Rounded.NoteAlt
    WorkspaceObjectType.CANVAS -> Icons.Rounded.DashboardCustomize
    WorkspaceObjectType.FILE, WorkspaceObjectType.MEDIA -> Icons.Rounded.Description
    WorkspaceObjectType.CONVERSATION -> Icons.Rounded.Forum
    WorkspaceObjectType.MEMORY -> Icons.Rounded.Psychology
    WorkspaceObjectType.ARTIFACT -> if (row.contentRef.startsWith("canvas:")) Icons.Rounded.DashboardCustomize else Icons.Rounded.Description
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

@Composable
private fun WorkspaceTool(
    title: String,
    description: String,
    icon: ImageVector,
    onOpen: () -> Unit,
    actionLabel: String? = null,
    onAction: () -> Unit = {},
) {
    Surface(
        onClick = onOpen,
        modifier = Modifier.fillMaxWidth(),
        shape = com.halo.app.ui.design.HaloShapes.control,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(uiText(title), style = MaterialTheme.typography.titleSmall)
                Text(uiText(description), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (actionLabel != null) TextButton(onClick = onAction) { Text(uiText(actionLabel)) }
        }
    }
}
