package com.halo.app.ui.screens.study

import kotlin.math.roundToInt

import com.halo.app.ui.presence.BindShellPageActions
import com.halo.app.ui.presence.ShellPageAction
import com.halo.app.ui.presence.ShellPageOwner

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
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
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
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextAlign
import com.halo.app.data.study.StudyCitation
import com.halo.app.data.study.StudyPracticeItem
import com.halo.app.data.study.StudyVerdict
import com.halo.app.study.MasteryCardCurve
import com.halo.app.study.SourceCoverage
import com.halo.app.study.StudyMasterySnapshot
import com.halo.app.study.StudyMasteryModel
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

private val practiceDraftCountNotice = Regex("""([1-5]) drafts created\. Review and Keep each card before scheduling\.""")

/** Bloom requires a genuinely observed change to a durably completed lesson. */
internal fun shouldCelebrateStudyCompletion(
    previous: StudySessionLifecycle?,
    current: StudySessionLifecycle?,
): Boolean = previous == StudySessionLifecycle.ACTIVE &&
    current == StudySessionLifecycle.COMPLETED

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun StudyScreen(viewModel: StudyViewModel, onOpenSource: (String) -> Unit = {}, startInReview: Boolean = false, onConfigureVision: () -> Unit = {}, onStartVoiceMode: () -> Unit = {},
    activeTools: com.halo.app.study.ActiveStudyToolsOwner? = null, onBack: () -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val attemptStarter = uiText("I think the answer is... because...")
    val questionStarter = uiText("Can you explain this with an example?")
    var showMenu by remember { mutableStateOf(false) }
    var listenMode by remember { mutableStateOf(false) }
    if (listenMode) StudyListenMode(state, viewModel) { listenMode = false }
    var showDetails by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var showCards by rememberSaveable { mutableStateOf(false) }
    var showSaveCard by remember { mutableStateOf(false) }
    var showComposer by rememberSaveable { mutableStateOf(false) }
    var cardQuestion by remember { mutableStateOf("") }
    var focusedReview by rememberSaveable { mutableStateOf(startInReview) }
    var skippedReviewIds by rememberSaveable { mutableStateOf(listOf<String>()) }
    val pageActionsReady = !state.isGenerating && state.comparingCards.isEmpty()
    val shellOwnsOrb = com.halo.app.ui.presence.LocalShellHaloOrb.current
    val pageOwner = ShellPageOwner("study", state.session?.id,
        "${state.session?.updatedAtEpochMs}:${state.session?.currentExerciseId}:${state.sources.hashCode()}",
        selectionIds = listOfNotNull(state.session?.currentExerciseId),
        canonicalToolIds = listOf("halo_study_current", "halo_study_state"))
    BindShellPageActions(pageOwner, listOf(
        ShellPageAction("page.study.finish", "Finish lesson", HaloGlyph.SUCCESS, pageActionsReady && !state.isFinished) { viewModel.finishSession() },
        ShellPageAction("page.study.delete", "Delete this session's data", HaloGlyph.TRASH, pageActionsReady) { confirmDelete = true },
        ShellPageAction("page.study.details", "Sources and progress", HaloGlyph.FILE, pageActionsReady) { showDetails = true },
        ShellPageAction("page.study.listen", "Listen, then explain", HaloGlyph.SPEAK,
            pageActionsReady && state.sources.any { !it.excerpt.isNullOrBlank() && !it.stale }) { viewModel.listenThenExplain() },
        ShellPageAction("page.study.save-notes", "Save study notes to Workspace", HaloGlyph.NOTE,
            pageActionsReady && !state.savingSummary && !state.loading) { viewModel.saveSummaryToNotebook() },
    ))
    val nextAction = rememberLearningNextAction(state, kids = false,
        blocking = showComposer || showDetails || showMenu || confirmDelete || focusedReview)
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, viewModel, activeTools) {
        var attachment: String? = null
        fun update() {
            if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                if (attachment == null) attachment = activeTools?.attach(viewModel)
            } else { attachment?.let { activeTools?.detach(it) }; attachment = null }
        }
        val observer = LifecycleEventObserver { _, _ -> update() }
        lifecycle.addObserver(observer); update()
        onDispose { lifecycle.removeObserver(observer); attachment?.let { activeTools?.detach(it) } }
    }
    DisposableEffect(lifecycle, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.refreshSources()
            if (event == Lifecycle.Event.ON_PAUSE) viewModel.stopReading("Study no longer visible")
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    // S03/S05: returning from focused review is not leaving the lesson.
    // Otherwise, leaving stops playback while durable session progress remains.
    BackHandler(enabled = true) {
        when {
            focusedReview -> {
                focusedReview = false
                skippedReviewIds = emptyList()
            }
            showComposer -> showComposer = false
            showDetails -> showDetails = false
            showMenu -> showMenu = false
            else -> {
                viewModel.stopReading("Left the Study screen")
                onBack()
            }
        }
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
    var confirmSourceRestart by rememberSaveable { mutableStateOf(false) }
    val candidates by viewModel.sourceCandidates.collectAsStateWithLifecycle()
    if (sourcePicker) com.halo.app.ui.workspace.WorkspaceObjectPicker(candidates.filter { it.id.value !in state.session?.sourceObjectIds.orEmpty() },
        onPick = { viewModel.changeSources(state.session?.sourceObjectIds.orEmpty() + it.id.value); sourcePicker = false }, onDismiss = { sourcePicker = false })
    removeSource?.let { id -> AlertDialog(onDismissRequest = { removeSource = null }, title = { Text(uiText("Remove active material?")) },
        text = { Text(uiText("Future turns will use the remaining sources. Earlier citations and practice cards keep their original source identity.")) },
        confirmButton = { TextButton({ viewModel.changeSources(state.session?.sourceObjectIds.orEmpty() - id); removeSource = null }) { Text(uiText("Remove")) } },
        dismissButton = { TextButton({ removeSource = null }) { Text(uiText("Keep")) } }) }
    if (confirmSourceRestart) AlertDialog(
        onDismissRequest = { confirmSourceRestart = false },
        title = { Text(uiText("Restart with updated material?")) },
        text = { Text(uiText("Your earlier work is kept. The lesson will start again with the updated material.")) },
        confirmButton = {
            TextButton(onClick = {
                confirmSourceRestart = false
                showDetails = false
                viewModel.restartUpdatedLesson()
            }, enabled = !state.isGenerating && !state.isSubmitting) {
                Text(uiText("Restart lesson"))
            }
        },
        dismissButton = {
            TextButton(onClick = { confirmSourceRestart = false }) {
                Text(uiText("Keep current"))
            }
        }
    )
    if (showDetails) ModalBottomSheet(onDismissRequest = { showDetails = false }) {
        Column(Modifier.fillMaxWidth().padding(20.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("Material", "Languages", "Setup", "Schedule", "Progress").forEach { tab -> FilterChip(detailsTab == tab, { detailsTab = tab }, { Text(uiText(tab)) }) }
            }
            when (detailsTab) {
                "Material" -> {
                    if (state.sources.isEmpty()) Text(uiText("Topic lesson · general knowledge · no attached material"))
                    Text(uiText("%1\$d readable sources · %2\$d need attention",
                        state.sources.count { it.excerpt != null },
                        state.sources.count { it.coverage == SourceCoverage.MISSING }),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                            TextButton(onConfigureVision) { Text(uiText("Let halo see photos")) }
                        }
                        (source.resolved as? ResolvedStudySource.Pdf)?.let { pdf ->
                            val page = state.pdfPages[source.objectId] ?: 1
                            Text(uiText("Page %1\$d of %2\$d · This page may need image reading.",
                                page, pdf.pageCount))
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                TextButton(onClick = { viewModel.selectPdfPage(source.objectId, page - 1) },
                                    enabled = page > 1,
                                    modifier = Modifier.heightIn(min = 48.dp)) {
                                    Text(uiText("Previous page"))
                                }
                                TextButton(onClick = { viewModel.selectPdfPage(source.objectId, page + 1) },
                                    enabled = page < pdf.pageCount,
                                    modifier = Modifier.heightIn(min = 48.dp)) {
                                    Text(uiText("Next page"))
                                }
                            }
                        }
                        TextButton({ removeSource = source.objectId }) { Text(uiText("Remove material")) }
                    }
                    if (state.sources.any { it.stale }) {
                        TextButton(viewModel::continueWithCurrentSources) { Text(uiText("Continue with current source")) }
                        TextButton(onClick = { confirmSourceRestart = true },
                            enabled = !state.isGenerating && !state.isSubmitting,
                            modifier = Modifier.heightIn(min = 48.dp)) {
                            Text(uiText("Restart updated lesson"))
                        }
                    }
                    OutlinedButton(onClick = {
                        // Generation progress, refusal and draft review belong to the lesson.
                        // Keep the modal from covering the result of this action.
                        showDetails = false
                        viewModel.createPracticeCards()
                    }, enabled = !state.isGenerating && state.sources.isNotEmpty()) {
                        StudyActionLabel(HaloGlyph.MEMORY, "Create practice cards")
                    }
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

    if (shellOwnsOrb) com.halo.app.ui.presence.BindShellUtilityBand(pageOwner) {
        com.halo.app.ui.screens.notebook.ToolZoneHeader(state.session?.goal?.takeIf { it.isNotBlank() } ?: uiText("Study"),
            onBack = { viewModel.stopReading("Left the Study screen"); onBack() }) {
            StudyLessonOverflow(pageActionsReady, showMenu, { showMenu = it },
                canListen = state.sources.any { !it.excerpt.isNullOrBlank() && !it.stale },
                canSave = !state.savingSummary && !state.loading, completed = state.isFinished,
                onDetails = { showDetails = true }, onListen = viewModel::listenThenExplain,
                onSaveNotes = viewModel::saveSummaryToNotebook, onFinish = viewModel::finishSession,
                onDelete = { confirmDelete = true })
        }
    }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
    Column(
        Modifier.widthIn(max = 720.dp).fillMaxSize()
            .background(com.halo.app.ui.theme.haloWorkspaceBackground())
            .padding(horizontal = 20.dp)
    ) {
        if (!shellOwnsOrb) HaloScreenHeader(
            title = state.session?.goal?.takeIf { it.isNotBlank() } ?: uiText("Study"),
            subtitle = uiText(if (focusedReview) "Focused review" else "One step at a time."),
            navigation = {
                viewModel.stopReading("Left the Study screen")
                onBack()
            },
            primaryAction = if (!com.halo.app.ui.presence.LocalShellHaloOrb.current)
                com.halo.app.ui.design.HaloHeaderAction(uiText("Start Voice Mode"), onStartVoiceMode) else null,
            glyph = HaloGlyph.QUESTION,
            trailing = {
                StudyLessonOverflow(!state.isGenerating && state.comparingCards.isEmpty(), showMenu, { showMenu = it },
                    canListen = state.sources.any { !it.excerpt.isNullOrBlank() && !it.stale },
                    canSave = !state.savingSummary && !state.loading, completed = state.isFinished,
                    onDetails = { showDetails = true }, onListen = viewModel::listenThenExplain,
                    onSaveNotes = viewModel::saveSummaryToNotebook, onFinish = viewModel::finishSession,
                    onDelete = { confirmDelete = true })
            },
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
            if (state.isGenerating || state.comparingCards.isNotEmpty()) {
                // Presence motion is driven by the real turn state. The supplied
                // THINKING animation pauses with lifecycle and honors reduced motion.
                if (!shellOwnsOrb) com.halo.app.ui.presence.HaloSignalFace(
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
                Spacer(Modifier.weight(1f))
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
            // Only this exact host-authored receipt has a dynamic catalogue argument.
            // Other notices, including literal provider/source text, retain their lookup.
            val draftCount = practiceDraftCountNotice.matchEntire(notice)?.groupValues?.get(1)?.toIntOrNull()
            val noticeText = if (draftCount != null) {
                uiText("%1\$d drafts created. Review and Keep each card before scheduling.", draftCount)
            } else uiText(notice)
            Surface(
                Modifier.fillMaxWidth().padding(vertical = 6.dp),
                shape = com.halo.app.ui.design.HaloShapes.control,
                color = MaterialTheme.colorScheme.secondaryContainer,
            ) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        noticeText,
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

        if (focusedReview) StudySessionGuide(state, reviewing = true)
        else StudyJourneyProgress(state.journey.stage)
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
                    var confirmReveal by rememberSaveable(current.id) { mutableStateOf(false) }
                    if (confirmReveal && !revealed) AlertDialog(
                        onDismissRequest = { confirmReveal = false },
                        title = { Text(uiText("Show this answer?")) },
                        text = { Text(uiText("You can try your own answer first. Reveal only when you want to see the saved answer.")) },
                        confirmButton = {
                            TextButton(onClick = {
                                confirmReveal = false
                                viewModel.revealAnswer(current.id)
                            }, enabled = current.id !in state.reviewing) {
                                Text(uiText("Show answer"))
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { confirmReveal = false }) {
                                Text(uiText("Keep trying"))
                            }
                        }
                    )
                    val answerDraft = state.cardAnswers[current.id].orEmpty()
                    val result = state.cardResults[current.id]
                    val autoReviewed = result?.verdict != null
                    var writeAttempt by rememberSaveable(current.id) { mutableStateOf(answerDraft.isNotBlank()) }
                    StudyFocusFrame(active = !revealed && current.id !in state.reviewing) {
                        Column(Modifier.fillMaxWidth().padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            StudyReviewQuestionFace(current.question, answer = current.answer.takeIf { revealed })
                            key(current.id, revealed) {
                                HaloVoiceTextActions(text = "", allowDictation = false,
                                    onText = {}, enabled = current.id !in state.reviewing,
                                    readText = if (revealed) current.answer else current.question,
                                    maxChars = 12000, appendDictation = false)
                            }
                            if (!revealed) {
                                TextButton(onClick = { writeAttempt = !writeAttempt },
                                    enabled = current.id !in state.reviewing,
                                    modifier = Modifier.heightIn(min = 48.dp)) {
                                    Text(uiText(if (writeAttempt) "Hide my answer draft" else "Try my own answer"))
                                }
                                if (writeAttempt) {
                                Text(uiText("Try to answer without looking. Your answer stays in the draft. You can choose to compare it with halo’s answer."),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                                HaloTextField(
                                    value = answerDraft,
                                    onValueChange = { viewModel.setCardAnswer(current.id, it.take(1500)) },
                                    modifier = Modifier.fillMaxWidth(),
                                    label = { Text(uiText("Your answer")) },
                                    minLines = 2,
                                    enabled = current.id !in state.reviewing, kind = VoiceFieldKind.TEXT, maxChars = 1500)
                                CardMeaningComparisonAction(current.id, answerDraft, current.id !in state.reviewing, viewModel)
                                Button(onClick = { viewModel.checkCardAnswer(current.id) },
                                    enabled = answerDraft.isNotBlank() && current.id !in state.reviewing,
                                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                                    StudyActionLabel(HaloGlyph.SUCCESS, "Check answer")
                                }
                                }
                                Button(onClick = { confirmReveal = true },
                                    enabled = current.id !in state.reviewing,
                                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primaryContainer,
                                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer)) {
                                    Text(uiText("Show answer"))
                                }
                            } else {
                                result?.let { outcome ->
                                    CardMeaningFeedback(outcome)
                                    Text(uiText(outcome.message), style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface)
                                }
                                if (!autoReviewed) {
                                    Text(uiText("How well did you remember?"), style = MaterialTheme.typography.titleSmall)
                                    StudyRecallActions(
                                        enabled = current.id !in state.reviewing,
                                        onNotYet = { viewModel.selfAssessCard(current.id, false) },
                                        onAlmost = { viewModel.selfAssessCard(current.id, com.halo.app.study.StudyRecallRating.ALMOST) },
                                        onKnow = { viewModel.selfAssessCard(current.id, true) },
                                    )
                                }
                            }
                            FlowRow(Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                TextButton(onClick = { skippedReviewIds = skippedReviewIds + current.id },
                                    modifier = Modifier.heightIn(min = 48.dp)) {
                                    Text(uiText("Skip for now"))
                                }
                                current.sourceObjectId?.let { id ->
                                    TextButton(onClick = {
                                        viewModel.stopReading("Opening source")
                                        onOpenSource(id)
                                    }, modifier = Modifier.heightIn(min = 48.dp)) {
                                        StudyActionLabel(HaloGlyph.FILE, "Open source")
                                    }
                                }
                            }
                        }
                    }
                }
            }
            return@Column
        }

        Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState())) {
            if (state.turns.any { it.failure == null && it.text.isNotBlank() })
                OutlinedButton(onClick = { viewModel.stopReading("Listen mode opened"); listenMode = true },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(uiText("Listen to this lesson")) }
            val currentTurn = state.turns.lastOrNull { it.exerciseId == state.session?.currentExerciseId }
            currentTurn?.let { TutorTurnCard(it, state, viewModel, onOpenSource) }
            if (state.journey.stage == LearningStage.DISCOVER) {
                LearningJourneyPanel(state, viewModel, nextAction = nextAction)
            } else {
                StageControls(state, viewModel)
                var learningOptions by rememberSaveable(state.session?.id) { mutableStateOf(false) }
                TextButton(onClick = { learningOptions = !learningOptions }) {
                    Text(uiText("Learning options"))
                }
                if (learningOptions || state.journey.stage == LearningStage.REFLECT) {
                    LearningJourneyPanel(state, viewModel, nextAction = nextAction)
                    LearningHelpLadder(state, viewModel, guideHint = nextAction == LearningNextAction.HINT)
                    TextButton(onClick = { showComposer = true }) {
                        Text(uiText("Write an answer or question"))
                    }
                }
            }
            var earlierSteps by rememberSaveable(state.session?.id) { mutableStateOf(false) }
            val history = state.turns.filter { it.id != currentTurn?.id }
            if (history.isNotEmpty()) {
                TextButton(onClick = { earlierSteps = !earlierSteps }) {
                    Text(uiText(if (earlierSteps) "Hide earlier steps" else "Show earlier steps"))
                }
                if (earlierSteps) history.forEach { TutorTurnCard(it, state, viewModel, onOpenSource) }
            }
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
            if (state.journey.stage != LearningStage.DISCOVER) {
                val practice = state.journey.stage == LearningStage.PRACTICE
                val entryLabel = uiText(if (practice) "Try my answer" else "Ask a question")
                val send: () -> Unit = {
                    if (state.canSubmitAttempt) {
                        if (practice) viewModel.submitAttempt() else viewModel.askQuestion()
                    }
                }
                Row(Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    HaloTextField(
                        value = state.draft, onValueChange = viewModel::setDraft,
                        kind = VoiceFieldKind.TEXT, modifier = Modifier.weight(1f),
                        placeholder = { Text(entryLabel) },
                        maxLines = 3, enabled = !state.isGenerating && !state.isSubmitting && !state.loading,
                        shape = com.halo.app.ui.design.HaloShapes.hero,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            imeAction = androidx.compose.ui.text.input.ImeAction.Send),
                        keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                            onSend = { send() }),
                    )
                    IconButton(onClick = send, enabled = state.canSubmitAttempt,
                        modifier = Modifier.size(48.dp)) {
                        HaloIcon(HaloGlyph.NEXT, entryLabel, Modifier.size(24.dp))
                    }
                }
            } else {
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
}

/** A question face only displays stored learner material; it never invents a hint or grade. */
@Composable
internal fun StudyReviewQuestionFace(question: String, answer: String? = null) {
    Surface(
        modifier = Modifier.fillMaxWidth().heightIn(min = 300.dp),
        shape = com.halo.app.ui.design.HaloShapes.hero,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.fillMaxWidth().padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally) {
            Text(if (answer == null) uiText("Question") else question,
                style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            val face = answer ?: question
            Text(face, style = MaterialTheme.typography.headlineSmall.copy(
                fontSize = if (face.length > 120) 24.sp else 32.sp,
                lineHeight = if (face.length > 120) 34.sp else 42.sp, fontWeight = FontWeight.SemiBold),
                textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurface)
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
    FlowRow(Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
            source.status?.let { status ->
                // Translate only these host-authored statuses; preserve arbitrary source reasons.
                val label = when (status) {
                    "Ready", "Partial", "No readable text", "Image · Requires verified vision",
                    "PDF page image · Requires verified vision · OCR not installed" -> uiText(status)
                    else -> status
                }
                Text(label, style = MaterialTheme.typography.labelSmall)
            }
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
                        uiText("Only part of this source is included (%1\$d of %2\$d characters omitted).",
                            source.omittedCharacters, source.totalCharacters),
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
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                HaloIcon(HaloGlyph.HALO, contentDescription = null, modifier = Modifier.size(22.dp),
                    tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text(
                    uiText(turn.stage.name.replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() }),
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
                            contentDescription = uiText(if (reading) "Stop reading" else "Read response aloud"),
                        )
                    }
                }
            }
            turn.question?.let { Text(uiText("You: %1\$s", it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant); Spacer(Modifier.height(12.dp)) }
            if (turn.failure != null) {
                Text(uiText(turn.failure), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
            } else {
                com.halo.app.ui.glass.components.HaloMarkdownText(turn.text)
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
            }
            mastery?.let { snapshot -> MasteryBadge(snapshot, now) }
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
                    label = { Text(uiText("Your answer")) }, kind = VoiceFieldKind.TEXT, maxChars = 1500)
                HaloVoiceTextActions(text="",allowDictation=false,readActionLabel="Read question aloud",onText={viewModel.setCardAnswer(item.id,it)},
                    enabled=interactionReady,readText=item.question,
                    maxChars=1500,appendDictation=false)
                CardMeaningComparisonAction(item.id, answer, interactionReady, viewModel)
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

            result?.takeIf { item.accepted }?.let { outcome ->
                CardMeaningFeedback(outcome)
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
                    uiText(outcome.message),
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
                        onAlmost = { viewModel.selfAssessCard(item.id, com.halo.app.study.StudyRecallRating.ALMOST) },
                        onKnow = { viewModel.selfAssessCard(item.id, known = true) },
                    )
                }
            }

            // Draft review shows what Keep will retain without granting recall/reveal
            // authority. Accepted cards still require their explicit Reveal action.
            if (!item.accepted || (revealed && result == null)) {
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
                if (item.accepted) StudyRecallActions(
                    enabled = interactionReady,
                    onNotYet = { viewModel.selfAssessCard(item.id, known = false) },
                        onAlmost = { viewModel.selfAssessCard(item.id, com.halo.app.study.StudyRecallRating.ALMOST) },
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
                Text(uiText("How well you remember"), style = MaterialTheme.typography.labelMedium)
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
                    snapshot?.let { localizedMasteryStatus(it, now) } ?: uiText("Not measured yet"),
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
internal fun MasteryBadge(snapshot: StudyMasterySnapshot, now: Long) {
    Surface(shape = com.halo.app.ui.design.HaloShapes.control, color = MaterialTheme.colorScheme.surfaceVariant) {
        Column(Modifier.padding(horizontal = 8.dp, vertical = 3.dp)) {
            Text(
                if (snapshot.isMeasured) uiText("Mastery %1\$d%%", snapshot.masteryPercent) else uiText("Mastery —"),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                "${localizedMasteryStatus(snapshot, now)} · ${uiText("Memory: %1\$s", localizedMasteryStability(snapshot))}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** App-owned metadata only; never translate stored questions, answers or source locators. */
@Composable
private fun localizedMasteryStatus(snapshot: StudyMasterySnapshot, now: Long): String = when {
    !snapshot.isMeasured -> uiText("New card")
    now >= snapshot.dueAtEpochMs -> uiText("Due now")
    else -> uiText("Due in %1\$dd", ((snapshot.dueAtEpochMs - now) / StudyMasteryModel.DAY_MS).coerceAtLeast(0))
}

@Composable
private fun localizedMasteryStability(snapshot: StudyMasterySnapshot): String = when {
    !snapshot.isMeasured -> uiText("Not measured yet")
    snapshot.stabilityDays < 1.0 -> uiText("less than a day")
    snapshot.stabilityDays < 2.0 -> uiText("about 1 day")
    snapshot.stabilityDays < 31.0 -> uiText("about %1\$d days", snapshot.stabilityDays.roundToInt())
    else -> uiText("about %1\$d months", (snapshot.stabilityDays / 30.4).roundToInt())
}

/** Learner-owned recall choices; no result is selected automatically. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun StudyRecallActions(
    enabled: Boolean,
    onNotYet: () -> Unit,
    onKnow: () -> Unit,
    modifier: Modifier = Modifier,
    onAlmost: () -> Unit,
) {
    FlowRow(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        maxItemsInEachRow = if (androidx.compose.ui.platform.LocalDensity.current.fontScale >= 1.5f) 1 else 3) {
        com.halo.app.ui.components.HaloActionButton(onClick = onNotYet, enabled = enabled,
            modifier = Modifier.weight(1f).heightIn(min = 56.dp), style = com.halo.app.ui.components.HaloActionStyle.SECONDARY) {
            Text(uiText("Again"))
        }
        com.halo.app.ui.components.HaloActionButton(onClick = onAlmost, enabled = enabled,
            modifier = Modifier.weight(1f).heightIn(min = 56.dp), style = com.halo.app.ui.components.HaloActionStyle.TONAL) {
            Text(uiText("Almost"))
        }
        com.halo.app.ui.components.HaloActionButton(onClick = onKnow, enabled = enabled,
            modifier = Modifier.weight(1f).heightIn(min = 56.dp)) { Text(uiText("I knew it")) }
    }
}

@Composable
private fun CardMeaningComparisonAction(itemId: String, answer: String, enabled: Boolean, model: StudyViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        OutlinedButton(onClick = { model.compareCardMeaning(itemId) }, enabled = enabled && answer.isNotBlank(),
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(uiText("Compare meaning with AI")) }
        Text(uiText("Uses this card and your answer with a permitted model. AI feedback does not grade or schedule the card."),
            style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun CardMeaningFeedback(result: CardReviewResult) {
    result.meaningComparison?.let { feedback ->
        Text(uiText("Compare with halo’s answer"), style = MaterialTheme.typography.titleSmall)
        result.meaningExecutionLabel?.let { Text(uiText(it), style = MaterialTheme.typography.labelMedium) }
        Text(feedback, style = MaterialTheme.typography.bodyMedium)
        com.halo.app.ui.components.HaloReadAloud(feedback)
    }
}
