package com.halo.app.ui.screens.kids

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.halo.app.di.ManualDependencyGraph
import com.halo.app.language.uiText
import com.halo.app.ui.presence.HaloSignalFace
import com.halo.app.ui.state.toSignalPresentation
import com.halo.app.ui.screens.home.HomeViewModel
import com.halo.app.ui.screens.study.StudyHomeViewModel
import com.halo.app.ui.screens.study.StudyViewModel
import com.halo.app.ui.screens.study.shouldCelebrateStudyCompletion
import com.halo.app.ui.fx.HaloFxAnimation
import com.halo.app.ui.fx.HaloFxMoment
import kotlinx.coroutines.delay
import com.halo.sdk.runtime.study.*
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/** One learning workspace, backed by the real Study and canonical Home/tool pipelines. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable fun KidsModeScreen(home: HomeViewModel, onMicrophone: () -> Unit) {
    KidsBrandTheme { KidsModeContent(home, onMicrophone) }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable private fun KidsModeContent(home: HomeViewModel, onMicrophone: () -> Unit) {
    val ui by home.uiState.collectAsStateWithLifecycle()
    val submission by home.textSubmission.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var topic by rememberSaveable { mutableStateOf("") }
    var question by rememberSaveable { mutableStateOf("") }
    var idea by rememberSaveable { mutableStateOf("") }
    var destination by rememberSaveable { mutableStateOf(KidsDestination.HOME) }
    var activeLesson by rememberSaveable { mutableStateOf<String?>(null) }
    var allowStartedLesson by remember { mutableStateOf(false) }
    var startTutor by remember { mutableStateOf(false) }
    var tools by rememberSaveable { mutableStateOf(false) }
    var colours by rememberSaveable { mutableStateOf(false) }
    var saveError by remember { mutableStateOf(false) }
    var showReply by rememberSaveable { mutableStateOf(false) }
    var replyBaseline by rememberSaveable { mutableStateOf("") }
    val replyKey = listOf(ui.presentedIdentity?.interactionId, ui.presentedIdentity?.requestId,
        ui.presentedIdentity?.turnId, ui.presentedResponse?.hashCode()).joinToString(":")
    fun prepareReply() { replyBaseline = replyKey; showReply = true }
    val starter: StudyHomeViewModel = viewModel(key = "kids-study-home", factory = StudyHomeViewModel.Factory(
        ManualDependencyGraph.studyUseCases, ManualDependencyGraph.workspaceRepository, ManualDependencyGraph.workspaceFileRepository,
        ManualDependencyGraph.studySourceResolver, ManualDependencyGraph.studyLessonStore))
    val busy by starter.busy.collectAsStateWithLifecycle()
    val notice by starter.notice.collectAsStateWithLifecycle()
    val sessions by starter.sessions.collectAsStateWithLifecycle()
    LaunchedEffect(starter) { starter.navigation.collectLatest { activeLesson = it; destination = KidsDestination.LEARN; startTutor = allowStartedLesson; allowStartedLesson = false } }
    val lesson: StudyViewModel? = activeLesson?.let { id -> viewModel(key = "kids-lesson-$id", factory = StudyViewModel.Factory(
        id, ManualDependencyGraph.studyUseCases, ManualDependencyGraph.studyTutorGateway,
        notes = ManualDependencyGraph.notebookUseCases, lessons = ManualDependencyGraph.studyLessonStore,
        workspace = ManualDependencyGraph.workspaceRepository, readerStorage = ManualDependencyGraph.storage)) }
    DisposableEffect(lesson) { onDispose { lesson?.cancelTurn("Left Kids lesson"); lesson?.stopReading("Left Kids lesson") } }
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycle, lesson) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_STOP) { lesson?.stopReading("Kids page hidden"); home.cancelVoiceInput() }
        }
        lifecycle.lifecycle.addObserver(observer)
        onDispose { lifecycle.lifecycle.removeObserver(observer) }
    }
    fun listen() { prepareReply(); if (ui.microphonePermissionGranted) home.toggleListening() else onMicrophone() }
    if (colours) ModalBottomSheet(onDismissRequest = { colours = false }) {
        com.halo.app.ui.screens.settings.PaletteControls()
        Spacer(Modifier.height(24.dp))
    }
    if (tools) ModalBottomSheet(onDismissRequest = { tools = false }) {
        Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(uiText("My tools"), style = MaterialTheme.typography.headlineSmall)
            Text(uiText("Choose a tool. Halo will ask when an action needs approval."), style = MaterialTheme.typography.bodyMedium)
            listOf("5-minute timer" to "start a 5 minute timer", "Open camera" to "open camera",
                "Device information" to "show device info").forEach { (label, command) ->
                OutlinedButton(onClick = { prepareReply(); home.submitText(command); tools = false }, enabled = !submission.busy,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text(uiText(label)) }
            }
        }
    }
    val stopActions: () -> Unit = {
        allowStartedLesson = false
        startTutor = false
        home.cancelSession()
        lesson?.cancelTurn("Stopped by learner")
        lesson?.stopReading()
    }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(Modifier.widthIn(max = 720.dp).fillMaxSize().imePadding(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item(key = "header") {
                val signal = ui.sessionState.toSignalPresentation(fallback = ui.runtimeState)
                if (destination == KidsDestination.HOME) {
                    KidsModeHeader(signal, ::listen,
                        { colours = true },
                        { scope.launch {
                            try { ManualDependencyGraph.kidsPreferences.setEnabled(false) }
                            catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
                            catch (_: Exception) { saveError = true }
                        } },
                        stopActions, saveError)
                } else {
                    KidsActivityHeader(destination, signal,
                        onBack = { destination = KidsDestination.HOME },
                        onStop = stopActions)
                }
            }
            if (destination == KidsDestination.HOME) item(key = "choices") {
                KidsHomeChoices { destination = it }
            }
            if (destination == KidsDestination.LEARN) item(key = "learn") {
                KidsCard {
                    Text(uiText("My learning adventure"), style = MaterialTheme.typography.titleLarge)
                    if (lesson == null) {
                        KidsLearningStart(topic, { topic = it }, busy, {
                            allowStartedLesson = true; starter.start(topic, "", level = StudyLearnerLevel.PRIMARY,
                                style = StudySupportStyle.GUIDED, policy = StudyKnowledgePolicy.SOURCE_PLUS_GENERAL)
                        })
                        sessions.filter { it.learnerLevel in setOf(StudyLearnerLevel.PRIMARY, StudyLearnerLevel.EARLY_PRIMARY) }.take(2).forEach { session ->
                            OutlinedButton(onClick = { activeLesson = session.id }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text(session.goal ?: uiText("Continue learning")) }
                        }
                    } else {
                        KidsLesson(lesson, startTutor) { startTutor = false }
                        TextButton(onClick = { activeLesson = null }) { Text(uiText("Choose another topic")) }
                    }
                    notice?.let { Text(uiText(it), color = MaterialTheme.colorScheme.error) }
                }
            }
            if (destination == KidsDestination.CREATE) item(key = "create") {
                KidsCard {
                    KidsCreationPanel(idea, { idea = it.take(600) }, !submission.busy) {
                        prepareReply()
                        home.submitText(
                            "Help me write a short, playful story for a child about: " +
                                idea.trim().take(600) +
                                ". Make it interactive and invite me to choose what happens next."
                        )
                    }
                    if (submission.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                    submission.message?.let { Text(uiText(it),
                        color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    if (showReply && !submission.busy && replyKey != replyBaseline) {
                        ui.presentedResponse?.takeIf { it.isNotBlank() }?.let { response ->
                            Text(response, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                    ui.rejectionNotice?.let { Text(uiText(it.reasonText),
                        color = MaterialTheme.colorScheme.error) }
                }
            }
            if (destination == KidsDestination.ASK) item(key = "ask") {
                KidsCard {
                    Text(uiText("Ask Halo"), style = MaterialTheme.typography.titleLarge)
                    OutlinedTextField(question, { question = it }, label = { Text(uiText("My question or tool request")) },
                        modifier = Modifier.fillMaxWidth(), maxLines = 5)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { prepareReply(); home.submitText(question) }, enabled = question.isNotBlank() && !submission.busy) { Text(uiText("Ask")) }
                        OutlinedButton(onClick = ::listen) { Text(uiText("Talk to Halo")) }
                        OutlinedButton(onClick = { tools = true }) { Text(uiText("My tools")) }
                    }
                    if (submission.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                    submission.message?.let { Text(uiText(it), color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    if (showReply && !submission.busy && replyKey != replyBaseline)
                        ui.presentedResponse?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodyLarge) }
                    ui.rejectionNotice?.let { Text(uiText(it.reasonText), color = MaterialTheme.colorScheme.error) }
                }
            }
        }
    }
}

@Composable internal fun KidsCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(Modifier.fillMaxWidth(), shape = com.halo.app.ui.design.HaloShapes.card,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp), content = content)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable internal fun KidsModeHeader(signal: com.halo.app.ui.presence.HaloSignalPresentation,
    onListen: () -> Unit, onColours: () -> Unit, onExit: () -> Unit, onStop: () -> Unit,
    saveError: Boolean = false) {
    var options by remember { mutableStateOf(false) }
    if (options) ModalBottomSheet(onDismissRequest = { options = false }) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(uiText("Kids options"), style = MaterialTheme.typography.titleLarge)
            Text(uiText("An adult should help with these settings."),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedButton(onClick = { options = false; onColours() },
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                Text(uiText("Colours"))
            }
            OutlinedButton(onClick = { options = false; onExit() },
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                Text(uiText("Full Halo"))
            }
            Spacer(Modifier.height(20.dp))
        }
    }
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth().heightIn(min = 56.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = com.halo.app.ui.design.HaloShapes.pill,
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = .25f)) {
                Text(uiText("Kids"), Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    style = MaterialTheme.typography.labelLarge)
            }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onStop, modifier = Modifier.size(56.dp)) {
                Icon(painterResource(com.halo.app.R.drawable.halo_ic_stop),
                    contentDescription = uiText("Stop"))
            }
            IconButton(onClick = { options = true }, modifier = Modifier.size(56.dp)) {
                Icon(painterResource(com.halo.app.R.drawable.halo_ic_settings),
                    contentDescription = uiText("Kids options"))
            }
        }
        HaloSignalFace(signal, kids = true,
            size = if (androidx.compose.ui.platform.LocalDensity.current.fontScale > 1.3f) 128.dp else 160.dp,
            contentDescription = uiText("Talk to Halo"), onClick = onListen)
        Text(uiText("Hi there!"), style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center)
        Text(uiText("What shall we do today?"), style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        if (saveError) Text(uiText("Could not save your change. Try again."),
            color = MaterialTheme.colorScheme.error)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable internal fun KidsLearningStart(topic: String, onTopic: (String) -> Unit, busy: Boolean, onStart: () -> Unit) {
    OutlinedTextField(topic, onTopic, label = { Text(uiText("What would you like to learn?")) },
        modifier = Modifier.fillMaxWidth(), minLines = 1, maxLines = 3)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf("Maths", "Reading").forEach { label ->
            val localized = uiText(label)
            AssistChip(onClick = { onTopic(localized) }, label = { Text(localized) })
        }
    }
    Button(onClick = onStart, enabled = !busy && topic.isNotBlank(), modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
        Text(uiText(if (busy) "Starting lesson…" else "Let's begin"))
    }
}

/** A kids celebration must follow the same durable lifecycle as ordinary Study. */
internal fun kidsLessonCanFinish(
    lifecycle: StudySessionLifecycle?,
    busy: Boolean,
): Boolean = lifecycle == StudySessionLifecycle.ACTIVE && !busy

