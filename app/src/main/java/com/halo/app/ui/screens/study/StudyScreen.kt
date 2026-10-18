package com.halo.app.ui.screens.study

import com.halo.app.ui.components.HaloTextField
import com.halo.app.ui.components.VoiceFieldKind
import com.halo.app.language.uiText
import com.halo.app.ui.components.HaloActionButton
import com.halo.app.ui.components.HaloVoiceTextActions
import com.halo.app.ui.components.HaloActionStyle

import com.halo.app.ui.icons.HaloGlyph
import com.halo.app.ui.icons.HaloIcon

import androidx.lifecycle.compose.collectAsStateWithLifecycle

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.*
import com.halo.app.study.ResolvedStudySource
import com.halo.sdk.runtime.study.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.runtime.key
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.halo.app.data.study.StudyCitation
import com.halo.app.data.study.StudyPracticeItem
import com.halo.app.data.study.StudyVerdict
import com.halo.app.study.MasteryCardCurve
import com.halo.app.study.SourceCoverage
import com.halo.app.study.StudyMasterySnapshot
import com.halo.app.study.StudyReviewIntensity
import com.halo.app.study.StudySourceReference
import com.halo.app.ui.design.HaloScreenHeader
import com.halo.app.ui.design.HaloSpacing
import com.halo.app.ui.presence.HaloSignalGlyph
import com.halo.app.ui.presence.HaloSignalKind
import com.halo.sdk.runtime.study.TutorStage
import com.halo.sdk.runtime.study.StudySessionLifecycle
import com.halo.app.ui.fx.HaloFxAnimation
import com.halo.app.ui.fx.HaloFxMoment
import kotlinx.coroutines.delay

