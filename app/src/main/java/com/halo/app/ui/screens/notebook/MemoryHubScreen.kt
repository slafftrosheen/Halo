package com.halo.app.ui.screens.notebook

import androidx.compose.foundation.verticalScroll

import com.halo.app.ui.components.HaloTextField
import com.halo.app.ui.components.VoiceFieldKind
import com.halo.app.ui.zone5.*
import androidx.compose.ui.res.stringResource
import com.halo.app.R
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.platform.LocalFocusManager
import com.halo.app.language.uiText

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.listSaver
import com.halo.app.ui.design.HaloFocusAction
import com.halo.app.ui.icons.HaloGlyph
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import com.halo.app.ui.fx.HaloFxAnimation
import com.halo.app.ui.fx.HaloFxMoment
import kotlinx.coroutines.delay
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.halo.app.data.memory.MemoryCandidate
import com.halo.app.data.memory.MemoryScope
import com.halo.app.data.memory.MemoryKind
import com.halo.app.ui.workspace.*
import com.halo.runtime.cognition.memory.MemoryConflictKind
import com.halo.runtime.cognition.memory.MemoryLimits

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MemoryHubScreen(viewModel: MemoryHubViewModel, notebook: NotebookViewModel, onBack: () -> Unit,
    onOpenMemory: (String) -> Unit, onSettings: () -> Unit, initialSection: String = "Memories",
    initialCandidate: MemoryCandidate? = null) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val pending by viewModel.pending.collectAsStateWithLifecycle()
    val episodes by viewModel.episodes.collectAsStateWithLifecycle()
    val pendingStatus by viewModel.pendingStatus.collectAsStateWithLifecycle()
    val episodeStatus by viewModel.episodeStatus.collectAsStateWithLifecycle()
    val useMemory by viewModel.memoryUse.collectAsStateWithLifecycle()
    val review by viewModel.review.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val scopes by viewModel.scopeChoices.collectAsStateWithLifecycle()
    val savedRevision by viewModel.savedRevision.collectAsStateWithLifecycle()
    var lastObservedSave by remember { mutableLongStateOf(savedRevision) }
    var showRemember by remember { mutableStateOf(false) }
    LaunchedEffect(savedRevision) {
        // A restored / recomposed screen never celebrates an old write.
        val newWrite = savedRevision > lastObservedSave
        lastObservedSave = savedRevision
        if (newWrite) {
            showRemember = true
            delay(2000L) // Supplied Remember is 60 frames at 30 fps.
            showRemember = false
        }
    }
    var section by rememberSaveable(initialCandidate?.content, initialSection) { mutableStateOf(initialSection.takeIf { it in listOf("Memories", "Suggestions", "Learning history") } ?: "Memories") }
    var editor by rememberSaveable(initialCandidate?.content, initialSection) { mutableStateOf(initialCandidate != null) }
    var draft by rememberSaveable(initialCandidate?.content, initialSection) { mutableStateOf(initialCandidate?.content.orEmpty()) }
    var kind by rememberSaveable(initialCandidate?.content, initialSection) { mutableStateOf(initialCandidate?.kind ?: MemoryKind.FACT) }
    var scopeChoice by rememberSaveable(stateSaver = listSaver<MemoryScopeChoice, String>(
        save = { listOf(it.scope.name, it.id.orEmpty(), it.label) },
        restore = { MemoryScopeChoice(MemoryScope.valueOf(it[0]), it[1].takeIf(String::isNotEmpty), it[2]) },
    )) { mutableStateOf(MemoryScopeChoice(MemoryScope.GLOBAL, null, "Everywhere")) }
    var search by rememberSaveable(initialCandidate?.content, initialSection) { mutableStateOf("") }
    LaunchedEffect(search, viewModel) { viewModel.search(search) }
    val shellOwnsOrb = com.halo.app.ui.presence.LocalShellHaloOrb.current
    val pageOwner = com.halo.app.ui.presence.ShellPageOwner("memory", sourceIdentity = "$savedRevision:$section:${state.group}:${state.scope}:${state.sort}:${search.hashCode()}:${state.query.hashCode()}:$editor:${review.hashCode()}:$busy",
        canonicalToolIds = listOf("halo_search_memory", "halo_read_memory", "halo_remember", "halo_propose_memory"))
    com.halo.app.ui.presence.BindShellPageActions(pageOwner, listOf(
        com.halo.app.ui.presence.ShellPageAction("page.memory.add", "Remember something", HaloGlyph.MEMORY, !busy) { editor = true },
        com.halo.app.ui.presence.ShellPageAction("page.memory.settings", "Learning settings", HaloGlyph.SETTINGS, !busy, onSettings),
    ))
    if (shellOwnsOrb) com.halo.app.ui.presence.BindShellUtilityBand(pageOwner) {
        ToolZoneHeader(uiText("Memory") + " · " + uiText(section), onBack)
    }
    val memoryScroll = rememberLazyListState()
    val suggestionsScroll = rememberLazyListState()
    val historyScroll = rememberLazyListState()
    val focus = LocalFocusManager.current
    var showUsage by rememberSaveable { mutableStateOf(false) }
    Box(Modifier.fillMaxSize()) {
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
    Zone5LocalTabs(listOf(Zone5LocalTab("Memories", uiText("Memories"), "tool_memory"),
        Zone5LocalTab("Suggestions", uiText("Suggestions"), "memory_suggestions"),
        Zone5LocalTab("Learning history", uiText("Learning history"), "memory_history")), section,
        onSelect = { focus.clearFocus(); section = it })
    LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = when (section) { "Suggestions" -> suggestionsScroll; "Learning history" -> historyScroll; else -> memoryScroll },
        contentPadding = PaddingValues(vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (!shellOwnsOrb) item { HaloWorkspaceHeader(uiText("Memory"), uiText("What Halo remembers and may use as context"), onBack = onBack) }
        else item { Zone5InstrumentHeading(uiText("Memory"), "tool_memory", state = uiText("%1\$d remembered · %2\$d suggestions", state.count, pending.size)) }
        item {
            Row(Modifier.fillMaxWidth()) { Text(uiText("Use memory in conversations"), Modifier.weight(1f)); Switch(useMemory, viewModel::useMemory) }
            TextButton({ showUsage = !showUsage }) { Text(stringResource(R.string.zone5_memory_usage_details)) }
            if (showUsage) {
            Text(uiText(if (useMemory) "Halo may retrieve relevant saved memories that fit the current scope and context budget." else
                "Saved memories stay here, but Halo will not use them automatically in conversations."), style = MaterialTheme.typography.bodySmall)
            Text(uiText("Remote AI receives memory only when your existing cloud-memory permission allows it."), style = MaterialTheme.typography.bodySmall)
            }
        }
        state.error?.let { message -> item {
            Text(if (message == "Memory could not be loaded.") stringResource(R.string.zone5_memory_load_error) else uiText(message), color = MaterialTheme.colorScheme.error)
            Zone5ActionButton(uiText("Try again"), "action_refresh", onClick = viewModel::retry)
        } }
        when (section) {
            "Memories" -> {
                item {
                    HaloTextField(search, { search = it; viewModel.search(it) }, label = { Text(uiText("Search memory")) }, modifier = Modifier.fillMaxWidth(), singleLine = true, kind = VoiceFieldKind.SEARCH)
                    Text(uiText("Search by words in your saved memories."), style = MaterialTheme.typography.bodyMedium)
                }
                item {
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MemoryGroup.entries.forEach { group -> FilterChip(state.group == group, { viewModel.filter(group) }, label = { Text(uiText(group.label)) }) }
                    }
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(state.scope == null, { viewModel.filterScope(null) },
                            label = { Text(uiText("All places")) })
                        listOf(MemoryScope.GLOBAL to "Everywhere", MemoryScope.PROJECT to "Projects", MemoryScope.DEVICE to "Devices", MemoryScope.WORKSPACE_OBJECT to "Workspace").forEach { (scope, label) ->
                            FilterChip(state.scope == scope, { viewModel.filterScope(scope) }, label = { Text(uiText(label)) })
                        }
                    }
                    if (search.isBlank()) Row(Modifier.horizontalScroll(rememberScrollState())) {
                        MemorySort.entries.forEach { sort -> TextButton({ viewModel.sort(sort) }) { Text((if (state.sort == sort) "✓ " else "") + uiText(sort.label)) } }
                    }
                    if (!shellOwnsOrb) HaloFocusAction(uiText("Remember something"), HaloGlyph.MEMORY, !busy, { editor = true }, active = state.items.isEmpty())
                }
                if (state.loading) item { CircularProgressIndicator() }
                else if (state.error == null && state.items.isEmpty()) item { Text(uiText(if (search.isBlank()) "Nothing remembered here yet. Add something to remember, or review Suggestions." else "No memories match. Try another word or change the filters.")) }
                items(state.items, key = { it.record.id }) { item ->
                    HaloWorkspaceCard {
                        Text(uiText(item.kindLabel), style = MaterialTheme.typography.labelMedium)
                        Text(item.record.content, style = MaterialTheme.typography.bodyLarge)
                        com.halo.app.ui.components.HaloReadAloud(item.record.content)
                        Text(uiText(item.originLabel), style = MaterialTheme.typography.bodySmall)
                        Text(uiText(item.confirmationLabel) + " · " + uiText(item.scopeLabel), style = MaterialTheme.typography.labelSmall)
                        if (item.stale) Text(uiText("Needs review · This memory may be outdated."))
                        if (item.record.usageCount > 0) Text(item.usageSummary, style = MaterialTheme.typography.bodySmall)
                        TextButton({ onOpenMemory(item.record.id) }) { Text(uiText("Details · Correct · Forget")) }
                    }
                }
                if (state.items.size == 100) item { Text(uiText("Showing up to 100 memories. Search to narrow the list.")) }
            }
            "Suggestions" -> {
                item { Text(uiText("Suggestions are not saved facts. Nothing is remembered until you approve it.")) }
                if (pendingStatus.loading) item { Text(stringResource(R.string.zone5_suggestions_loading)); LinearProgressIndicator(Modifier.fillMaxWidth()) }
                else if (pendingStatus.failed) item { Text(stringResource(R.string.zone5_memory_load_error), color = MaterialTheme.colorScheme.error); Zone5ActionButton(uiText("Try again"), "action_refresh", onClick = viewModel::retry) }
                else if (pending.isEmpty()) item { Text(uiText("No suggestions awaiting review")) }
                items(pending, key = { it.id }) { row -> SuggestionCard(row, notebook) { block -> viewModel.act(block); Unit } }
            }
            else -> {
                item { Text(uiText("Past tasks Halo may use to understand repeated workflows. These are historical summaries, not current facts about you.")) }
                if (episodeStatus.loading) item { Text(stringResource(R.string.zone5_history_loading)); LinearProgressIndicator(Modifier.fillMaxWidth()) }
                else if (episodeStatus.failed) item { Text(stringResource(R.string.zone5_memory_load_error), color = MaterialTheme.colorScheme.error); Zone5ActionButton(uiText("Try again"), "action_refresh", onClick = viewModel::retry) }
                else if (episodes.isEmpty()) item { Text(uiText("No meaningful task history yet.")) }
                items(episodes, key = { it.id.value }) { episode ->
                    HaloWorkspaceCard {
                        Text(uiText(MemoryPresentation.date(episode.endedAt.toEpochMilli())) + " · " + uiText(if (episode.unknownCompletion) "Completion wasn't confirmed" else when (episode.outcome) {
                            com.halo.runtime.cognition.memory.EpisodeOutcome.SUCCESS -> "Completed"
                            com.halo.runtime.cognition.memory.EpisodeOutcome.PARTIAL -> "Partially completed"
                            com.halo.runtime.cognition.memory.EpisodeOutcome.FAILED -> "Failed"
                            com.halo.runtime.cognition.memory.EpisodeOutcome.CANCELLED -> "Cancelled"
                            com.halo.runtime.cognition.memory.EpisodeOutcome.BLOCKED -> "Blocked"
                            com.halo.runtime.cognition.memory.EpisodeOutcome.INTERRUPTED -> "Interrupted"
                        }), style = MaterialTheme.typography.titleSmall)
                        Text(episode.summary)
                        Text(uiText("Referenced %1\$d Workspace items · %2\$d saved memories", episode.referencedObjectIds.size, episode.referencedMemoryIds.size))
                        TextButton({ viewModel.act { viewModel.learning.dao.saveEpisode(episode.id.value) } }) { Text(uiText("Keep in learning history")) }
                        Text(uiText("Keep this task in your history instead of letting it expire."),
                            style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        item {
            HorizontalDivider()
            Text(uiText("Memory: saved information you approved, plus clearly marked imports. Learning controls suggestions. Notebook holds your documents and files."), style = MaterialTheme.typography.bodySmall)
            if (!shellOwnsOrb) TextButton(onSettings) { Text(uiText("Learning settings")) }
        }
    }
    }
    if (showRemember) {
        HaloFxAnimation(
            moment = HaloFxMoment.REMEMBER,
            modifier = Modifier.align(Alignment.TopCenter)
                .padding(top = 24.dp).size(120.dp),
        )
    }
    }
    if (editor && review == null) StudioAlertDialog(onDismissRequest = { if (!busy) editor = false }, title = { Text(uiText("Remember something")) }, text = {
        Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (initialCandidate != null) Text(uiText("Review what becomes memory. The original note stays in Notebook."))
            if (draft.length > MemoryLimits.CONTENT) Text(uiText("This note is too long to store as one memory. Write the concise fact Halo should remember."))
            HaloTextField(draft, { draft = it }, enabled = !busy, label = { Text(uiText("What should halo remember?")) }, modifier = Modifier.fillMaxWidth(), maxLines = 6, kind = VoiceFieldKind.TEXT)
            if (draft.length >= 900) Text("${draft.length}/${MemoryLimits.CONTENT}")
            Row(Modifier.horizontalScroll(rememberScrollState())) {
                listOf(MemoryKind.PREFERENCE, MemoryKind.FACT, MemoryKind.PERSON, MemoryKind.DEVICE, MemoryKind.INSTRUCTION, MemoryKind.WORKFLOW).forEach { type ->
                    FilterChip(kind == type, { kind = type }, label = { Text(uiText(MemoryPresentation.kind(type))) })
                }
                if (scopes.any { it.scope == MemoryScope.PROJECT }) FilterChip(kind == MemoryKind.PROJECT, { kind = MemoryKind.PROJECT }, label = { Text(uiText("Project")) })
            }
            Text(uiText("Where it applies"))
            Row(Modifier.horizontalScroll(rememberScrollState())) { scopes.forEach { choice -> FilterChip(scopeChoice == choice, { scopeChoice = choice }, enabled = !busy, label = { Text(if (choice.scope == MemoryScope.GLOBAL) uiText("Everywhere") else choice.label) }) } }
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }, confirmButton = { TextButton(enabled = !busy && draft.trim().length in 1..MemoryLimits.CONTENT && (kind != MemoryKind.PROJECT || scopeChoice.scope == MemoryScope.PROJECT), onClick = {
        viewModel.prepare((initialCandidate ?: MemoryCandidate(draft)).copy(content = draft, kind = kind, scope = scopeChoice.scope, scopeId = scopeChoice.id))
    }) { Text(uiText("Review memory")) } }, dismissButton = { TextButton({ editor = false }, enabled = !busy) { Text(uiText("Cancel")) } })
    // NB07: comparing existing memory with a proposed update must stay readable
    // at large font scales. Nothing is persisted until the user chooses an action.
    review?.let { reviewed ->
        StudioAlertDialog(
            onDismissRequest = {
                if (!busy) { viewModel.discardReview(); editor = false }
            },
            title = {
                Text(uiText(when (reviewed.conflict.kind) {
                    MemoryConflictKind.CONFLICTS -> "Update memory?"
                    MemoryConflictKind.REINFORCES -> "Halo already remembers this"
                    else -> "Remember this?"
                }))
            },
            text = {
                Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    reviewed.conflict.records.forEach { previous ->
                        Text(uiText("Currently remembered"), style = MaterialTheme.typography.labelLarge)
                        Text(previous.content, style = MaterialTheme.typography.bodyMedium)
                    }
                    Text(uiText("New information"), style = MaterialTheme.typography.labelLarge)
                    Text(reviewed.candidate.content, style = MaterialTheme.typography.bodyMedium)
                    state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !busy && (reviewed.conflict.kind != MemoryConflictKind.CONFLICTS ||
                        reviewed.conflict.records.size == 1),
                    onClick = { editor = false; viewModel.saveReviewed() },
                    modifier = Modifier.heightIn(min = 48.dp),
                ) {
                    Text(uiText(when (reviewed.conflict.kind) {
                        MemoryConflictKind.CONFLICTS -> "Use new"
                        MemoryConflictKind.REINFORCES -> "Confirm existing"
                        else -> "Remember"
                    }))
                }
            },
            dismissButton = {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = { viewModel.discardReview(); editor = false },
                        enabled = !busy, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text(uiText("Keep current"))
                    }
                    TextButton(onClick = { viewModel.discardReview(); editor = true },
                        enabled = !busy, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text(uiText("Edit new"))
                    }
                }
            }
        )
    }
}
