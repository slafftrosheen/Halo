package com.halo.app.ui.screens.study

import com.halo.app.language.uiText
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.halo.app.ui.design.HaloScreenHeader
import com.halo.app.ui.icons.HaloGlyph
import com.halo.sdk.runtime.study.*
import com.halo.app.study.*

/**
 * Study starts from a learning goal, not a file manager.
 * Every step has a visible purpose; material and advanced preferences are optional.
 */
@Composable
fun StudyHomeScreen(
    model: StudyHomeViewModel,
    onResume: (String) -> Unit,
    onReview: (String) -> Unit,
    onNotebook: () -> Unit,
) {
    val sessions by model.sessions.collectAsStateWithLifecycle()
    val summaries by model.summaries.collectAsStateWithLifecycle()
    val due by model.due.collectAsStateWithLifecycle()
    val busy by model.busy.collectAsStateWithLifecycle()
    val notice by model.notice.collectAsStateWithLifecycle()
    val selected by model.selectedIds.collectAsStateWithLifecycle()
    val candidates by model.candidates.collectAsStateWithLifecycle()
    val previews by model.previews.collectAsStateWithLifecycle()

    var picker by remember { mutableStateOf(false) }
    var topic by rememberSaveable { mutableStateOf("") }
    var assignment by rememberSaveable { mutableStateOf("") }
    var language by rememberSaveable { mutableStateOf("") }
    var strongest by rememberSaveable { mutableStateOf("") }
    var optionsExpanded by rememberSaveable { mutableStateOf(false) }
    var showAllSessions by rememberSaveable { mutableStateOf(false) }
    var level by remember { mutableStateOf(StudyLearnerLevel.ADULT) }
    var style by remember { mutableStateOf(StudySupportStyle.GUIDED) }
    var policy by remember { mutableStateOf(StudyKnowledgePolicy.SOURCE_ONLY) }
    val importFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {
        uri -> uri?.let(model::importMaterial)
    }
    if (picker) {
        com.halo.app.ui.workspace.WorkspaceObjectPicker(
            candidates.filter { it.id.value !in selected },
            onPick = { model.select(it.id.value); picker = false },
            onDismiss = { picker = false },
        )
    }
    val canStart = !busy && (topic.isNotBlank() || selected.isNotEmpty())
    val startLesson = {
        model.start(topic, language, assignment, strongest, level, style,
            if (selected.isEmpty()) StudyKnowledgePolicy.SOURCE_PLUS_GENERAL else policy)
    }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            Modifier.widthIn(max = 720.dp).fillMaxSize().imePadding(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            item(key = "header") {
                HaloScreenHeader(
                    uiText("Study"),
                    uiText("Choose a topic, add material, and learn at your pace."),
                    glyph = HaloGlyph.QUESTION,
                )
            }

            if (due.isNotEmpty()) item(key = "review_due") {
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(uiText("Ready to remember"), style = MaterialTheme.typography.titleMedium)
                        Text(uiText("%1\$d cards are ready for another look.", due.size),
                            color = MaterialTheme.colorScheme.onSecondaryContainer)
                        Button(onClick = { onReview(due.first().sessionId) }) {
                            StudyActionLabel(HaloGlyph.MEMORY, "Review cards")
                        }
                    }
                }
            }

            if (sessions.isNotEmpty()) {
                item(key = "resume_title") {
                    Text(uiText("Pick up where you left off"),
                        style = MaterialTheme.typography.titleLarge)
                }
                items(if (showAllSessions) sessions else sessions.take(2), key = { "session:${it.id}" }) { session ->
                    Surface(
                        onClick = { onResume(session.id) },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 76.dp),
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    ) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(session.goal?.takeIf { it.isNotBlank() } ?: uiText("Study from a note"),
                                style = MaterialTheme.typography.titleMedium, maxLines = 2)
                            summaries[session.id]?.let {
                                Text(uiText("%1\$d sources · %2\$d cards · %3\$d saved replies",
                                    it.sources, it.cards, it.replies),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text(uiText("Continue learning"), style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
                if (sessions.size > 2) item(key = "all_sessions") {
                    TextButton(onClick = { showAllSessions = !showAllSessions }) {
                        Text(uiText(if (showAllSessions) "Show fewer lessons" else "Show all lessons"))
                    }
                }
            }

            item(key = "new_lesson") {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text(uiText("Start a new lesson"), style = MaterialTheme.typography.titleLarge)
                    Text(uiText("Halo guides you through understanding, trying, and reviewing. Your answers lead the lesson."),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    StudyHomeStep(1, "Your learning goal") {
                        OutlinedTextField(
                            value = topic,
                            onValueChange = { topic = it.take(400) },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 2,
                            label = { Text(uiText("What would you like to learn?")) },
                            placeholder = { Text(uiText("A topic, a question, or an exercise...")) },
                            supportingText = {
                                Text(uiText("For example, explain fractions, practise spelling, or solve a problem together."))
                            },
                            enabled = !busy,
                        )
                    }
                }
            }

            item(key = "material") {
                StudyHomeStep(2, "Add material (optional)") {
                    Text(uiText("You can start with just a topic. Add a file or note if you want to work from your own material."),
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    com.halo.app.study.ExtractionImportButton(com.halo.app.di.ManualDependencyGraph.extractionReview)
                    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { picker = true },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                            enabled = !busy && selected.size < StudySourceLimits.MAX_SOURCES,
                        ) { StudyActionLabel(HaloGlyph.STORAGE, "Saved material") }
                        OutlinedButton(
                            onClick = { importFile.launch(arrayOf("text/*", "application/json", "image/*", "application/pdf", StudyTextExtractor.DOCX)) },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                            enabled = !busy && selected.size < StudySourceLimits.MAX_SOURCES,
                        ) { StudyActionLabel(HaloGlyph.FILE, "Add a file") }
                    }
                    selected.forEach { id ->
                        val source = previews.firstOrNull { it.objectId == id }
                        Surface(color = MaterialTheme.colorScheme.surfaceContainerLow,
                            shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                            Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(source?.title ?: uiText("Loading material…"), style = MaterialTheme.typography.titleSmall)
                                    Text(when (source) {
                                        is ResolvedStudySource.Text -> if (source.coverage == SourceCoverage.FULL) uiText("Text ready") else uiText("Part of the text is ready")
                                        is ResolvedStudySource.Pdf -> uiText("PDF pages may need image AI")
                                        is ResolvedStudySource.Visual -> uiText("Image · vision required")
                                        is ResolvedStudySource.Unsupported -> source.reason
                                        null -> uiText("Checking access")
                                    }, style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                TextButton(onClick = { model.remove(id) }, enabled = !busy) { Text(uiText("Remove")) }
                            }
                        }
                    }
                }
            }

            item(key = "learning_style") {
                StudyHomeStep(3, "How should Halo help?") {
                    Text(uiText("Choose a learning style. You can change this during the lesson."),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StudySupportStyle.entries.forEach { choice ->
                            FilterChip(
                                selected = style == choice,
                                onClick = { style = choice },
                                label = { Text(uiText(when (choice) {
                                    StudySupportStyle.GUIDED -> "Guide me"
                                    StudySupportStyle.PRACTICE -> "Let me practise"
                                    StudySupportStyle.EXPLAIN -> "Explain it"
                                })) },
                            )
                        }
                    }
                    OutlinedButton(
                        onClick = { optionsExpanded = !optionsExpanded },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    ) {
                        StudyActionLabel(HaloGlyph.SETTINGS,
                            if (optionsExpanded) "Hide learning options" else "Age, language & learning options")
                    }
                    if (optionsExpanded) {
                        StudyLearningSetup(
                            level, style, policy,
                            onChange = { l, s, p -> level = l; style = s; policy = p },
                            showStyle = false, showKnowledgePolicy = selected.isNotEmpty(),
                        )
                        OutlinedTextField(assignment, { assignment = it.take(80) },
                            label = { Text(uiText("Material language (optional)")) }, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(language, { language = it.take(80) },
                            label = { Text(uiText("Explain in this language (blank = app language)")) },
                            modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(strongest, { strongest = it.take(80) },
                            label = { Text(uiText("Language I know best (optional)")) },
                            modifier = Modifier.fillMaxWidth())
                    } else {
                        Text(uiText("Explanation level: %1\$s. Change it in Learning setup.", uiText(level.friendlyLabel())),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Button(onClick = startLesson, enabled = canStart,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                        StudyActionLabel(HaloGlyph.PLAY, if (busy) "Starting..." else "Start lesson")
                    }
                    if (!canStart && !busy) {
                        Text(uiText("Enter a learning goal or choose material to begin."),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    notice?.let { Text(uiText(it), color = MaterialTheme.colorScheme.error) }
                }
            }

            item(key = "workspace") {
                TextButton(onClick = onNotebook) { StudyActionLabel(HaloGlyph.NOTE, "Open Workspace") }
            }
        }
    }
}

@Composable
private fun StudyHomeStep(number: Int, title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Surface(color = MaterialTheme.colorScheme.primaryContainer,
                shape = RoundedCornerShape(12.dp)) {
                Box(Modifier.size(36.dp), contentAlignment = Alignment.Center) {
                    Text(number.toString(), color = MaterialTheme.colorScheme.onPrimaryContainer,
                        style = MaterialTheme.typography.titleMedium)
                }
            }
            Text(uiText(title), style = MaterialTheme.typography.titleMedium)
        }
        Surface(shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
        }
    }
}
