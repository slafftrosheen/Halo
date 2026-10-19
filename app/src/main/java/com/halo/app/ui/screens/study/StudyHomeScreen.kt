package com.halo.app.ui.screens.study

import com.halo.app.ui.components.HaloTextField
import com.halo.app.ui.components.VoiceFieldKind
import com.halo.app.language.uiText
import com.halo.app.R
import androidx.compose.ui.res.stringResource
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material.icons.automirrored.filled.ArrowForward
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
    initialSourceId: String? = null,
    initialGoal: String? = null,
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
    var executionChoice by rememberSaveable { mutableStateOf<String?>(null) }
    var setupStep by rememberSaveable { mutableIntStateOf(0) }
    // A source/passage arriving from Notebook or capture enters the wizard,
    // not the existing-lesson home. Model selection is populated by the host.
    var newLesson by rememberSaveable(initialSourceId, initialGoal) {
        mutableStateOf(initialSourceId != null || initialGoal != null)
    }
    var topic by rememberSaveable(initialSourceId, initialGoal) {
        mutableStateOf(initialGoal.orEmpty().take(400))
    }
    var assignment by rememberSaveable { mutableStateOf("") }
    var language by rememberSaveable { mutableStateOf("") }
    var strongest by rememberSaveable { mutableStateOf("") }
    var optionsExpanded by rememberSaveable { mutableStateOf(false) }
    var materialOptionsExpanded by rememberSaveable { mutableStateOf(false) }
    var showAllSessions by rememberSaveable { mutableStateOf(false) }
    var showAllDue by rememberSaveable { mutableStateOf(false) }
    // These choices are part of the learner's unfinished setup. Rotation and
    // background recreation must not silently reset age/style/source policy.
    var level by rememberSaveable { mutableStateOf(StudyLearnerLevel.ADULT) }
    var style by rememberSaveable { mutableStateOf(StudySupportStyle.GUIDED) }
    var policy by rememberSaveable { mutableStateOf(StudyKnowledgePolicy.SOURCE_ONLY) }
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
    val setupActive = newLesson || sessions.isEmpty()
    val setupLabels = listOf("Goal", "Material", "Style", "Options")
    val startLesson = {
        model.start(topic, language, assignment, strongest, level, style,
            studyKnowledgePolicyForSources(selected.isNotEmpty(), policy), executionChoice = executionChoice)
    }
    val returnFromSetup: () -> Unit = {
        when {
            busy -> model.cancelStart()
            setupStep > 0 -> setupStep--
            sessions.isNotEmpty() -> {
                model.cancelStart()
                newLesson = false
            }
            else -> {
                model.cancelStart()
                onNotebook()
            }
        }
    }
    // S01/S02: system Back mirrors the visible wizard Back. Leaving the
    // first step cancels only unfinished setup; a saved lesson is untouched.
    BackHandler(enabled = setupActive, onBack = returnFromSetup)

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = 720.dp).fillMaxSize().imePadding()) {
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            item(key = "header") {
                HaloScreenHeader(
                    title = uiText(if (setupActive) "New lesson" else "Study"),
                    subtitle = if (setupActive)
                        uiText("Step %1\$d of 4 · %2\$s", setupStep + 1,
                            uiText(setupLabels[setupStep.coerceIn(0, 3)]))
                    else uiText("Choose a topic, add material, and learn at your pace."),
                    navigation = if (setupActive) {
                        returnFromSetup
                    } else null,
                    glyph = if (setupActive) null else HaloGlyph.QUESTION,
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
                                guide = sessions.isNotEmpty(),
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
                if (sessions.isNotEmpty() && !newLesson) OutlinedButton(
                    onClick = {
                        // S02: each new lesson starts at Goal with NO inherited cloud choice.
                        // Reset the old material only after explicit New lesson, not when
                        // launched with a Notebook source already selected.
                        model.resetDraft()
                        topic = ""
                        assignment = ""
                        language = ""
                        strongest = ""
                        level = StudyLearnerLevel.ADULT
                        style = StudySupportStyle.GUIDED
                        policy = StudyKnowledgePolicy.SOURCE_ONLY
                        setupStep = 0
                        executionChoice = null
                        newLesson = true
                    },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Text(uiText("Start a new lesson"))
                }
                if (newLesson || sessions.isEmpty()) Column(
                    Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    StudySetupProgress(
                        currentStep = setupStep,
                        hasLearningInput = hasLearningInput,
                        busy = busy,
                        onSelectStep = { setupStep = it }
                    )
                }
            }
            if ((newLesson || sessions.isEmpty()) && setupStep == 0) {
            item(key = "new_lesson") {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text(uiText("Start a new lesson"), style = MaterialTheme.typography.titleLarge)
                    Text(uiText("halo guides you through understanding, trying, and reviewing. Your answers lead the lesson."),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    StudyHomeStep(1, "Your learning goal") {
                        HaloTextField(
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
                            enabled = !busy, kind = VoiceFieldKind.TEXT, maxChars = 400)
                    }
                }
            }

            }
            if ((newLesson || sessions.isEmpty()) && setupStep == 1) {
            item(key = "material") {
                StudyHomeStep(2, "What should I learn from?", bare = true) {
                    Text(uiText("You can start with just a topic. Add a file or note if you want to work from your own material."),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        // The OCR action opens the existing local extraction-and-review workflow;
                        // it does not claim to launch the camera directly.
                        com.halo.app.study.ExtractionImportButton(
                            com.halo.app.di.ManualDependencyGraph.extractionReview,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp),
                            title = "Read a photo or page",
                            subtitle = "Choose a saved photo or document. Text is read on this phone.",
                            enabled = !busy,
                        )
                        StudyMaterialOption(
                            "From your Notebook", "Notes, canvases and chats", HaloGlyph.STORAGE,
                            enabled = !busy && selected.size < StudySourceLimits.MAX_SOURCES,
                        ) { picker = true }
                        StudyMaterialOption(
                            "Add a file", "PDF, document or image", HaloGlyph.FILE,
                            enabled = !busy && selected.size < StudySourceLimits.MAX_SOURCES,
                        ) {
                            importFile.launch(arrayOf("text/*", "application/json", "image/*",
                                "application/pdf", StudyTextExtractor.DOCX))
                        }
                        StudyMaterialOption(
                            "Change age, languages and learning style",
                            "Choose learning preferences", HaloGlyph.SETTINGS,
                            enabled = !busy, selected = materialOptionsExpanded,
                        ) { materialOptionsExpanded = !materialOptionsExpanded }
                        if (materialOptionsExpanded) {
                            Surface(shape = com.halo.app.ui.design.HaloShapes.card,
                                color = MaterialTheme.colorScheme.surfaceContainerLow,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
                                Column(Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    StudyLearningSetup(level, style, policy,
                                        onChange = { l, s, p -> level = l; style = s; policy = p },
                                        showKnowledgePolicy = selected.isNotEmpty())
                                    HaloTextField(assignment, { assignment = it.take(80) },
                                        label = { Text(uiText("Material language (optional)")) },
                                        modifier = Modifier.fillMaxWidth(), kind = VoiceFieldKind.SHORT, maxChars = 80)
                                    HaloTextField(language, { language = it.take(80) },
                                        label = { Text(uiText("Explain in this language (blank = app language)")) },
                                        modifier = Modifier.fillMaxWidth(), kind = VoiceFieldKind.SHORT, maxChars = 80)
                                    HaloTextField(strongest, { strongest = it.take(80) },
                                        label = { Text(uiText("Language I know best (optional)")) },
                                        modifier = Modifier.fillMaxWidth(), kind = VoiceFieldKind.SHORT, maxChars = 80)
                                }
                            }
                        }
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
                                        is ResolvedStudySource.Pdf -> uiText("Some pages need halo to read photos")
                                        is ResolvedStudySource.Visual -> uiText("halo needs permission to read this photo")
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
                StudyHomeStep(3, "How should halo help?") {
                    Text(uiText("Choose a learning style. You can change this during the lesson."),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    StudySupportStyle.entries.forEach { choice ->
                        StudySupportOption(
                            choice = choice,
                            selected = style == choice,
                            enabled = !busy,
                            onClick = { style = choice },
                        )
                    }
                }
            }
            }
            if ((newLesson || sessions.isEmpty()) && setupStep == 3) {
            item(key = "learning_options") {
                StudyHomeStep(4, "Learning options") {
                    Text(uiText("Where should halo think?"), style = MaterialTheme.typography.titleMedium)
                    Text(uiText("Choose for this lesson. Neither option is selected for you."), style = MaterialTheme.typography.bodyMedium)
                    listOf("phone" to "On this phone", "cloud" to "With cloud help").forEach { (choice, label) ->
                        OutlinedButton(onClick = { executionChoice = choice },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp), enabled = !busy,
                            border = BorderStroke(if (executionChoice == choice) 2.dp else 1.dp,
                                if (executionChoice == choice) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant)) {
                            Text(uiText(label))
                            if (executionChoice == choice) { Spacer(Modifier.width(8.dp)); Text("✓") }
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
                        HaloTextField(assignment, { assignment = it.take(80) },
                            label = { Text(uiText("Material language (optional)")) }, modifier = Modifier.fillMaxWidth(), kind = VoiceFieldKind.SHORT, maxChars = 80)
                        HaloTextField(language, { language = it.take(80) },
                            label = { Text(uiText("Explain in this language (blank = app language)")) },
                            modifier = Modifier.fillMaxWidth(), kind = VoiceFieldKind.SHORT, maxChars = 80)
                        HaloTextField(strongest, { strongest = it.take(80) },
                            label = { Text(uiText("Language I know best (optional)")) },
                            modifier = Modifier.fillMaxWidth(), kind = VoiceFieldKind.SHORT, maxChars = 80)
                    } else {
                        Text(uiText("Explanation level: %1\$s. Change it in Learning setup.", uiText(level.friendlyLabel())),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(
                        uiText(if (selected.isEmpty())
                            "No material attached. halo will use general knowledge."
                        else "Selected material will follow your chosen source policy."),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    notice?.let { Text(uiText(it), color = MaterialTheme.colorScheme.error) }
                }
            }

            }
            item(key = "workspace") {
                TextButton(onClick = { model.cancelStart(); onNotebook() }) { StudyActionLabel(HaloGlyph.NOTE, "Open Workspace") }
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
                enabled = studySetupCanContinue(setupStep, hasLearningInput, busy) && (setupStep < 3 || executionChoice != null),
                onClick = { if (setupStep < 3) setupStep++ else startLesson() },
                modifier = Modifier.fillMaxWidth(),
                active = studySetupCanContinue(setupStep, hasLearningInput, busy) && (setupStep < 3 || executionChoice != null),
                guide = due.isEmpty() || newLesson,
            )
            if (setupStep == 1 && topic.isNotBlank() && selected.isEmpty()) {
                TextButton(
                    onClick = { setupStep = 2 },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                ) { Text(uiText("Skip for now")) }
                Text(uiText("No material attached. halo will use general knowledge."),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        }
        }
    }
}

@Composable
private fun StudyHomeStep(
    number: Int, title: String, bare: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // The material options are standalone cards in Round 2 p.39. Other
        // setup steps still use a contained editor to preserve input affordance.
        Text(uiText(title), style = MaterialTheme.typography.titleLarge)
        if (bare) {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp),
                content = content)
        } else {
            Surface(shape = com.halo.app.ui.design.HaloShapes.card,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp),
                    content = content)
            }
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

/**
 * No selected source can support SOURCE_ONLY. Show and execute the same policy;
 * never imply that general-knowledge answers were grounded in an imported file.
 */
internal fun studyKnowledgePolicyForSources(
    hasSource: Boolean,
    preferred: StudyKnowledgePolicy,
): StudyKnowledgePolicy =
    if (hasSource) preferred else StudyKnowledgePolicy.SOURCE_PLUS_GENERAL

/** Round 2 p.39: thin 4-segment meter with full 48dp hit targets.
 * Advancing the step is still controlled by studySetupCanOpenStep, so tapping
 * ahead cannot bypass a missing learning goal or material source.
 */
@Composable
internal fun StudySetupProgress(
    currentStep: Int,
    hasLearningInput: Boolean,
    busy: Boolean,
    onSelectStep: (Int) -> Unit,
) {
    Row(Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        listOf("Goal", "Material", "Style", "Options").forEachIndexed { index, title ->
            val completedOrCurrent = index <= currentStep
            val stepDescription = uiText(title)
            Surface(
                onClick = { onSelectStep(index) },
                enabled = studySetupCanOpenStep(currentStep, index, hasLearningInput, busy),
                color = androidx.compose.ui.graphics.Color.Transparent,
                modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                    .semantics { contentDescription = stepDescription },
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Box(
                        Modifier.fillMaxWidth().height(4.dp)
                            .background(
                                if (completedOrCurrent) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.surfaceContainerHigh,
                                RoundedCornerShape(4.dp),
                            ),
                    )
                }
            }
        }
    }
}

@Composable
internal fun StudyMaterialOption(
    title: String,
    subtitle: String,
    glyph: HaloGlyph,
    enabled: Boolean = true,
    selected: Boolean = false,
    onClick: () -> Unit,
) {
    Surface(onClick = onClick, enabled = enabled,
        modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp),
        shape = com.halo.app.ui.design.HaloShapes.card,
        color = if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.22f)
            else MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, if (selected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.outlineVariant)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Surface(shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.32f)) {
                Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
                    com.halo.app.ui.icons.HaloGlyphRenderer(glyph, Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.primary)
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(uiText(title), style = MaterialTheme.typography.titleSmall)
                Text(uiText(subtitle), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(androidx.compose.material.icons.Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null, modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/**
 * Brand Book Round 2: the selected learning approach is an accent-outlined card,
 * not an error/permission colour. Selection changes only the learner preference.
 */
@Composable
internal fun StudySupportOption(
    choice: StudySupportStyle,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val label = when (choice) {
        StudySupportStyle.GUIDED -> "Guide me"
        StudySupportStyle.PRACTICE -> "Let me practise"
        StudySupportStyle.EXPLAIN -> "Explain it"
    }
    val description = when (choice) {
        StudySupportStyle.GUIDED -> "halo gives hints in small steps. You keep trying."
        StudySupportStyle.PRACTICE -> "You answer first, then review what needs work."
        StudySupportStyle.EXPLAIN -> "halo explains the idea, then checks your understanding."
    }
    val icon = when (choice) {
        StudySupportStyle.GUIDED -> R.drawable.halo_ic_guide_me
        StudySupportStyle.PRACTICE -> R.drawable.halo_ic_practise
        StudySupportStyle.EXPLAIN -> R.drawable.halo_ic_explain
    }
    val colors = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().heightIn(min = 80.dp),
        shape = com.halo.app.ui.design.HaloShapes.card,
        color = if (selected) colors.primaryContainer.copy(alpha = .22f)
            else colors.surfaceContainerLow,
        border = BorderStroke(if (selected) 2.dp else 1.dp,
            if (selected) colors.primary else colors.outlineVariant),
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Surface(shape = com.halo.app.ui.design.HaloShapes.control,
                color = colors.primaryContainer.copy(alpha = .35f)) {
                Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
                    Icon(androidx.compose.ui.res.painterResource(icon), contentDescription = null,
                        modifier = Modifier.size(24.dp), tint = colors.primary)
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(uiText(label), style = MaterialTheme.typography.titleSmall,
                    color = colors.onSurface)
                Text(uiText(description), style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant)
            }
            if (selected) {
                Icon(androidx.compose.ui.res.painterResource(R.drawable.halo_ic_check),
                    contentDescription = uiText("Selected"),
                    modifier = Modifier.size(22.dp), tint = colors.primary)
            }
        }
    }
}
