package com.halo.app.ui.screens.notebook

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.halo.app.data.memory.MemoryCandidate
import com.halo.app.data.memory.MemoryScope
import com.halo.app.data.memory.MemoryKind
import com.halo.app.ui.workspace.*
import com.halo.runtime.cognition.memory.MemoryConflictKind
import com.halo.runtime.cognition.memory.MemoryLimits

@Composable
fun MemoryHubScreen(viewModel: MemoryHubViewModel, notebook: NotebookViewModel, onBack: () -> Unit,
    onOpenMemory: (String) -> Unit, onSettings: () -> Unit, initialSection: String = "Memories",
    initialCandidate: MemoryCandidate? = null) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val pending by viewModel.pending.collectAsStateWithLifecycle()
    val episodes by viewModel.episodes.collectAsStateWithLifecycle()
    val useMemory by viewModel.memoryUse.collectAsStateWithLifecycle()
    val review by viewModel.review.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val scopes by viewModel.scopeChoices.collectAsStateWithLifecycle()
    var section by remember { mutableStateOf(initialSection) }
    var editor by remember { mutableStateOf(initialCandidate != null) }
    var draft by remember { mutableStateOf(initialCandidate?.content.orEmpty()) }
    var kind by remember { mutableStateOf(initialCandidate?.kind ?: MemoryKind.FACT) }
    var scopeChoice by remember { mutableStateOf(MemoryScopeChoice(MemoryScope.GLOBAL, null, "Everywhere")) }
    var search by remember { mutableStateOf("") }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 20.dp), contentPadding = PaddingValues(vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { HaloWorkspaceHeader("Memory", "What Halo remembers and may use as context", onBack = onBack) }
        item {
            Text("${state.count} remembered · ${pending.size} suggestions")
            Row(Modifier.fillMaxWidth()) { Text("Use memory in conversations", Modifier.weight(1f)); Switch(useMemory, viewModel::useMemory) }
            Text(if (useMemory) "Halo may retrieve relevant saved memories that fit the current scope and context budget." else
                "Saved memories stay here, but Halo will not use them automatically in conversations.", style = MaterialTheme.typography.bodySmall)
            Text("Remote AI receives memory only when your existing cloud-memory permission allows it.", style = MaterialTheme.typography.bodySmall)
        }
        item {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Memories", "Suggestions", "Learning history").forEach { tab -> FilterChip(section == tab, { section = tab }, label = { Text(tab) }) }
            }
        }
        state.error?.let { message -> item { Text(message, color = MaterialTheme.colorScheme.error) } }
        when (section) {
            "Memories" -> {
                item {
                    OutlinedTextField(search, { search = it; viewModel.search(it) }, label = { Text("Search memory") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    Text("Lexical search · Semantic model not installed", style = MaterialTheme.typography.labelSmall)
                }
                item {
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MemoryGroup.entries.forEach { group -> FilterChip(state.group == group, { viewModel.filter(group) }, label = { Text(group.label) }) }
                    }
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(state.scope == null, { viewModel.filterScope(null) }, label = { Text("All scopes") })
                        listOf(MemoryScope.GLOBAL to "Everywhere", MemoryScope.PROJECT to "Projects", MemoryScope.DEVICE to "Devices", MemoryScope.WORKSPACE_OBJECT to "Workspace").forEach { (scope, label) ->
                            FilterChip(state.scope == scope, { viewModel.filterScope(scope) }, label = { Text(label) })
                        }
                    }
                    if (search.isBlank()) Row(Modifier.horizontalScroll(rememberScrollState())) {
                        MemorySort.entries.forEach { sort -> TextButton({ viewModel.sort(sort) }) { Text(if (state.sort == sort) "✓ ${sort.label}" else sort.label) } }
                    }
                    TextButton({ editor = true }) { Text("+ Remember something") }
                }
                if (state.loading) item { CircularProgressIndicator() }
                else if (state.items.isEmpty()) item { Text(if (search.isBlank()) "No memories in this view. Remember something or review a suggestion." else "No current memories match this search in the eligible scope.") }
                items(state.items, key = { it.record.id }) { item ->
                    HaloWorkspaceCard {
                        Text(item.kindLabel, style = MaterialTheme.typography.labelMedium)
                        Text(item.record.content, style = MaterialTheme.typography.bodyLarge)
                        Text(item.originLabel, style = MaterialTheme.typography.bodySmall)
                        Text("${item.confirmationLabel} · ${item.scopeLabel}", style = MaterialTheme.typography.labelSmall)
                        if (item.stale) Text("Needs review · This memory may be outdated.")
                        if (item.record.usageCount > 0) Text(item.usageSummary, style = MaterialTheme.typography.bodySmall)
                        TextButton({ onOpenMemory(item.record.id) }) { Text("Details · Correct · Forget") }
                    }
                }
                if (state.items.size == 100) item { Text("Showing up to 100 memories. Search to narrow the list.") }
            }
            "Suggestions" -> {
                item { Text("Suggestions are not saved facts. Nothing is remembered until you approve it.") }
                if (pending.isEmpty()) item { Text("No suggestions awaiting review") }
                items(pending, key = { it.id }) { row -> SuggestionCard(row, notebook) { block -> viewModel.act(block); Unit } }
            }
            else -> {
                item { Text("Past tasks Halo may use to understand repeated workflows. These are historical summaries, not current facts about you.") }
                if (episodes.isEmpty()) item { Text("No meaningful task history yet.") }
                items(episodes, key = { it.id.value }) { episode ->
                    HaloWorkspaceCard {
                        Text("${MemoryPresentation.date(episode.endedAt.toEpochMilli())} · ${if (episode.unknownCompletion) "Completion wasn't confirmed" else when (episode.outcome) {
                            com.halo.runtime.cognition.memory.EpisodeOutcome.SUCCESS -> "Completed"
                            com.halo.runtime.cognition.memory.EpisodeOutcome.PARTIAL -> "Partially completed"
                            com.halo.runtime.cognition.memory.EpisodeOutcome.FAILED -> "Failed"
                            com.halo.runtime.cognition.memory.EpisodeOutcome.CANCELLED -> "Cancelled"
                            com.halo.runtime.cognition.memory.EpisodeOutcome.BLOCKED -> "Blocked"
                            com.halo.runtime.cognition.memory.EpisodeOutcome.INTERRUPTED -> "Interrupted"
                        }}", style = MaterialTheme.typography.titleSmall)
                        Text(episode.summary)
                        Text("Referenced ${episode.referencedObjectIds.size} Workspace items · ${episode.referencedMemoryIds.size} saved memories")
                        TextButton({ viewModel.act { viewModel.learning.dao.saveEpisode(episode.id.value) } }) { Text("Keep in learning history") }
                        Text("Prevents normal retention cleanup from removing this historical task.", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        item {
            HorizontalDivider()
            Text("Memory: saved information you approved, plus clearly marked imports. Learning controls suggestions. Notebook holds your documents and files.", style = MaterialTheme.typography.bodySmall)
            TextButton(onSettings) { Text("Learning settings") }
        }
    }
    if (editor && review == null) AlertDialog(onDismissRequest = { if (!busy) editor = false }, title = { Text("Remember something") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (initialCandidate != null) Text("Review what becomes memory. The original note stays in Notebook.")
            if (draft.length > MemoryLimits.CONTENT) Text("This note is too long to store as one memory. Write the concise fact Halo should remember.")
            OutlinedTextField(draft, { draft = it }, label = { Text("What should Halo remember?") }, modifier = Modifier.fillMaxWidth(), maxLines = 6)
            if (draft.length >= 900) Text("${draft.length}/${MemoryLimits.CONTENT}")
            Row(Modifier.horizontalScroll(rememberScrollState())) {
                listOf(MemoryKind.PREFERENCE, MemoryKind.FACT, MemoryKind.PERSON, MemoryKind.DEVICE, MemoryKind.INSTRUCTION, MemoryKind.WORKFLOW).forEach { type ->
                    FilterChip(kind == type, { kind = type }, label = { Text(MemoryPresentation.kind(type)) })
                }
                if (scopes.any { it.scope == MemoryScope.PROJECT }) FilterChip(kind == MemoryKind.PROJECT, { kind = MemoryKind.PROJECT }, label = { Text("Project") })
            }
            Text("Where it applies")
            Row(Modifier.horizontalScroll(rememberScrollState())) { scopes.forEach { choice -> FilterChip(scopeChoice == choice, { scopeChoice = choice }, label = { Text(choice.label) }) } }
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }, confirmButton = { TextButton(enabled = !busy && draft.trim().length in 1..MemoryLimits.CONTENT && (kind != MemoryKind.PROJECT || scopeChoice.scope == MemoryScope.PROJECT), onClick = {
        viewModel.prepare((initialCandidate ?: MemoryCandidate(draft)).copy(content = draft, kind = kind, scope = scopeChoice.scope, scopeId = scopeChoice.id))
    }) { Text("Review memory") } }, dismissButton = { TextButton({ editor = false }) { Text("Cancel") } })
    review?.let { reviewed -> AlertDialog(onDismissRequest = { if (!busy) { viewModel.discardReview(); editor = false } }, title = {
        Text(when (reviewed.conflict.kind) { MemoryConflictKind.CONFLICTS -> "Update memory?"; MemoryConflictKind.REINFORCES -> "Halo already remembers this"; else -> "Remember this?" })
    }, text = { Column {
        reviewed.conflict.records.forEach { Text("Currently remembered"); Text(it.content) }
        Text("New information"); Text(reviewed.candidate.content)
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    } }, confirmButton = { TextButton(enabled = !busy && (reviewed.conflict.kind != MemoryConflictKind.CONFLICTS || reviewed.conflict.records.size == 1), onClick = { editor = false; viewModel.saveReviewed() }) {
        Text(when (reviewed.conflict.kind) { MemoryConflictKind.CONFLICTS -> "Use new"; MemoryConflictKind.REINFORCES -> "Confirm existing"; else -> "Remember" })
    } }, dismissButton = { Row { TextButton({ viewModel.discardReview(); editor = false }) { Text("Keep current") }; TextButton({ viewModel.discardReview(); editor = true }) { Text("Edit new") } } }) }
}
