package com.halo.app.ui.screens.study

import com.halo.app.language.uiText

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import com.halo.app.ui.icons.HaloGlyph
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.halo.app.ui.design.HaloScreenHeader
import com.halo.sdk.runtime.study.*
import com.halo.app.study.*
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts

@Composable
fun StudyHomeScreen(model: StudyHomeViewModel, onResume: (String) -> Unit, onReview: (String) -> Unit, onNotebook: () -> Unit) {
    val sessions by model.sessions.collectAsStateWithLifecycle()
    val summaries by model.summaries.collectAsStateWithLifecycle()
    val due by model.due.collectAsStateWithLifecycle()
    val busy by model.busy.collectAsStateWithLifecycle()
    val notice by model.notice.collectAsStateWithLifecycle()
    val selected by model.selectedIds.collectAsStateWithLifecycle()
    val candidates by model.candidates.collectAsStateWithLifecycle()
    val previews by model.previews.collectAsStateWithLifecycle()
    var picker by remember { mutableStateOf(false) }
    var assignment by rememberSaveable { mutableStateOf("") }
    var strongest by rememberSaveable { mutableStateOf("") }
    var level by remember { mutableStateOf(StudyLearnerLevel.ADULT) }
    var style by remember { mutableStateOf(StudySupportStyle.GUIDED) }
    var policy by remember { mutableStateOf(StudyKnowledgePolicy.SOURCE_ONLY) }
    val importFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(model::importMaterial) }
    if (picker) com.halo.app.ui.workspace.WorkspaceObjectPicker(candidates.filter { it.id.value !in selected },
        onPick = { model.select(it.id.value); picker = false }, onDismiss = { picker = false })
    var topic by rememberSaveable { mutableStateOf("") }
    var language by rememberSaveable { mutableStateOf("") }
    var preferences by rememberSaveable { mutableStateOf(false) }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
    LazyColumn(Modifier.widthIn(max = 720.dp).fillMaxSize().imePadding(), contentPadding = PaddingValues(24.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
        item { HaloScreenHeader(uiText("Study"), "A little curiosity goes a long way.") }
        item {
            Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.secondaryContainer) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    StudyActionLabel(HaloGlyph.SEARCH, "Explore a question")
                    StudyActionLabel(HaloGlyph.THINK, "Try it yourself, then ask for a hint")
                    StudyActionLabel(HaloGlyph.MEMORY, "Practice what you discover")
                    Text(uiText("Halo helps you learn. Your ideas and attempts lead the way."), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(uiText("Your study material"), style = MaterialTheme.typography.titleMedium)
                com.halo.app.study.ExtractionImportButton(com.halo.app.di.ManualDependencyGraph.extractionReview)
                Text(uiText("Add a photo or file, choose saved material, or type a topic below."))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton({ picker = true }, enabled = !busy && selected.size < 4) { StudyActionLabel(HaloGlyph.STORAGE, "Choose saved material") }
                    OutlinedButton({ importFile.launch(arrayOf("text/*", "application/json", "image/*", "application/pdf", StudyTextExtractor.DOCX)) }, enabled = !busy && selected.size < 4) { StudyActionLabel(HaloGlyph.FILE, "Add a file") }
                }
                selected.forEach { id ->
                    val source = previews.firstOrNull { it.objectId == id }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(source?.title ?: "Loading material…")
                            Text(when (source) {
                                is ResolvedStudySource.Text -> if(source.coverage == SourceCoverage.FULL) "Text ready" else "Part of the text is ready"
                                is ResolvedStudySource.Pdf -> "${source.pageCount} pages: read the text first, or use image AI"
                                is ResolvedStudySource.Visual -> "Image · vision required"
                                is ResolvedStudySource.Unsupported -> source.reason
                                null -> "Checking access"
                            }, style = MaterialTheme.typography.bodySmall)
                        }
                        TextButton({ model.remove(id) }, enabled = !busy) { Text(uiText("Remove")) }
                    }
                }
                Text(uiText(if (selected.isEmpty()) "What would you like to learn?" else "Question or goal (optional)"), style = MaterialTheme.typography.headlineSmall)
                OutlinedTextField(topic, { topic = it.take(400) }, modifier = Modifier.fillMaxWidth(), minLines = 2,
                    placeholder = { Text(uiText("A topic, a question, or an exercise...")) }, enabled = !busy)
                if (preferences) {
                    OutlinedTextField(assignment, { assignment = it.take(80) }, label = { Text(uiText("Material language (optional)")) }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(language, { language = it.take(80) }, label = { Text(uiText("Explain in this language (blank = app language)")) }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(strongest, { strongest = it.take(80) }, label = { Text(uiText("Language I know best (optional)")) }, modifier = Modifier.fillMaxWidth())
                    StudyLearningSetup(level, style, policy) { l, s, p -> level = l; style = s; policy = p }
                }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { model.start(topic, language, assignment, strongest, level, style, policy) }, enabled = (topic.isNotBlank() || selected.isNotEmpty()) && !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { StudyActionLabel(HaloGlyph.THINK, if (busy) "Starting..." else "Let's learn") }
                    TextButton(onClick = { preferences = !preferences }) { StudyActionLabel(HaloGlyph.SETTINGS, "Learning setup") }
                }
                Text(uiText("Explanation level: %1\$s. Change it in Learning setup.", uiText(level.friendlyLabel())),style=MaterialTheme.typography.bodySmall)
                TextButton(onClick = onNotebook) { StudyActionLabel(HaloGlyph.NOTE, "Open Workspace") }
                notice?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        }
        if (due.isNotEmpty()) item {
            Text(uiText("Ready to remember"), style = MaterialTheme.typography.titleMedium)
            Text(uiText("%1\$d cards are ready for another look.", due.size), color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick = { onReview(due.first().sessionId) }) { StudyActionLabel(HaloGlyph.MEMORY, "Review cards") }
        }
        if (sessions.isNotEmpty()) item { Text(uiText("Pick up where you left off"), style = MaterialTheme.typography.titleMedium) }
        items(sessions, key = { it.id }) { session ->
            Surface(onClick = { onResume(session.id) }, modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerLow) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(session.goal ?: "Study from a note", style = MaterialTheme.typography.titleMedium, maxLines = 2)
                    Text(uiText("Continue learning"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    summaries[session.id]?.let { Text(uiText("%1\$d sources · %2\$d cards · %3\$d saved replies", it.sources, it.cards, it.replies),style=MaterialTheme.typography.bodySmall) }
                }
            }
        }
    }
    }
}
