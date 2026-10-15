package com.halo.app.ui.screens.notebook

import com.halo.app.language.uiText

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

/** Notebook links into the same Memory hub; no second inline memory browser. */
@Composable fun PersonalMemorySection(viewModel: NotebookViewModel, inboxOnly: Boolean = false) {
    TextButton(onClick = { viewModel.openMemoryHub(if (inboxOnly) "Suggestions" else "Memories") }) {
        Text(if (inboxOnly) "Review learning suggestions" else "Open Memory control center")
    }
}
@Composable internal fun SuggestionCard(row: PersonalSuggestionEntity,viewModel: NotebookViewModel,act: (suspend ()->Unit)->Unit) {
    val graph=ManualDependencyGraph
    var editing by remember(row.id) { mutableStateOf(false) }
    if(row.kind == "MEMORY") {
        val p=remember(row.json) { runCatching { PersonalMemoryCodec.decodeProposal(row.json) }.getOrNull() } ?: return
        if (MemorySensitivityClassifier.classify(p.content) == MemorySensitivity.SECRET) return
        var content by remember(row.id) { mutableStateOf(p.content) }
        var conflict by remember(row.id) { mutableStateOf<MemoryConflict?>(null) }
        var scopeLabel by remember(row.id) { mutableStateOf(MemoryPresentation.scope(p.scope, null)) }
        LaunchedEffect(row.id,row.json,content) {
            conflict = null
            delay(250)
            try { conflict=withContext(Dispatchers.IO) { graph.personalLearning.conflicts(row.id, content) } }
            catch(cancelled: CancellationException) { throw cancelled }
            catch(_: Exception) { conflict=null }
            scopeLabel = MemoryPresentation.scope(p.scope, p.scopeId?.let { id -> if (p.scope == com.halo.app.data.memory.MemoryScope.DEVICE) graph.deviceRegistry.getDevice(id)?.identity?.displayName else graph.workspaceUseCases.getObject(id)?.title })
        }
        HaloWorkspaceCard {
            Text(uiText("Suggested memory"),style=MaterialTheme.typography.titleSmall)
            if(editing) OutlinedTextField(content,{content=it.take(MemoryLimits.CONTENT)},label={Text(uiText("Suggested memory"))},modifier=Modifier.fillMaxWidth()) else Text(content)
            Text(uiText("Why this was suggested"), style=MaterialTheme.typography.titleSmall)
            Text(MemoryPresentation.origin(p.origin))
            Text("${p.evidence.size} supporting references · ${if(p.confidence >= .8f) "Strong evidence" else "Suggested"}",style=MaterialTheme.typography.labelSmall)
            Text(scopeLabel, style=MaterialTheme.typography.bodySmall)
            if(conflict?.kind == MemoryConflictKind.CONFLICTS) {
                Text(uiText("This conflicts with something halo already remembers."),style=MaterialTheme.typography.titleSmall)
                conflict?.records?.forEach { old -> Text("Current: ${old.content}") }
                Text("New: $content")
            }
            Row {
                TextButton(onClick={act { graph.personalLearning.dismiss(row.id) }}) { Text(if(conflict?.kind == MemoryConflictKind.CONFLICTS) "Keep current" else "Not now") }
                TextButton(onClick={editing=!editing}) { Text(uiText("Edit")) }
                TextButton(enabled=conflict != null && (conflict?.kind != MemoryConflictKind.CONFLICTS || conflict?.records?.size == 1),onClick={act {
                    graph.personalLearning.acceptMemory(row.id,content,if(conflict?.kind == MemoryConflictKind.CONFLICTS) conflict?.records?.singleOrNull()?.id else null)
                }}) { Text(if(conflict?.kind == MemoryConflictKind.CONFLICTS) "Use new" else "Remember") }
            }
            var more by remember(row.id) { mutableStateOf(false) }
            TextButton({ more = !more }) { Text(uiText("More")) }
            if (more) {
                TextButton(onClick={act { graph.personalLearning.dismiss(row.id,true) }}) { Text(uiText("Don't suggest this again")) }
                Text(uiText("Suppresses this same suggestion fingerprint; unrelated suggestions remain possible."), style=MaterialTheme.typography.bodySmall)
            }
        }
    } else {
        val suggestion=remember(row.json) { runCatching { PersonalMemoryCodec.decodeSuggestion(row.json) }.getOrNull() } ?: return
        if (MemorySensitivityClassifier.classify(suggestion.content) == MemorySensitivity.SECRET) return
        var name by remember(row.id) { mutableStateOf("Repeated workflow") }
        var description by remember(row.id) { mutableStateOf("A workflow reviewed and approved by me") }
        HaloWorkspaceCard {
            Text("${when (row.kind) { "PROCEDURE" -> "Procedure"; "AUTOMATION" -> "Automation"; else -> "Capability setup" }} · ${suggestion.title}",style=MaterialTheme.typography.titleSmall)
            Text(suggestion.content)
            Text("${suggestion.evidenceCount} recent occurrences",style=MaterialTheme.typography.labelSmall)
            if(row.kind == "PROCEDURE") {
                suggestion.tools.forEachIndexed { i,tool -> Text("${i+1}. ${graph.toolRegistry.descriptor(tool)?.presentation?.title ?: tool}") }
                if(editing) {
                    OutlinedTextField(name,{name=it.take(80)},label={Text(uiText("Procedure name"))})
                    OutlinedTextField(description,{description=it.take(400)},label={Text(uiText("What this procedure does"))})
                    Text(uiText("No private arguments are copied. Execution still needs the usual permissions and approvals."))
                }
                TextButton(onClick={if(!editing) editing=true else act { graph.personalLearning.saveProcedure(row.id,name,description,graph.learnedProcedureStore) { graph.toolRegistry.contains(it) } }}) { Text(if(editing) "Save reviewed procedure" else "Review procedure") }
            } else if(row.kind == "AUTOMATION") TextButton(onClick={suggestion.targetId?.let { viewModel.reviewAutomation(it,row.id) }}) { Text(uiText("Review trigger and permission")) }
            else TextButton(onClick={viewModel.reviewCapabilities()}) { Text(uiText("Review capability setup")) }
            Row {
                TextButton(onClick={act { graph.personalLearning.dismiss(row.id) }}) { Text(uiText("Not now")) }
                var more by remember(row.id) { mutableStateOf(false) }
            TextButton({ more = !more }) { Text(uiText("More")) }
            if (more) {
                TextButton(onClick={act { graph.personalLearning.dismiss(row.id,true) }}) { Text(uiText("Don't suggest this again")) }
                Text(uiText("Suppresses this same suggestion fingerprint; unrelated suggestions remain possible."), style=MaterialTheme.typography.bodySmall)
            }
            }
        }
    }
}