@OptIn(ExperimentalLayoutApi::class)
@Composable private fun KidsLesson(model: StudyViewModel, startRequested: Boolean, onStarted: () -> Unit) {
    val state by model.uiState.collectAsStateWithLifecycle()
    var lastLifecycle by remember(model) { mutableStateOf(state.session?.lifecycle) }
    var showKidsBloom by remember(model) { mutableStateOf(false) }
    LaunchedEffect(state.session?.lifecycle) {
        val current = state.session?.lifecycle
        val justCompleted = shouldCelebrateStudyCompletion(lastLifecycle, current)
        lastLifecycle = current
        if (justCompleted) {
            showKidsBloom = true
            delay(1000L) // supplied kids Bloom clip is 30 frames at 30fps
            showKidsBloom = false
        }
    }
    LaunchedEffect(state.loading, startRequested, state.session?.id) {
        if (startRequested && !state.loading && state.session != null) { onStarted(); model.askTutor() }
    }
    if (state.loading) { LinearProgressIndicator(Modifier.fillMaxWidth()); return }
    state.unavailable?.let { Text(uiText(it), color = MaterialTheme.colorScheme.error); return }
    Text(state.session?.goal ?: uiText("My lesson"), style = MaterialTheme.typography.titleMedium)
    if (state.isFinished) {
        if (showKidsBloom) Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            HaloFxAnimation(HaloFxMoment.BLOOM_KIDS, modifier = Modifier.size(124.dp))
        }
        Text(uiText("Lesson completed"), style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.primary)
    }
    state.turns.lastOrNull()?.let { turn ->
        val reader = state.reader?.takeIf { it.passageId ==
            "${state.session?.id}:turn:${turn.id}" }
        if (reader != null) {
            KidsReadAlongText(
                sentence = reader.current,
                activeRange = reader.wordRange.takeIf { reader.speaking },
            )
            // The real TTS progress caption sits above full child-sized
            // actions. A fixed Row clipped translated labels at 1.5× and 2×.
            Text(uiText(reader.progressLabel),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            FlowRow(Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = model::toggleReaderPlayback,
                    modifier = Modifier.heightIn(min = 56.dp),
                ) {
                    Text(uiText(if (reader.speaking) "Pause reading" else "Resume reading"))
                }
                OutlinedButton(
                    onClick = { model.stopReading("Stopped by learner") },
                    modifier = Modifier.heightIn(min = 56.dp),
                ) { Text(uiText("Stop reading")) }
            }
        } else Text(turn.text, style = MaterialTheme.typography.bodyLarge)
        turn.failure?.let { Text(uiText(it), color = MaterialTheme.colorScheme.error) }
        if (state.narrationAvailable && reader == null)
            TextButton(onClick = { model.readTurnAloud(turn) }) { Text(uiText("Read to me")) }
    }
    if (state.isGenerating || state.isSubmitting) LinearProgressIndicator(Modifier.fillMaxWidth())
    OutlinedTextField(state.draft, model::setDraft, label = { Text(uiText("My thinking")) },
        modifier = Modifier.fillMaxWidth(), minLines = 2, maxLines = 5, enabled = !state.isFinished)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = model::submitAttempt, enabled = state.canSubmitAttempt) { Text(uiText("Try my answer")) }
        OutlinedButton(onClick = model::askQuestion, enabled = state.canSubmitAttempt) { Text(uiText("Ask a question")) }
        OutlinedButton(onClick = { if (state.turns.isEmpty()) model.askTutor() else model.advance() }, enabled = !state.isGenerating && !state.isSubmitting && !state.isFinished) { Text(uiText(if (state.turns.isEmpty()) "Let's begin" else "Next step")) }
        if (!state.isFinished) {
            OutlinedButton(
                onClick = model::finishSession,
                enabled = kidsLessonCanFinish(state.session?.lifecycle, state.isGenerating || state.isSubmitting),
            ) { Text(uiText("Finish lesson")) }
        }
    }
    state.notice?.let { Text(uiText(it), color = MaterialTheme.colorScheme.onSurfaceVariant) }
}