/** Bloom requires a genuinely observed change to a durably completed lesson. */
internal fun shouldCelebrateStudyCompletion(
    previous: StudySessionLifecycle?,
    current: StudySessionLifecycle?,
): Boolean = previous == StudySessionLifecycle.ACTIVE &&
    current == StudySessionLifecycle.COMPLETED

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudyScreen(viewModel: StudyViewModel, onOpenSource: (String) -> Unit = {}, startInReview: Boolean = false, onConfigureVision: () -> Unit = {}, onBack: () -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val attemptStarter = uiText("I think the answer is... because...")
    val questionStarter = uiText("Can you explain this with an example?")
    var showMenu by remember { mutableStateOf(false) }
    var showDetails by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var showCards by rememberSaveable { mutableStateOf(false) }
    var showSaveCard by remember { mutableStateOf(false) }
    var showComposer by rememberSaveable { mutableStateOf(false) }
    var cardQuestion by remember { mutableStateOf("") }
    var focusedReview by rememberSaveable { mutableStateOf(startInReview) }
    var skippedReviewIds by rememberSaveable { mutableStateOf(listOf<String>()) }
    val nextAction = rememberLearningNextAction(state, kids = false,
        blocking = showComposer || showDetails || showMenu || confirmDelete || focusedReview)
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.refreshSources()
            if (event == Lifecycle.Event.ON_PAUSE) viewModel.stopReading("Study no longer visible")
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    // Leaving the screen must never leave the phone talking to an empty room.
    BackHandler(enabled = true) {
        viewModel.stopReading("Left the Study screen")
        onBack()
    }

    LaunchedEffect(state.isGenerating) {
        // Keep the in-flight turn visible; preserve drafts when the request fails.
        if (state.isGenerating) {
            if (showComposer) showComposer = false
            showMenu = false
        }
    }
    if (showComposer && !state.isFinished) {
        ModalBottomSheet(onDismissRequest = { showComposer = false }) {
            Column(Modifier.fillMaxWidth().imePadding().padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (state.draft.isBlank()) {
                    Text(uiText("Need a starting point?"),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AssistChip(onClick = { viewModel.setDraft(attemptStarter) },
                            label = { Text(uiText("Share my thinking")) })
                        AssistChip(onClick = { viewModel.setDraft(questionStarter) },
                            label = { Text(uiText("Ask for an example")) })
                    }
                }
                StudyFocusFrame(active = state.draft.isNotBlank() && state.canSubmitAttempt) {
                    AttemptEditor(state, viewModel)
                }
                TextButton(onClick = { showComposer = false }) { Text(uiText("Back to lesson")) }
                Spacer(Modifier.height(28.dp))
            }
        }
    }
    if (confirmDelete) AlertDialog(onDismissRequest = { confirmDelete = false },
        title = { Text(uiText("Delete this session?")) }, text = { Text(uiText("Its attempts and review cards will be removed.")) },
        confirmButton = { TextButton(onClick = { viewModel.deleteAllSessionData(); confirmDelete = false }) { Text(uiText("Delete")) } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(uiText("Keep session")) } })
    var detailsTab by remember { mutableStateOf("Material") }
    var findQuery by remember { mutableStateOf("") }
    var sourcePicker by remember { mutableStateOf(false) }
    var removeSource by remember { mutableStateOf<String?>(null) }
    val candidates by viewModel.sourceCandidates.collectAsStateWithLifecycle()
    if (sourcePicker) com.halo.app.ui.workspace.WorkspaceObjectPicker(candidates.filter { it.id.value !in state.session?.sourceObjectIds.orEmpty() },
        onPick = { viewModel.changeSources(state.session?.sourceObjectIds.orEmpty() + it.id.value); sourcePicker = false }, onDismiss = { sourcePicker = false })
    removeSource?.let { id -> AlertDialog(onDismissRequest = { removeSource = null }, title = { Text(uiText("Remove active material?")) },
        text = { Text(uiText("Future turns will use the remaining sources. Earlier citations and practice cards keep their original source identity.")) },
        confirmButton = { TextButton({ viewModel.changeSources(state.session?.sourceObjectIds.orEmpty() - id); removeSource = null }) { Text(uiText("Remove")) } },
        dismissButton = { TextButton({ removeSource = null }) { Text(uiText("Keep")) } }) }
    if (showDetails) ModalBottomSheet(onDismissRequest = { showDetails = false }) {
        Column(Modifier.fillMaxWidth().padding(20.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("Material", "Languages", "Setup", "Schedule", "Progress").forEach { tab -> FilterChip(detailsTab == tab, { detailsTab = tab }, { Text(tab) }) }
            }
            when (detailsTab) {
                "Material" -> {
                    if (state.sources.isEmpty()) Text(uiText("Topic lesson · general knowledge · no attached material"))
                    Text("${state.sources.count { it.excerpt != null }} text sources ready · ${state.sources.count { it.coverage == SourceCoverage.MISSING }} without extracted text")
                    OutlinedButton({ sourcePicker = true }, enabled = state.sources.size < 4 && !state.isGenerating) { StudyActionLabel(HaloGlyph.FILE, "Add material") }
                    if (state.sources.any { it.resolved is ResolvedStudySource.Text }) {
                        HaloTextField(findQuery, { findQuery = it.take(200) }, label = { Text(uiText("Find in material (text only)")) }, kind = VoiceFieldKind.SEARCH, maxChars = 200)
                        TextButton({ viewModel.findInMaterial(findQuery) }) { StudyActionLabel(HaloGlyph.SEARCH, "Find passage") }
                    }
                    state.sources.forEach { source ->
                        SourceCard(source, state, viewModel, onOpenSource)
                        if (source.resolved is ResolvedStudySource.Visual || source.resolved is ResolvedStudySource.Pdf) {
                            FilterChip(state.activeVisualId == source.objectId || (state.activeVisualId == null && state.sources.count { it.resolved is ResolvedStudySource.Visual || it.resolved is ResolvedStudySource.Pdf } == 1),
                                { viewModel.selectVisual(source.objectId) }, { Text(uiText("Use this visual material")) })
                            TextButton(onConfigureVision) { Text(uiText("Configure vision")) }
                        }
                        (source.resolved as? ResolvedStudySource.Pdf)?.let { pdf ->
                            val page = state.pdfPages[source.objectId] ?: 1
                            Text("Page $page of ${pdf.pageCount} · visual only · OCR not installed")
                            Row {
                                TextButton({ viewModel.selectPdfPage(source.objectId, page - 1) }, enabled = page > 1) { Text(uiText("Previous page")) }
                                TextButton({ viewModel.selectPdfPage(source.objectId, page + 1) }, enabled = page < pdf.pageCount) { Text(uiText("Next page")) }
                            }
                        }
                        TextButton({ removeSource = source.objectId }) { Text(uiText("Remove material")) }
                    }
                    if (state.sources.any { it.stale }) {
                        TextButton(viewModel::continueWithCurrentSources) { Text(uiText("Continue with current source")) }
                        TextButton(viewModel::restartUpdatedLesson) { Text(uiText("Restart updated lesson")) }
                    }
                    OutlinedButton(viewModel::createPracticeCards, enabled = !state.isGenerating && state.sources.isNotEmpty()) { StudyActionLabel(HaloGlyph.MEMORY, "Create practice cards") }
                }
                "Languages" -> state.session?.languages?.let { languages ->
                    var assignment by remember(languages) { mutableStateOf(languages.assignmentLanguage.orEmpty()) }
                    var explanation by remember(languages) { mutableStateOf(languages.explanationLanguage.orEmpty()) }
                    var strongest by remember(languages) { mutableStateOf(languages.strongestLanguage.orEmpty()) }
                    HaloTextField(assignment, { assignment = it.take(80) }, label = { Text(uiText("Assignment language · blank = same as material")) }, kind = VoiceFieldKind.SHORT, maxChars = 80)
                    HaloTextField(explanation, { explanation = it.take(80) }, label = { Text(uiText("Explanation language (blank = app language)")) }, kind = VoiceFieldKind.SHORT, maxChars = 80)
                    HaloTextField(strongest, { strongest = it.take(80) }, label = { Text(uiText("Strongest language · brief clarification")) }, kind = VoiceFieldKind.SHORT, maxChars = 80)
                    TextButton({ viewModel.setStudyLanguages(assignment, explanation, strongest) }) { Text(uiText("Save languages")) }
                }
                "Setup" -> state.session?.let { StudyLearningSetup(it.learnerLevel, it.supportStyle, it.knowledgePolicy, viewModel::setLearningSetup) }
                "Schedule" -> ReviewIntensityRow(state.reviewIntensity, viewModel)
                "Progress" -> {
                    Text(uiText("%1\$d attempts saved", state.attempts.size))
                    state.measuredMasteryPercent?.let { Text(uiText("Measured mastery: %1\$d%%", it)) }
                    Text(uiText("%1\$d cards due · %2\$d mastered", state.dueReviewItems.size, state.masteredCount))
                    state.executionLabel?.let { Text(it, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
            TextButton({ showDetails = false }) { Text(uiText("Done")) }
            Spacer(Modifier.height(24.dp))
        }
    }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
    Column(
        Modifier.widthIn(max = 720.dp).fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 20.dp)
    ) {
        HaloScreenHeader(
            title = state.session?.goal?.takeIf { it.isNotBlank() } ?: uiText("Study"),
            subtitle = uiText(if (focusedReview) "Focused review" else "One step at a time."),
            navigation = {
                viewModel.stopReading("Left the Study screen")
                onBack()
            },
            primaryAction = null,
            glyph = HaloGlyph.QUESTION,
        )

        if (state.sources.isNotEmpty()) {
            AssistChip(
                onClick = { detailsTab = "Material"; showDetails = true },
                label = { Text(uiText("Material") + " · " + state.sources.size) },
                leadingIcon = {
                    HaloIcon(HaloGlyph.FILE, contentDescription = null,
                        modifier = Modifier.size(18.dp))
                },
            )
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            if (state.isGenerating) {
                // Presence motion is driven by the real turn state. The supplied
                // THINKING animation pauses with lifecycle and honors reduced motion.
                com.halo.app.ui.presence.HaloSignalFace(
                    presentation = com.halo.app.ui.presence.HaloSignalPresentation(
                        state = com.halo.app.ui.presence.HaloSignalState.THINKING,
                        label = "Thinking",
                    ),
                    size = 72.dp,
                    contentDescription = uiText("halo is thinking..."),
                )
                Spacer(Modifier.width(8.dp))
                Text(uiText("Preparing your next step…"), modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                HaloActionButton(
                    onClick = { viewModel.cancelTurn("Learner cancelled") },
                    style = HaloActionStyle.STOP,
                ) { Text(uiText("Stop")) }
            } else {
                Box(Modifier.weight(1f)) {
                    TextButton(onClick = { showMenu = true }) {
                        StudyActionLabel(HaloGlyph.SETTINGS, "Lesson options")
                    }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        DropdownMenuItem(
                            text = { Text(uiText("Sources and progress")) },
                            onClick = { showMenu = false; showDetails = true },
                        )
                        DropdownMenuItem(
                            text = { Text(uiText("Listen, then explain")) },
                            enabled = state.sources.any { !it.excerpt.isNullOrBlank() && !it.stale },
                            onClick = { showMenu = false; viewModel.listenThenExplain() },
                        )
                        DropdownMenuItem(
                            text = { Text(uiText("Save study notes to Workspace")) },
                            enabled = !state.savingSummary && !state.loading,
                            onClick = { showMenu = false; viewModel.saveSummaryToNotebook() },
                        )
                        DropdownMenuItem(
                            text = { Text(uiText(if (state.isFinished) "Already completed" else "Finish session")) },
                            enabled = !state.isFinished,
                            onClick = { showMenu = false; viewModel.finishSession() },
                        )
                        DropdownMenuItem(
                            text = { Text(uiText("Delete this session's data")) },
                            onClick = { showMenu = false; confirmDelete = true },
                        )
                    }
                }
                if (focusedReview || state.dueReviewItems.isNotEmpty()) {
                    TextButton(onClick = {
                        focusedReview = !focusedReview
                        skippedReviewIds = emptyList()
                    }) {
                        Text(if (focusedReview) uiText("Learn")
                            else uiText("Review (%1\$d)", state.dueReviewItems.size))
                    }
                }
            }
        }

        state.reader?.let { reader ->
            Spacer(Modifier.height(4.dp))
            ReaderPanel(reader, viewModel)
        }

        state.notice?.let { notice ->
            Surface(
                Modifier.fillMaxWidth().padding(vertical = 6.dp),
                shape = com.halo.app.ui.design.HaloShapes.control,
                color = MaterialTheme.colorScheme.secondaryContainer,
            ) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        notice,
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                    TextButton(onClick = viewModel::clearNotice) { Text(uiText("Dismiss")) }
                }
            }
        }

        if (state.loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    CircularProgressIndicator()
                    Text(uiText("Opening your lesson"), style = MaterialTheme.typography.bodyMedium)
                }
            }
            return@Column
        }
        state.unavailable?.let { message ->
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(Modifier.fillMaxWidth().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    HaloIcon(HaloGlyph.WARNING, contentDescription = null, modifier = Modifier.size(40.dp),
                        tint = MaterialTheme.colorScheme.error)
                    Text(message, style = MaterialTheme.typography.titleMedium)
                    OutlinedButton(onClick = onBack) { Text(uiText("Back to Study")) }
                }
            }
            return@Column
        }

        StudySessionGuide(state, reviewing = focusedReview)
        Spacer(Modifier.height(12.dp))

        if (focusedReview) {
            val current = state.dueReviewItems.firstOrNull { it.id !in skippedReviewIds }
            Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(uiText("Focused review"), style = MaterialTheme.typography.titleMedium)
                Text(uiText("Remember first. Reveal the answer or check your own attempt, then rate what you remembered."),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (current == null) {
                    HaloIcon(HaloGlyph.SUCCESS, contentDescription = null,
                        modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
                    Text(uiText(if (state.dueReviewItems.isEmpty()) "You're caught up for now." else "You've skipped the remaining cards."))
                    if (skippedReviewIds.isNotEmpty()) TextButton(onClick = { skippedReviewIds = emptyList() }) { Text(uiText("Return to skipped cards")) }
                    TextButton(onClick = { focusedReview = false }) { Text(uiText("Back to tutor")) }
                } else key(current.id) {
                    val revealed = current.id in state.revealedAnswers
                    val answerDraft = state.cardAnswers[current.id].orEmpty()
                    val result = state.cardResults[current.id]
                    val autoReviewed = result?.verdict != null
                    StudyFocusFrame(active = !revealed && current.id !in state.reviewing) {
                        Column(Modifier.fillMaxWidth().padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            StudyReviewQuestionFace(current.question)
                            if (!revealed) {
                                Text(uiText("Try to answer without looking. Your draft stays on this device."),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                                HaloTextField(
                                    value = answerDraft,
                                    onValueChange = { viewModel.setCardAnswer(current.id, it.take(1500)) },
                                    modifier = Modifier.fillMaxWidth(),
                                    label = { Text(uiText("Your answer")) },
                                    minLines = 2,
                                    enabled = current.id !in state.reviewing, kind = VoiceFieldKind.TEXT, maxChars = 1500)
                                key(current.id) {
                                    HaloVoiceTextActions(text="",allowDictation=false,readActionLabel="Read question aloud",
                                        onText={ viewModel.setCardAnswer(current.id,it.take(1500)) },
                                        enabled=current.id !in state.reviewing,
                                        readText=current.question,maxChars=1500,appendDictation=false)
                                }
                                Button(onClick = { viewModel.checkCardAnswer(current.id) },
                                    enabled = answerDraft.isNotBlank() && current.id !in state.reviewing,
                                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                                    StudyActionLabel(HaloGlyph.SUCCESS, "Check answer")
                                }
                                TextButton(onClick = { viewModel.revealAnswer(current.id) },
                                    enabled = current.id !in state.reviewing) { Text(uiText("Reveal answer instead")) }
                            } else {
                                Text(uiText("Stored answer"), style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(current.answer, style = MaterialTheme.typography.bodyLarge)
                                result?.let { outcome ->
                                    Text(outcome.message, style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface)
                                }
                                if (!autoReviewed) {
                                    Text(uiText("How well did you remember?"), style = MaterialTheme.typography.titleSmall)
                                    StudyRecallActions(
                                        enabled = current.id !in state.reviewing,
                                        onNotYet = { viewModel.selfAssessCard(current.id, false) },
                                        onKnow = { viewModel.selfAssessCard(current.id, true) },
                                    )
                                }
                            }
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                TextButton(onClick = { skippedReviewIds = skippedReviewIds + current.id }) {
                                    Text(uiText("Skip for now"))
                                }
                                current.sourceObjectId?.let { id ->
                                    TextButton(onClick = {
                                        viewModel.stopReading("Opening source")
                                        onOpenSource(id)
                                    }) { StudyActionLabel(HaloGlyph.FILE, "Open source") }
                                }
                            }
                        }
                    }
                }
            }
            return@Column
        }

        Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState())) {
            LearningJourneyPanel(state, viewModel, nextAction = nextAction)
            if (state.journey.stage != LearningStage.DISCOVER)
                LearningHelpLadder(state, viewModel, guideHint = nextAction == LearningNextAction.HINT)
            Spacer(Modifier.height(8.dp))
            if (state.hasPartialCoverage) {
                Text(
                    uiText("halo sent only part of this source to the model. Ask about a narrower passage for a complete answer."),
                    style = MaterialTheme.typography.labelSmall,
                    // Partial source coverage is a limitation, not an inference failure.
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 4.dp),
                )
            }
            Spacer(Modifier.height(6.dp))
            state.turns.forEach { turn -> TutorTurnCard(turn, state, viewModel, onOpenSource) }
            Spacer(Modifier.height(12.dp))
            StageControls(state, viewModel)
            Spacer(Modifier.height(14.dp))
            if (state.turns.isNotEmpty()) {
                TextButton(onClick = { showSaveCard = !showSaveCard }) { Text(uiText("Make a learning card")) }
                if (showSaveCard) SaveAsCardRow(cardQuestion, { cardQuestion = it }) {
                    viewModel.saveLatestTurnAsCard(cardQuestion)
                    cardQuestion = ""
                }
                Spacer(Modifier.height(10.dp))
            }
            TextButton(onClick = { showCards = !showCards }) { Text(if (showCards) uiText("Hide saved cards") else uiText("Saved cards (%1\$d)", state.practiceItems.size)) }
            if (showCards) PracticeSection(state, viewModel)
            Spacer(Modifier.height(28.dp))
        }
        if (!state.isFinished) {
            StudyFocusAction(
                text = if (state.draft.isNotBlank()) "Continue my answer" else "Write an answer or question",
                glyph = HaloGlyph.NOTE,
                enabled = !state.isGenerating && !state.isSubmitting && !state.loading,
                onClick = { showComposer = true },
                guide = state.journey.stage != LearningStage.DISCOVER && nextAction == LearningNextAction.MIC,
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            )
        }
    }

    }
}

