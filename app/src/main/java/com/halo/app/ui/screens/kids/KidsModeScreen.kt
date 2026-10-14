package com.halo.app.ui.screens.kids

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.halo.sdk.runtime.study.*
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/** One learning workspace, backed by the real Study and canonical Home/tool pipelines. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable fun KidsModeScreen(home: HomeViewModel, onMicrophone: () -> Unit) {
    val ui by home.uiState.collectAsStateWithLifecycle()
    val submission by home.textSubmission.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var topic by rememberSaveable { mutableStateOf("") }
    var question by rememberSaveable { mutableStateOf("") }
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
    LaunchedEffect(starter) { starter.navigation.collectLatest { activeLesson = it; startTutor = allowStartedLesson; allowStartedLesson = false } }
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
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(Modifier.widthIn(max = 720.dp).fillMaxSize().imePadding(),
            contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
            item(key = "header") {
                KidsModeHeader(ui.sessionState.toSignalPresentation(fallback = ui.runtimeState), ::listen,
                    { colours = true }, { scope.launch {
                            try { ManualDependencyGraph.kidsPreferences.setEnabled(false) }
                            catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
                            catch (_: Exception) { saveError = true }
                        } }, { allowStartedLesson = false; startTutor = false; home.cancelSession(); lesson?.cancelTurn("Stopped by learner"); lesson?.stopReading() }, saveError)
            }
            item(key = "learn") {
                KidsCard {
                    Text(uiText("My learning adventure"), style = MaterialTheme.typography.titleLarge)
                    if (lesson == null) {
                        KidsLearningStart(topic, { topic = it }, busy, {
                            allowStartedLesson = true; starter.start(topic, "", level = StudyLearnerLevel.PRIMARY,
                                style = StudySupportStyle.GUIDED, policy = StudyKnowledgePolicy.SOURCE_PLUS_GENERAL)
                        })
                        sessions.filter { it.learnerLevel in setOf(StudyLearnerLevel.PRIMARY, StudyLearnerLevel.EARLY_PRIMARY) }.take(2).forEach { session ->
                            OutlinedButton(onClick = { activeLesson = session.id }, modifier = Modifier.fillMaxWidth()) { Text(session.goal ?: uiText("Continue learning")) }
                        }
                    } else {
                        KidsLesson(lesson, startTutor) { startTutor = false }
                        TextButton(onClick = { activeLesson = null }) { Text(uiText("Choose another topic")) }
                    }
                    notice?.let { Text(uiText(it), color = MaterialTheme.colorScheme.error) }
                }
            }
            item(key = "ask") {
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
    Surface(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp), content = content)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable internal fun KidsModeHeader(signal: com.halo.app.ui.presence.HaloSignalPresentation,
    onListen: () -> Unit, onColours: () -> Unit, onExit: () -> Unit, onStop: () -> Unit, saveError: Boolean = false) {
    val largeText = androidx.compose.ui.platform.LocalDensity.current.fontScale > 1.3f
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        val heading: @Composable (Modifier) -> Unit = { modifier ->
            Column(modifier) {
                Text(uiText("Let's learn something!"), style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onBackground)
                Text(uiText("Ask, explore, and try it yourself."), style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (largeText) {
            HaloSignalFace(signal, size = 80.dp, contentDescription = uiText("Talk to Halo"), onClick = onListen)
            heading(Modifier.fillMaxWidth())
        } else Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            HaloSignalFace(signal, size = 80.dp, contentDescription = uiText("Talk to Halo"), onClick = onListen)
            heading(Modifier.weight(1f).padding(start = 16.dp))
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = onColours) { Text(uiText("Colours")) }
            TextButton(onClick = onExit) { Text(uiText("Full Halo")) }
            TextButton(onClick = onStop) { Text(uiText("Stop")) }
        }
        if (saveError) Text(uiText("Could not save your change. Try again."), color = MaterialTheme.colorScheme.error)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable internal fun KidsLearningStart(topic: String, onTopic: (String) -> Unit, busy: Boolean, onStart: () -> Unit) {
    OutlinedTextField(topic, onTopic, label = { Text(uiText("What would you like to learn?")) },
        modifier = Modifier.fillMaxWidth(), minLines = 1, maxLines = 3)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf("Maths", "Nature", "Reading").forEach { label ->
            val localized = uiText(label)
            AssistChip(onClick = { onTopic(localized) }, label = { Text(localized) })
        }
    }
    Button(onClick = onStart, enabled = !busy && topic.isNotBlank(), modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
        Text(uiText(if (busy) "Starting lesson…" else "Let's begin"))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable private fun KidsLesson(model: StudyViewModel, startRequested: Boolean, onStarted: () -> Unit) {
    val state by model.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(state.loading, startRequested, state.session?.id) {
        if (startRequested && !state.loading && state.session != null) { onStarted(); model.askTutor() }
    }
    if (state.loading) { LinearProgressIndicator(Modifier.fillMaxWidth()); return }
    state.unavailable?.let { Text(uiText(it), color = MaterialTheme.colorScheme.error); return }
    Text(state.session?.goal ?: uiText("My lesson"), style = MaterialTheme.typography.titleMedium)
    state.turns.lastOrNull()?.let { turn ->
        Text(turn.text, style = MaterialTheme.typography.bodyLarge)
        turn.failure?.let { Text(uiText(it), color = MaterialTheme.colorScheme.error) }
        if (state.narrationAvailable) TextButton(onClick = { model.readTurnAloud(turn) }) { Text(uiText("Read to me")) }
    }
    if (state.isGenerating || state.isSubmitting) LinearProgressIndicator(Modifier.fillMaxWidth())
    OutlinedTextField(state.draft, model::setDraft, label = { Text(uiText("My thinking")) },
        modifier = Modifier.fillMaxWidth(), minLines = 2, maxLines = 5, enabled = !state.isFinished)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = model::submitAttempt, enabled = state.canSubmitAttempt) { Text(uiText("Try my answer")) }
        OutlinedButton(onClick = model::askQuestion, enabled = state.canSubmitAttempt) { Text(uiText("Ask a question")) }
        OutlinedButton(onClick = { if (state.turns.isEmpty()) model.askTutor() else model.advance() }, enabled = !state.isGenerating && !state.isSubmitting && !state.isFinished) { Text(uiText(if (state.turns.isEmpty()) "Let's begin" else "Next step")) }
    }
    state.notice?.let { Text(uiText(it), color = MaterialTheme.colorScheme.onSurfaceVariant) }
}