/** The range comes from the real Android TTS onRangeStart event. Never
 * fabricate a word index from a clock or estimate where audio playback is.
 */
internal fun kidsTtsHighlightRange(sentence: String, range: IntRange?): IntRange? =
    range?.takeIf { it.first >= 0 && it.last >= it.first && it.last < sentence.length }

@Composable
internal fun KidsReadAlongText(sentence: String, activeRange: IntRange?) {
    val scheme = MaterialTheme.colorScheme
    val safe = kidsTtsHighlightRange(sentence, activeRange)
    val highlighted = buildAnnotatedString {
        append(sentence)
        if (safe != null) addStyle(
            SpanStyle(color = scheme.onPrimaryContainer,
                background = scheme.primaryContainer.copy(alpha = 0.65f)),
            safe.first, safe.last + 1,
        )
    }
    Text(highlighted, style = MaterialTheme.typography.bodyLarge,
        color = scheme.onSurface)
}

@Composable
internal fun KidsBrandTheme(content: @Composable () -> Unit) {
    val dark = MaterialTheme.colorScheme.background.luminance() < .5f
    val colors = com.halo.app.ui.theme.haloColorScheme(dark,
        com.halo.app.ui.theme.HaloPalette(com.halo.app.ui.theme.HaloAccent.MINT, com.halo.app.ui.theme.HaloTone.WARM))
    val type = MaterialTheme.typography
    MaterialTheme(colorScheme = colors.copy(
        primary = if (dark) androidx.compose.ui.graphics.Color(0xFFFF9F6B) else androidx.compose.ui.graphics.Color(0xFFA84B12),
        primaryContainer = androidx.compose.ui.graphics.Color(0xFFFF9F6B),
        onPrimaryContainer = androidx.compose.ui.graphics.Color(0xFF060330)),
        typography = type.copy(
            bodyLarge = type.bodyLarge.copy(fontSize = androidx.compose.ui.unit.TextUnit(18f, androidx.compose.ui.unit.TextUnitType.Sp)),
            bodyMedium = type.bodyMedium.copy(fontSize = androidx.compose.ui.unit.TextUnit(18f, androidx.compose.ui.unit.TextUnitType.Sp)),
            titleLarge = type.titleLarge.copy(fontSize = androidx.compose.ui.unit.TextUnit(24f, androidx.compose.ui.unit.TextUnitType.Sp)))) {
        CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 56.dp, content = content)
    }
}