/** A question face only displays stored learner material; it never invents a hint or grade. */
@Composable
internal fun StudyReviewQuestionFace(question: String) {
    Surface(
        modifier = Modifier.fillMaxWidth().heightIn(min = 174.dp),
        shape = com.halo.app.ui.design.HaloShapes.hero,
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.fillMaxWidth().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                HaloIcon(HaloGlyph.QUESTION, contentDescription = null,
                    modifier = Modifier.size(19.dp), tint = MaterialTheme.colorScheme.primary)
                Text(uiText("Question"), style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary)
            }
            Text(question, style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Composable
private fun MasteryChip(percent: Int) {
    val color = when {
        percent >= 80 -> MaterialTheme.colorScheme.primaryContainer
        percent >= 50 -> MaterialTheme.colorScheme.secondaryContainer
        else -> MaterialTheme.colorScheme.surfaceContainerHigh
    }
    Surface(shape = com.halo.app.ui.design.HaloShapes.control, color = color) {
        Text(
            uiText("Mastery %1\$d%%", percent),
            Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

/** Sentence-level read-along. Word timing is unavailable, so the unit is honestly a sentence. */
@Composable
private fun ReaderPanel(reader: ReaderState, viewModel: StudyViewModel) {
    var expanded by remember(reader.passageId) { mutableStateOf(false) }
    Surface(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        shape = com.halo.app.ui.design.HaloShapes.card,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                HaloIcon(HaloGlyph.SPEAK, contentDescription = null,
                    modifier = Modifier.size(24.dp), tint = MaterialTheme.colorScheme.primary)
                Column(Modifier.weight(1f)) {
                    Text(reader.sourceLabel, style = MaterialTheme.typography.titleSmall,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(reader.progressLabel, style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = viewModel::toggleReaderPlayback) {
                    Icon(if (reader.speaking || reader.preparing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = uiText(if (reader.preparing) "Cancel voice preparation" else if (reader.speaking) "Pause reading" else "Resume reading"))
                }
                IconButton(onClick = viewModel::closeReader) {
                    Icon(Icons.Rounded.Stop, contentDescription = uiText("Stop reading"))
                }
            }
            LinearProgressIndicator(
                progress = { (reader.index + 1).toFloat() / reader.sentences.size.coerceAtLeast(1) },
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                androidx.compose.ui.text.buildAnnotatedString {
                    append(reader.current)
                    reader.wordRange?.let { range ->
                        addStyle(
                            androidx.compose.ui.text.SpanStyle(background = MaterialTheme.colorScheme.primaryContainer),
                            range.first,
                            (range.last + 1).coerceAtMost(reader.current.length),
                        )
                    }
                },
                style = MaterialTheme.typography.bodyMedium,
                maxLines = if (expanded) Int.MAX_VALUE else 2,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface,
            )
            TextButton(onClick = { expanded = !expanded }) {
                Text(uiText(if (expanded) "Hide reading controls" else "Reading controls"))
            }
            if (expanded) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(onClick = viewModel::readerPreviousSentence,
                        enabled = reader.hasPrevious, modifier = Modifier.weight(1f)) {
                        StudyActionLabel(HaloGlyph.PREVIOUS, "Previous sentence")
                    }
                    Spacer(Modifier.width(8.dp))
                    OutlinedButton(onClick = viewModel::readerNextSentence,
                        enabled = reader.hasNext, modifier = Modifier.weight(1f)) {
                        StudyActionLabel(HaloGlyph.NEXT, "Next sentence")
                    }
                }
                Text(uiText("Reading speed"), style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(0.75, 1.0, 1.25, 1.5).forEach { speed ->
                        FilterChip(
                            selected = reader.speed == speed,
                            onClick = { viewModel.setReaderSpeed(speed) },
                            label = { Text("${speed}×") },
                        )
                    }
                }
                Text(uiText("Pause keeps your place. Play starts this sentence again."),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(checked = reader.autoAdvance,
                        onCheckedChange = viewModel::setReaderAutoAdvance)
                    Spacer(Modifier.width(8.dp))
                    Text(uiText("Continue to the next sentence automatically"),
                        style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun StageControls(state: StudyUiState, viewModel: StudyViewModel) {
    if (state.isFinished) return
    var confirmSolution by remember { mutableStateOf(false) }
    if (confirmSolution) AlertDialog(
        onDismissRequest = { confirmSolution = false },
        title = { Text(uiText("Reveal the full solution?")) },
        text = { Text(uiText("Try a hint or your own attempt first. Reveal only when you want the complete answer.")) },
        confirmButton = { TextButton({
            confirmSolution = false
            viewModel.askForSolution()
        }) { Text(uiText("Reveal solution")) } },
        dismissButton = { TextButton({ confirmSolution = false }) { Text(uiText("Keep trying")) } },
    )
    Text(uiText("Need another approach?"), style = MaterialTheme.typography.titleSmall)
    Spacer(Modifier.height(6.dp))
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = viewModel::askTutor, enabled = !state.isGenerating && !state.isSubmitting) {
            StudyActionLabel(HaloGlyph.THINK,
                if (state.turns.isEmpty()) "Explain the first step" else "Repeat this step")
        }
        nextGuidanceAction(state.stage)?.let { label ->
            TextButton(onClick = viewModel::advance,
                enabled = !state.isGenerating && !state.isSubmitting) {
                StudyActionLabel(HaloGlyph.NEXT, label)
            }
        }
        if (state.stage.next() == TutorStage.SOLUTION) {
            TextButton(onClick = { confirmSolution = true },
                enabled = !state.isGenerating && !state.isSubmitting) {
                StudyActionLabel(HaloGlyph.APPROVAL, "Reveal solution")
            }
        }
        var more by remember { mutableStateOf(false) }
        Box {
            TextButton(onClick = { more = true }) { Text(uiText("More help")) }
            DropdownMenu(more, { more = false }) {
                DropdownMenuItem(text = { Text(uiText("Previous step")) },
                    enabled = !state.isGenerating && state.stage.previous() != null,
                    onClick = { more = false; viewModel.goBack() })
                DropdownMenuItem(text = { Text(uiText("Try another explanation")) },
                    enabled = !state.isGenerating && !state.isSubmitting,
                    onClick = { more = false; viewModel.repeatStage() })
                DropdownMenuItem(text = { Text(uiText("Show full solution")) },
                    enabled = !state.isGenerating && !state.isSubmitting && !state.stage.isSolution,
                    onClick = { more = false; confirmSolution = true })
            }
        }
    }
    if (state.attempts.isNotEmpty() || state.stage.isSolution) {
        Spacer(Modifier.height(12.dp))
        TextButton(onClick = viewModel::finishSession,
            enabled = !state.isGenerating && !state.isSubmitting) {
            StudyActionLabel(HaloGlyph.SUCCESS, "Finish session")
        }
    }
}

@Composable
private fun SourceCard(
    source: StudySourceReference,
    state: StudyUiState,
    viewModel: StudyViewModel,
    onOpenSource: (String) -> Unit,
) {
    Surface(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        shape = com.halo.app.ui.design.HaloShapes.card,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                HaloIcon(HaloGlyph.FILE, contentDescription = null, modifier = Modifier.size(22.dp),
                    tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text(source.title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                IconButton(
                    onClick = { viewModel.readSourceAloud(source.objectId) },
                    enabled = state.narrationAvailable && !source.excerpt.isNullOrBlank(),
                ) {
                    Icon(Icons.Rounded.GraphicEq, contentDescription = uiText("Read source aloud"))
                }
            }
            TextButton(onClick = { viewModel.stopReading("Opening source"); onOpenSource(source.objectId) }) { StudyActionLabel(HaloGlyph.FILE, "Open source") }
            if(source.resolved is ResolvedStudySource.Visual || source.resolved is ResolvedStudySource.Pdf) {
                val page = state.pdfPages[source.objectId] ?: (source.resolved as? ResolvedStudySource.Pdf)?.selectedPage ?: 1
                TextButton(onClick={com.halo.app.di.ManualDependencyGraph.extractionReview.workspace(source.objectId,page,page)}) { Text(uiText("Read the words in this image/page")) }
            }
            source.status?.let { Text(it, style = MaterialTheme.typography.labelSmall) }
            source.excerpt?.let { excerpt ->
                Spacer(Modifier.height(4.dp))
                Text(excerpt, style = MaterialTheme.typography.bodySmall, maxLines = 4)
            }
            when (source.coverage) {
                SourceCoverage.MISSING ->
                    Text(uiText("No readable text is available for this source."),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                SourceCoverage.PARTIAL ->
                    Text(
                        "Coverage: partial — ${source.omittedCharacters} of ${source.totalCharacters} " +
                            "characters are not sent to the model.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                SourceCoverage.FULL -> Unit
            }
            if (source.stale) {
                Spacer(Modifier.height(6.dp))
                Text(
                    uiText("This source changed since the session started. Citations refer to revision") + " " +
                        "${state.session?.sources?.firstOrNull { it.objectId == source.objectId }?.revision ?: "unknown"}.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun TutorTurnCard(turn: StudyTurn, state: StudyUiState, viewModel: StudyViewModel, onOpenSource: (String) -> Unit) {
    val reading = state.reader?.passageId == "${state.session?.id}:turn:${turn.id}"
    val latest = state.turns.lastOrNull()?.id == turn.id
    var sourcesExpanded by remember(turn.id) { mutableStateOf(false) }
    Surface(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        shape = com.halo.app.ui.design.HaloShapes.card,
        color = if (latest) MaterialTheme.colorScheme.surfaceContainerLow else MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, if (latest) MaterialTheme.colorScheme.primary.copy(alpha = 0.50f)
            else MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                HaloIcon(HaloGlyph.HALO, contentDescription = null, modifier = Modifier.size(22.dp),
                    tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text(
                    turn.stage.name.replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                turn.verdict?.let { VerdictChip(it) }
                if (turn.text.isNotBlank()) {
                    IconButton(
                        onClick = {
                            if (reading) viewModel.stopReading("Stopped by learner")
                            else viewModel.readTurnAloud(turn)
                        },
                        enabled = state.narrationAvailable,
                    ) {
                        Icon(
                            if (reading) Icons.Rounded.Stop else Icons.Rounded.GraphicEq,
                            contentDescription = if (reading) "Stop reading" else "Read response aloud",
                        )
                    }
                }
            }
            turn.question?.let { Text("You asked: $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant); Spacer(Modifier.height(12.dp)) }
            if (turn.failure != null) {
                Text(turn.failure, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
            } else {
                Text(turn.text, style = MaterialTheme.typography.bodyMedium)
            }
            turn.nextStep?.takeIf { it.isNotBlank() }?.let { next ->
                Spacer(Modifier.height(8.dp))
                Text(uiText("Next: %1\$s", uiText(next)), style = MaterialTheme.typography.labelMedium)
            }
            turn.uncertainty?.takeIf { it.isNotBlank() }?.let { uncertainty ->
                Spacer(Modifier.height(6.dp))
                Text(
                    uiText(uncertainty),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (turn.citations.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = { sourcesExpanded = !sourcesExpanded }) {
                    Text(uiText(if (sourcesExpanded) "Hide cited material" else "View cited material"))
                }
                if (sourcesExpanded) turn.citations.forEach { citation ->
                    CitationRow(citation, onOpenSource)
                }
            }
        }
    }
}

@Composable
private fun CitationRow(citation: StudyCitation, onOpenSource: (String) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.Top) {
        HaloSignalGlyph(kind = HaloSignalKind.FILE, size = 16.dp)
        Spacer(Modifier.width(6.dp))
        Column {
            Text(
                "${citation.sourceTitle ?: "Source"}${citation.locator?.let { " · $it" }.orEmpty()}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton({ onOpenSource(citation.sourceObjectId) }) { Text(if (citation.locator?.startsWith("Page ") == true) uiText("Open source · referenced %1\$s", citation.locator) else uiText("Open source")) }
            Text(citation.excerpt, style = MaterialTheme.typography.bodySmall, maxLines = 3)
        }
    }
}

@Composable
private fun VerdictChip(verdict: StudyVerdict) {
    val color = when (verdict) {
        StudyVerdict.CHECKS_OUT -> MaterialTheme.colorScheme.primaryContainer
        StudyVerdict.NEEDS_WORK -> MaterialTheme.colorScheme.surfaceContainerHigh
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    Surface(shape = com.halo.app.ui.design.HaloShapes.control, color = color) {
        Text(
            uiText(verdict.label),
            Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun AttemptEditor(state: StudyUiState, viewModel: StudyViewModel) {
    Column(Modifier.fillMaxWidth()) {
        Text(uiText("Your answer or question"), style = MaterialTheme.typography.titleSmall)
        Text(uiText("Submit an attempt for feedback. Asking a question does not grade your work."),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(6.dp))
        HaloTextField(
            value = state.draft,
            onValueChange = viewModel::setDraft,
            modifier = Modifier.fillMaxWidth(),
            enabled = !state.isFinished && !state.isGenerating && !state.isSubmitting,
            minLines = 3,
            placeholder = { Text(uiText("Write what you have so far — you can submit at any stage")) }, kind = VoiceFieldKind.TEXT)

        Spacer(Modifier.height(8.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            HaloActionButton(onClick = viewModel::submitAttempt, enabled = state.canSubmitAttempt) {
                Text(uiText(if (state.isSubmitting) "Saving…" else "Submit attempt"))
            }
            TextButton(onClick = viewModel::askQuestion, enabled = state.draft.isNotBlank() && !state.isGenerating && !state.isSubmitting && !state.isFinished) {
                StudyActionLabel(HaloGlyph.QUESTION, "Ask a question")
            }
        }
        val attempts = state.attempts
        if (attempts.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            val unaided = attempts.count { it.helpRevealed.isEmpty() }
            Text(
                uiText("%1\$d attempts saved locally · %2\$d without help", attempts.size, unaided),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SaveAsCardRow(question: String, onQuestionChange: (String) -> Unit, onSave: () -> Unit) {
    Surface(
        Modifier.fillMaxWidth(),
        shape = com.halo.app.ui.design.HaloShapes.control,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(uiText("Save this response as a practice card"), style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(6.dp))
            HaloTextField(
                value = question,
                onValueChange = onQuestionChange,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text(uiText("Question")) }, kind = VoiceFieldKind.TEXT)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onSave, enabled = question.isNotBlank()) { Text(uiText("Save draft card")) }
                Text(uiText("Drafts stay out of the queue until you accept them"),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun PracticeSection(state: StudyUiState, viewModel: StudyViewModel) {
    Column(Modifier.fillMaxWidth()) {
        var showProgress by remember { mutableStateOf(false) }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(uiText("Practice"), style = MaterialTheme.typography.titleMedium)
                Text(
                    uiText("%1\$d cards in review · %2\$d mastered",
                        state.practiceItems.count { it.accepted }, state.masteredCount),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (state.practiceItems.isNotEmpty()) {
                TextButton(onClick = { showProgress = !showProgress },
                    modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(uiText("Memory progress"))
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        if (state.practiceItems.isEmpty()) {
            Text(uiText("Accepted cards appear here with a due date. Nothing enters the queue automatically."),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return
        }
        if (showProgress) state.curveFocusItemId?.let { focusId ->
            val curve = state.curves[focusId]
            val item = state.practiceItems.firstOrNull { it.id == focusId }
            if (curve != null && item != null) {
                Spacer(Modifier.height(8.dp))
                MemoryCard(
                    question = item.question,
                    curve = curve,
                    snapshot = state.mastery[focusId],
                    now = remember { System.currentTimeMillis() },
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        state.practiceItems.forEach { item ->
            PracticeItemRow(item, state, viewModel)
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun PracticeItemRow(
    item: StudyPracticeItem,
    state: StudyUiState,
    viewModel: StudyViewModel,
) {
    val mastery = state.mastery[item.id]
    val result = state.cardResults[item.id]
    var editCard by remember(item.id) { mutableStateOf(false) }
    var confirmDiscard by remember(item.id) { mutableStateOf(false) }
    val interactionReady = !state.isGenerating && !state.isSubmitting && item.id !in state.reviewing
    var expanded by remember(item.id) { mutableStateOf(!item.accepted) }
    var showCardOptions by remember(item.id) { mutableStateOf(false) }
    var editQuestion by remember(item.question) { mutableStateOf(item.question) }
    var editAnswer by remember(item.answer) { mutableStateOf(item.answer) }
    val currentSource = state.sources.firstOrNull { it.objectId == item.sourceObjectId }
    val version = item.sourceVersion ?: item.sourceRevision?.toString()
    val stale = version != null && currentSource?.version != null && version != currentSource.version
    if (editCard) AlertDialog(onDismissRequest = { editCard = false }, title = { Text(uiText("Edit practice card")) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            HaloTextField(editQuestion, { editQuestion = it.take(500) }, label = { Text(uiText("Question")) }, kind = VoiceFieldKind.TEXT, maxChars = 500)
            HaloTextField(editAnswer, { editAnswer = it.take(1000) }, label = { Text(uiText("Answer")) }, kind = VoiceFieldKind.TEXT, maxChars = 1000)
        } }, confirmButton = { TextButton({ viewModel.updateCard(item.id, editQuestion, editAnswer, item.accepted); editCard = false },
            enabled = editQuestion.isNotBlank() && editAnswer.isNotBlank()) { Text(uiText("Save edit")) } },
        dismissButton = { TextButton({ editCard = false }) { Text(uiText("Cancel")) } })
    if (confirmDiscard) AlertDialog(
        onDismissRequest = { confirmDiscard = false },
        title = { Text(uiText(if (item.accepted) "Delete this card?" else "Discard draft")) },
        text = { Text(uiText("This cannot be undone.")) },
        confirmButton = {
            TextButton(onClick = {
                confirmDiscard = false
                viewModel.deleteCard(item.id)
            }) { Text(uiText(if (item.accepted) "Delete card" else "Discard draft")) }
        },
        dismissButton = { TextButton(onClick = { confirmDiscard = false }) {
            Text(uiText("Cancel"))
        } },
    )
    val revealed = item.id in state.revealedAnswers
    val answer = state.cardAnswers[item.id].orEmpty()
    val now = remember(System.currentTimeMillis()) { System.currentTimeMillis() }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = com.halo.app.ui.design.HaloShapes.card,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (!item.accepted) {
                Text(uiText("Draft — not in the review queue"),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Surface(shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.32f)) {
                    Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
                        Icon(androidx.compose.ui.res.painterResource(com.halo.app.R.drawable.halo_ic_flashcard),
                            contentDescription = null, modifier = Modifier.size(24.dp),
                            tint = MaterialTheme.colorScheme.primary)
                    }
                }
                Text(item.question,
                    style = if (expanded) MaterialTheme.typography.titleLarge
                        else MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f), maxLines = if (expanded) Int.MAX_VALUE else 2,
                    overflow = TextOverflow.Ellipsis)
                mastery?.let { snapshot -> MasteryBadge(snapshot, now) }
            }
            if (!expanded) {
                TextButton(onClick = { expanded = true }) {
                    StudyActionLabel(HaloGlyph.NEXT, if (item.accepted) "Practise this card" else "Review draft card")
                }
                return@Column
            }
            TextButton(onClick = { expanded = false }) { Text(uiText("Collapse card")) }
            if (item.mastered) {
                Spacer(Modifier.height(4.dp))
                Text(uiText("Mastered — kept in a long-interval maintenance queue"),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            item.sourceLocator?.let { Text(it, style = MaterialTheme.typography.labelSmall) }
            TextButton({ editCard = true }) { Text(uiText("Edit")) }
            if (stale) {
                Text(uiText("Source changed · this card keeps its original version"),
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton({ viewModel.reviewCardSource(item.id) }) { Text(uiText("Review source in Material")) }
                TextButton(viewModel::createPracticeCards, enabled = !state.isGenerating && !item.userEdited) { Text(uiText("Generate replacement drafts")) }
                Text(uiText("Replacement drafts require current material confirmation; this card and its review history stay intact."), style = MaterialTheme.typography.bodySmall)
                TextButton({ viewModel.setCardAccepted(item.id, true) }) { Text(uiText("Keep current card")) }
            }
            if (item.userEdited) {
                Spacer(Modifier.height(4.dp))
                Text(uiText("You edited this card; Halo will not overwrite it."),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (item.accepted) {
                Spacer(Modifier.height(8.dp))
                HaloTextField(
                    value = answer,
                    onValueChange = { viewModel.setCardAnswer(item.id, it) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    enabled = interactionReady,
                    label = { Text(uiText("Your answer")) }, kind = VoiceFieldKind.TEXT)
                HaloVoiceTextActions(text="",allowDictation=false,readActionLabel="Read question aloud",onText={viewModel.setCardAnswer(item.id,it)},
                    enabled=interactionReady,readText=item.question,
                    maxChars=1500,appendDictation=false)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Button(onClick = { viewModel.checkCardAnswer(item.id) },
                        enabled = interactionReady && answer.isNotBlank(),
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
                        Text(uiText("Check answer"))
                    }
                    if (!revealed) {
                        OutlinedButton(onClick = { viewModel.revealAnswer(item.id) },
                            enabled = interactionReady,
                            modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
                            Text(uiText("Reveal"))
                        }
                    }
                }
            }

            result?.let { outcome ->
                Spacer(Modifier.height(6.dp))
                if (outcome.diff != null) {
                    AnswerComparison(
                        prefix = outcome.diff.prefix,
                        learner = outcome.diff.learnerMiddle,
                        expected = outcome.diff.expectedMiddle,
                        suffix = outcome.diff.suffix,
                    )
                    Spacer(Modifier.height(4.dp))
                }
                Text(
                    outcome.message,
                    style = MaterialTheme.typography.labelSmall,
                    color = when (outcome.verdict) {
                        true -> MaterialTheme.colorScheme.primary
                        false -> MaterialTheme.colorScheme.onSurfaceVariant
                        null -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
                if (outcome.verdict == null) {
                    StudyRecallActions(
                        enabled = interactionReady,
                        onNotYet = { viewModel.selfAssessCard(item.id, known = false) },
                        onKnow = { viewModel.selfAssessCard(item.id, known = true) },
                    )
                }
            }

            if (revealed && result == null) {
                Spacer(Modifier.height(6.dp))
                Surface(shape = com.halo.app.ui.design.HaloShapes.card,
                    color = MaterialTheme.colorScheme.surfaceContainer) {
                    Column(Modifier.fillMaxWidth().padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(uiText("Stored answer"),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(item.answer, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                StudyRecallActions(
                    enabled = interactionReady,
                    onNotYet = { viewModel.selfAssessCard(item.id, known = false) },
                    onKnow = { viewModel.selfAssessCard(item.id, known = true) },
                )
            }

            Spacer(Modifier.height(4.dp))
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (!item.accepted) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { confirmDiscard = true },
                            enabled = interactionReady,
                            modifier = Modifier.weight(1f).heightIn(min = 52.dp)) {
                            Text(uiText("Discard draft"))
                        }
                        Button(onClick = { viewModel.setCardAccepted(item.id, true) },
                            enabled = interactionReady,
                            modifier = Modifier.weight(1f).heightIn(min = 52.dp)) {
                            Text(uiText("Keep card"))
                        }
                    }
                } else {
                    Box {
                        TextButton(onClick = { showCardOptions = true }) {
                            Text(uiText("Card options"))
                        }
                        DropdownMenu(
                            expanded = showCardOptions,
                            onDismissRequest = { showCardOptions = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text(uiText("Edit practice card")) },
                                onClick = { showCardOptions = false; editCard = true },
                            )
                            DropdownMenuItem(
                                text = { Text(uiText("Delete card")) },
                                onClick = { showCardOptions = false; confirmDiscard = true },
                            )
                        }
                    }
                }
                Text(
                    uiText("Reviewed %1\$d times", item.reviewCount),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * Review intensity. The target retention is spelled out because it is the trade-off being chosen:
 * a stricter target means shorter gaps and more reviews.
 */
@Composable
private fun ReviewIntensityRow(intensity: StudyReviewIntensity, viewModel: StudyViewModel) {
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(uiText("Review intensity"),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            StudyReviewIntensity.entries.forEach { option ->
                FilterChip(
                    selected = option == intensity,
                    onClick = { viewModel.setReviewIntensity(option) },
                    label = { Text("${option.label} · ${option.retentionPercent}%") },
                )
            }
        }
        Text(
            "${intensity.description}. Applies to how soon a card comes back; the mistake ladder is unaffected.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Memory decay for one card, drawn from the same closed-form model the scheduler uses.
 * Probabilities are sampled per pixel-width, so nothing is stored per day and the chart cannot drift
 * away from the schedule it explains.
 */
@Composable
private fun MemoryCard(
    question: String,
    curve: MasteryCardCurve,
    snapshot: StudyMasterySnapshot?,
    now: Long,
) {
    val lineColor = MaterialTheme.colorScheme.primary
    val dueColor = MaterialTheme.colorScheme.error
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    Surface(
        Modifier.fillMaxWidth(),
        shape = com.halo.app.ui.design.HaloShapes.control,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(uiText("Memory decay"), style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.weight(1f))
                snapshot?.let { state ->
                    Text(
                        "recall now ${state.retentionPercent}% · target ${(state.desiredRetention * 100).toInt()}%",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                question,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(6.dp))
            Canvas(Modifier.fillMaxWidth().height(48.dp)) {
                if (size.width <= 0f || curve.points.size < 2) return@Canvas
                // The 50% line is the horizon the due date is allowed to cross.
                drawLine(
                    color = gridColor,
                    start = Offset(0f, size.height / 2f),
                    end = Offset(size.width, size.height / 2f),
                    strokeWidth = 1f,
                )
                val path = Path()
                curve.points.forEachIndexed { index, point ->
                    val x = size.width * index / (curve.points.size - 1f)
                    val y = (size.height * (1f - point.retrievability.toFloat())).coerceIn(0f, size.height)
                    if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                drawPath(path, color = lineColor, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))
                curve.dueFraction?.let { fraction ->
                    val x = size.width * fraction
                    drawLine(
                        color = dueColor,
                        start = Offset(x, 0f),
                        end = Offset(x, size.height),
                        strokeWidth = 1.5.dp.toPx(),
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(curve.spanLabel(), style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.weight(1f))
                Text(
                    snapshot?.statusLabel(now) ?: "Not measured yet",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (snapshot != null && now >= snapshot.dueAtEpochMs) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
            Text(
                "${curve.reviewCount} review(s) · the line is read at 50% recall probability",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Learner answer in red, stored answer in green, shared prefix/suffix kept neutral. */
@Composable
private fun AnswerComparison(prefix: String, learner: String, expected: String, suffix: String) {
    Column(Modifier.fillMaxWidth()) {
        Text(uiText("Your answer"), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            text = prefix + learner + suffix,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
        )
        Text(uiText("Stored answer"), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(expected, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun MasteryBadge(snapshot: StudyMasterySnapshot, now: Long) {
    val percent = if (snapshot.isMeasured) "${snapshot.masteryPercent}%" else "—"
    Surface(shape = com.halo.app.ui.design.HaloShapes.control, color = MaterialTheme.colorScheme.surfaceVariant) {
        Column(Modifier.padding(horizontal = 8.dp, vertical = 3.dp)) {
            Text(
                "Mastery $percent",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                "${snapshot.statusLabel(now)} · memory ${snapshot.stabilityLabel()}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Round 2 practice decision: two honest choices, never preselect a result. */
@Composable
internal fun StudyRecallActions(
    enabled: Boolean,
    onNotYet: () -> Unit,
    onKnow: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(
            onClick = onNotYet, enabled = enabled,
            modifier = Modifier.weight(1f).heightIn(min = 56.dp),
            shape = com.halo.app.ui.design.HaloShapes.pill,
        ) { Text(uiText("Not yet")) }
        Button(
            onClick = onKnow, enabled = enabled,
            modifier = Modifier.weight(1f).heightIn(min = 56.dp),
            shape = com.halo.app.ui.design.HaloShapes.pill,
        ) { Text(uiText("I know it")) }
    }
}
