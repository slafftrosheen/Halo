package com.halo.app.ui.screens.study

import com.halo.app.language.uiText
import com.halo.app.R
import androidx.compose.ui.res.stringResource
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
    val dueBySession = due.groupBy { it.sessionId }
    val busy by model.busy.collectAsStateWithLifecycle()
    val notice by model.notice.collectAsStateWithLifecycle()
    val selected by model.selectedIds.collectAsStateWithLifecycle()
    val candidates by model.candidates.collectAsStateWithLifecycle()
    val previews by model.previews.collectAsStateWithLifecycle()

    var picker by remember { mutableStateOf(false) }
    var setupStep by rememberSaveable { mutableIntStateOf(0) }
    var newLesson by rememberSaveable { mutableStateOf(false) }
    var topic by rememberSaveable { mutableStateOf("") }
    var assignment by rememberSaveable { mutableStateOf("") }
    var language by rememberSaveable { mutableStateOf("") }
    var strongest by rememberSaveable { mutableStateOf("") }
    var optionsExpanded by rememberSaveable { mutableStateOf(false) }
    var showAllSessions by rememberSaveable { mutableStateOf(false) }
    var showAllDue by rememberSaveable { mutableStateOf(false) }
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
    val hasLearningInput = topic.isNotBlank() || selected.isNotEmpty()
    val canStart = !busy && hasLearningInput
    val startLesson = {
        model.start(topic, language, assignment, strongest, level, style,
            if (selected.isEmpty()) StudyKnowledgePolicy.SOURCE_PLUS_GENERAL else policy)
    }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = 720.dp).fillMaxSize().imePadding()) {
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            item(key = "header") {
                HaloScreenHeader(
                    uiText("Study"),
                    uiText("Choose a topic, add material, and learn at your pace."),
                    glyph = HaloGlyph.QUESTION,
                )
            }

            if (due.isNotEmpty() && !newLesson) item(key = "review_due") {
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = com.halo.app.ui.design.HaloShapes.hero,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(uiText("Ready to remember"), style = MaterialTheme.typography.titleMedium)
                        Text(uiText("Ready to review: %1\$d", due.size),
                            color = MaterialTheme.colorScheme.onSecondaryContainer)
                        if (dueBySession.size == 1) {
                            StudyFocusAction(
                                text = "Review cards",
                                glyph = HaloGlyph.MEMORY,
                                enabled = !busy,
                                onClick = { onReview(due.first().sessionId) },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        } else {
                            Text(uiText("Choose a lesson to review"),
                                style = MaterialTheme.typography.bodyMedium)
                            val groups = if (showAllDue) dueBySession.entries.toList()
                                else dueBySession.entries.take(3)
                            groups.forEach { (sessionId, cards) ->
                                OutlinedButton(
                                    onClick = { onReview(sessionId) },
                                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                                    enabled = !busy,
                                ) {
                                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text(sessions.firstOrNull { it.id == sessionId }?.goal
                                            ?.takeIf { it.isNotBlank() } ?: uiText("Study from a note"),
                                            modifier = Modifier.weight(1f), maxLines = 1)
                                        Text(cards.size.toString())
                                    }
                                }
                            }
                            if (dueBySession.size > 3) {
                                TextButton(onClick = { showAllDue = !showAllDue }) {
                                    Text(uiText(if (showAllDue) "Show fewer lessons" else "Show all lessons"))
                                }
                            }
                        }
                    }
                }
            }

            if (sessions.isNotEmpty() && !newLesson) {
                item(key = "resume_title") {
                    Text(uiText("Pick up where you left off"),
                        style = MaterialTheme.typography.titleLarge)
                }
                items(if (showAllSessions) sessions else sessions.take(2), key = { "session:${it.id}" }) { session ->
                    Surface(
                        onClick = { onResume(session.id) },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 76.dp),
                        shape = com.halo.app.ui.design.HaloShapes.card,
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    ) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(session.goal?.takeIf { it.isNotBlank() } ?: uiText("Study from a note"),
                                style = MaterialTheme.typography.titleMedium, maxLines = 2)
                            summaries[session.id]?.let {
                                Text(uiText("Sources: %1\$d · Cards: %2\$d · Saved replies: %3\$d",
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

            item(key = "new_lesson_entry") {
                if (sessions.isNotEmpty()) OutlinedButton(onClick = { newLesson = !newLesson }, modifier = Modifier.fillMaxWidth()) {
                    Text(uiText(if (newLesson) "Back to lessons" else "Start a new lesson"))
                }
                if (newLesson || sessions.isEmpty()) Column(
                    Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    LinearProgressIndicator(
                        progress = { (setupStep + 1) / 4f },
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceContainerHigh
                    )
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("Goal", "Material", "Style", "Options").forEachIndexed { index, title ->
                            FilterChip(
                                selected = setupStep == index,
                                enabled = studySetupCanOpenStep(setupStep, index, hasLearningInput, busy),
                                onClick = { setupStep = index },
                                label = { Text("${index + 1} · " + uiText(title)) }
                            )
                        }
                    }
                }
            }
            if ((newLesson || sessions.isEmpty()) && setupStep == 0) {
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
                                Column {
                                    Text(uiText("For example, explain fractions, practise spelling, or solve a problem together."))
                                    Text(stringResource(R.string.brand_study_material_first))
                                }
                            },
                            enabled = !busy,
                        )
                    }
                }
            }

            }
            if ((newLesson || sessions.isEmpty()) && setupStep == 1) {
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
                            shape = com.halo.app.ui.design.HaloShapes.control, modifier = Modifier.fillMaxWidth()) {
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

            }
            if ((newLesson || sessions.isEmpty()) && setupStep == 2) {
            item(key = "learning_style") {
                StudyHomeStep(3, "How should Halo help?") {
                    Text(uiText("Choose a learning style. You can change this during the lesson."),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    StudySupportStyle.entries.forEach { choice ->
                        val chosen = style == choice
                        Surface(onClick = { style = choice }, enabled = !busy,
                            shape = com.halo.app.ui.design.HaloShapes.card,
                            color = if (chosen) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                            contentColor = if (chosen) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                            border = BorderStroke(1.dp, if (chosen) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant)) {
                            Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(selected = chosen, onClick = null,
                                    colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colorScheme.onPrimaryContainer))
                                Column(Modifier.weight(1f).padding(start = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(uiText(when (choice) {
                                        StudySupportStyle.GUIDED -> "Guide me"
                                        StudySupportStyle.PRACTICE -> "Let me practise"
                                        StudySupportStyle.EXPLAIN -> "Explain it"
                                    }), style = MaterialTheme.typography.titleSmall)
                                    Text(uiText(when (choice) {
                                        StudySupportStyle.GUIDED -> "Halo gives hints in small steps. You keep trying."
                                        StudySupportStyle.PRACTICE -> "You answer first, then review what needs work."
                                        StudySupportStyle.EXPLAIN -> "Halo explains the idea, then checks your understanding."
                                    }), style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }
            }
            }
            if ((newLesson || sessions.isEmpty()) && setupStep == 3) {
            item(key = "learning_options") {
                StudyHomeStep(4, "Learning options") {
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
                    notice?.let { Text(uiText(it), color = MaterialTheme.colorScheme.error) }
                }
            }

            }
            item(key = "workspace") {
                TextButton(onClick = onNotebook) { StudyActionLabel(HaloGlyph.NOTE, "Open Workspace") }
            }
        }
        if (newLesson || sessions.isEmpty()) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (!hasLearningInput && !busy) {
                Text(
                    stringResource(if (setupStep == 0) R.string.brand_study_goal_or_material
                        else R.string.brand_study_missing_input),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (setupStep > 0) TextButton(onClick = { setupStep-- }, enabled = !busy) { Text(uiText("Back")) }
            StudyFocusAction(
                text = if (setupStep < 3) "Continue" else if (busy) "Starting..." else "Start lesson",
                glyph = HaloGlyph.PLAY,
                enabled = studySetupCanContinue(setupStep, hasLearningInput, busy),
                onClick = { if (setupStep < 3) setupStep++ else startLesson() },
                modifier = Modifier.fillMaxWidth(),
                active = canStart && due.isEmpty(),
            )
        }
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
                shape = com.halo.app.ui.design.HaloShapes.control) {
                Box(Modifier.size(36.dp), contentAlignment = Alignment.Center) {
                    Text(number.toString(), color = MaterialTheme.colorScheme.onPrimaryContainer,
                        style = MaterialTheme.typography.titleMedium)
                }
            }
            Text(uiText(title), style = MaterialTheme.typography.titleMedium)
        }
        Surface(shape = com.halo.app.ui.design.HaloShapes.card,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
        }
    }
}

/**
 * Goal and material are alternative starting inputs: an empty goal may advance to
 * the material picker, but it cannot jump past that step or start an empty lesson.
 */
internal fun studySetupCanContinue(step: Int, hasLearningInput: Boolean, busy: Boolean): Boolean =
    step in 0..3 && !busy && (step == 0 || hasLearningInput)

/** Prevent the horizontal stepper from bypassing source/goal validation. */
internal fun studySetupCanOpenStep(
    currentStep: Int,
    requestedStep: Int,
    hasLearningInput: Boolean,
    busy: Boolean,
): Boolean = !busy && requestedStep in 0..3 && (
    requestedStep <= currentStep ||
        (requestedStep == currentStep + 1 && studySetupCanContinue(currentStep, hasLearningInput, false))
    )
