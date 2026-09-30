package com.halo.app.ui.screens.notebook

import androidx.lifecycle.compose.collectAsStateWithLifecycle

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.halo.app.di.ManualDependencyGraph
import com.halo.app.data.memory.*
import com.halo.app.ui.workspace.*
import com.halo.runtime.cognition.memory.*
import kotlinx.coroutines.*

/** Uses the Notebook visual system and Inbox; review stays inline, never interrupts normal work. */
@Composable fun PersonalMemorySection(viewModel: NotebookViewModel,inboxOnly: Boolean = false) {
    val learning=ManualDependencyGraph.personalLearning
    val pending by learning.pending.collectAsStateWithLifecycle(emptyList())
    val memories by ManualDependencyGraph.memoryRepository.observeMemories().collectAsStateWithLifecycle(emptyList())
    val episodes by learning.episodes.collectAsStateWithLifecycle(emptyList())
    var section by remember { mutableStateOf(if(inboxOnly) "Suggestions" else "Confirmed") }
    val scope=rememberCoroutineScope()
    var error by remember { mutableStateOf<String?>(null) }
    fun act(block: suspend ()->Unit) { scope.launch { try { error=null; withContext(Dispatchers.IO) { block() } } catch(cancelled: CancellationException) { throw cancelled } catch(e: Exception) { error=e.message ?: "Could not save" } } }
    Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Text(if(inboxOnly) "Learning suggestions" else "Memory",style=MaterialTheme.typography.titleLarge)
        if(!inboxOnly) Row { listOf("Confirmed","Suggestions","Episodes").forEach { name -> TextButton(onClick={section=name}) { Text(if(name==section) "✓ $name" else name) } } }
        error?.let { Text(it,color=MaterialTheme.colorScheme.error) }
        when(section) {
            "Confirmed" -> {
                if(memories.isEmpty()) Text("No confirmed memories. Use Remember on a note or ask Halo explicitly.")
                memories.take(50).forEach { memory -> HaloWorkspaceCard {
                    Text(memory.content,style=MaterialTheme.typography.bodyLarge)
                    Text("${if(memory.userConfirmed) "Confirmed by you" else "Imported · source unverified"} · ${memory.kind.name.lowercase()} · ${memory.scope.name.lowercase()}",style=MaterialTheme.typography.labelSmall)
                    if(memory.validUntil?.let { it <= System.currentTimeMillis() } == true) Text("Possibly outdated")
                    Text("Updated ${java.time.Instant.ofEpochMilli(memory.updatedAt)} · Source: ${memory.origin.name.lowercase().replace('_',' ')}",style=MaterialTheme.typography.bodySmall)
                    TextButton(onClick={viewModel.openMemory(memory.id)}) { Text("Edit · Forget · History") }
                } }
            }
            "Episodes" -> {
                if(episodes.isEmpty()) Text("No meaningful task episodes yet. Ordinary greetings are not saved.")
                episodes.forEach { episode -> HaloWorkspaceCard {
                    Text("${episode.endedAt} · ${episode.outcome.name.lowercase()}",style=MaterialTheme.typography.titleSmall)
                    Text(episode.summary)
                    if(episode.unknownCompletion) Text("Completion remains unknown")
                    episode.referencedObjectIds.forEach { id -> TextButton(onClick={viewModel.openReferencedObject(id)}) { Text("Open referenced object") } }
                    TextButton(onClick={act { learning.dao.saveEpisode(episode.id.value); learning.dao.usedEpisode(episode.id.value,System.currentTimeMillis()) }}) { Text("Keep episode in Brain") }
                } }
            }
            else -> {
                if(pending.isEmpty()) Text("No suggestions awaiting review")
                pending.forEach { row -> SuggestionCard(row,viewModel,::act) }
            }
        }
    }
}
@Composable private fun SuggestionCard(row: PersonalSuggestionEntity,viewModel: NotebookViewModel,act: (suspend ()->Unit)->Unit) {
    val graph=ManualDependencyGraph
    var editing by remember(row.id) { mutableStateOf(false) }
    if(row.kind == "MEMORY") {
        val p=remember(row.json) { PersonalMemoryCodec.decodeProposal(row.json) }
        var content by remember(row.id) { mutableStateOf(p.content) }
        var conflict by remember(row.id) { mutableStateOf<MemoryConflict?>(null) }
        LaunchedEffect(row.id,row.json) {
            try { conflict=withContext(Dispatchers.IO) { graph.personalLearning.conflicts(row.id) } }
            catch(cancelled: CancellationException) { throw cancelled }
            catch(_: Exception) { conflict=null }
        }
        HaloWorkspaceCard {
            Text("Halo noticed · Memory",style=MaterialTheme.typography.titleSmall)
            if(editing) OutlinedTextField(content,{content=it.take(MemoryLimits.CONTENT)},label={Text("Suggested memory")},modifier=Modifier.fillMaxWidth()) else Text(content)
            Text("Suggested by Halo · ${p.evidence.size} evidence reference(s) · ${if(p.confidence >= .8f) "Likely" else "Suggested"}",style=MaterialTheme.typography.labelSmall)
            Text("${p.scope.name.lowercase()}${p.scopeId?.let { " · $it" }.orEmpty()}",style=MaterialTheme.typography.bodySmall)
            if(conflict?.kind == MemoryConflictKind.CONFLICTS) {
                Text("Update memory?",style=MaterialTheme.typography.titleSmall)
                conflict?.records?.forEach { old -> Text("Current: ${old.content}") }
                Text("New: $content")
            }
            Row {
                TextButton(onClick={act { graph.personalLearning.dismiss(row.id) }}) { Text(if(conflict?.kind == MemoryConflictKind.CONFLICTS) "Keep current" else "Not now") }
                TextButton(onClick={editing=!editing}) { Text("Edit") }
                TextButton(enabled=conflict != null && (conflict?.kind != MemoryConflictKind.CONFLICTS || conflict?.records?.size == 1),onClick={act {
                    graph.personalLearning.acceptMemory(row.id,content,if(conflict?.kind == MemoryConflictKind.CONFLICTS) conflict?.records?.singleOrNull()?.id else null)
                }}) { Text(if(conflict?.kind == MemoryConflictKind.CONFLICTS) "Use new" else "Remember") }
            }
            TextButton(onClick={act { graph.personalLearning.dismiss(row.id,true) }}) { Text("Don't suggest this again") }
        }
    } else {
        val suggestion=remember(row.json) { PersonalMemoryCodec.decodeSuggestion(row.json) }
        var name by remember(row.id) { mutableStateOf("Repeated workflow") }
        var description by remember(row.id) { mutableStateOf("A workflow reviewed and approved by me") }
        HaloWorkspaceCard {
            Text("${row.kind.lowercase().replaceFirstChar(Char::uppercase)} · ${suggestion.title}",style=MaterialTheme.typography.titleSmall)
            Text(suggestion.content)
            Text("${suggestion.evidenceCount} recent occurrences",style=MaterialTheme.typography.labelSmall)
            if(row.kind == "PROCEDURE") {
                suggestion.tools.forEachIndexed { i,tool -> Text("${i+1}. ${graph.toolRegistry.descriptor(tool)?.presentation?.title ?: tool}") }
                if(editing) {
                    OutlinedTextField(name,{name=it.take(80)},label={Text("Procedure name")})
                    OutlinedTextField(description,{description=it.take(400)},label={Text("What this procedure does")})
                    Text("No private arguments are copied. Execution still needs the usual permissions and approvals.")
                }
                TextButton(onClick={if(!editing) editing=true else act { graph.personalLearning.saveProcedure(row.id,name,description,graph.learnedProcedureStore) { graph.toolRegistry.contains(it) } }}) { Text(if(editing) "Save reviewed procedure" else "Review procedure") }
            } else if(row.kind == "AUTOMATION") TextButton(onClick={suggestion.targetId?.let { viewModel.reviewAutomation(it,row.id) }}) { Text("Review trigger and permission") }
            else TextButton(onClick={viewModel.reviewCapabilities()}) { Text("Review capability setup") }
            Row {
                TextButton(onClick={act { graph.personalLearning.dismiss(row.id) }}) { Text("Not now") }
                TextButton(onClick={act { graph.personalLearning.dismiss(row.id,true) }}) { Text("Don't suggest this again") }
            }
        }
    }
}
