package com.halo.app.ui.screens.kids

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
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
import com.halo.app.ui.components.HaloVoiceTextActions
import com.halo.app.ui.components.HaloTextField
import com.halo.app.ui.components.VoiceFieldKind
import com.halo.app.ui.presence.HaloSignalFace
import com.halo.app.ui.presence.HaloSignalPresentation
import com.halo.app.ui.presence.HaloSignalState
import com.halo.app.ui.icons.HaloGlyph
import com.halo.app.ui.icons.HaloIcon
import com.halo.app.ui.fx.haloTrace
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
@Composable fun KidsModeScreen(home: HomeViewModel, onMicrophone: () -> Unit, onOpenCanvas: ((String) -> Unit)? = null) {
    CompositionLocalProvider(com.halo.app.ui.components.LocalPrivateSpeech provides true) {
        KidsBrandTheme { KidsModeContent(home, onMicrophone, onOpenCanvas) }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable private fun KidsModeContent(home: HomeViewModel, onMicrophone: () -> Unit, onOpenCanvas: ((String) -> Unit)?) {
    val scope = rememberCoroutineScope()
    val project: KidsProjectViewModel = viewModel()
    val projectState by project.state.collectAsStateWithLifecycle()
    val cloudGrant by ManualDependencyGraph.kidsPreferences.cloudAllowed.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    var voiceSheet by rememberSaveable { mutableStateOf(false) }
    var helpSheet by rememberSaveable { mutableStateOf(false) }
    var voiceTarget by rememberSaveable { mutableStateOf("TOPIC") }
    var voiceTargetSession by rememberSaveable { mutableStateOf<String?>(null) }
    var voiceRequest by remember { mutableLongStateOf(0) }
    var voiceNotice by remember { mutableStateOf<String?>(null) }
    val unknownKnowledge = uiText("Not yet")
    val discoveryHint = uiText("You can choose Not yet. We can start together.")
    var commandHeard by remember { mutableStateOf("") }
    var intakeBusy by remember { mutableStateOf(false) }
    var sourcesExpanded by rememberSaveable { mutableStateOf(false) }
    val fieldActivity by com.halo.app.ui.components.VoiceFieldCancellation.activeFields.collectAsStateWithLifecycle()
    var topic by rememberSaveable { mutableStateOf("") }
    var destination by rememberSaveable { mutableStateOf(KidsDestination.HOME) }
    var activeLesson by rememberSaveable { mutableStateOf<String?>(null) }
    var allowStartedLesson by remember { mutableStateOf(false) }
    var startTutor by remember { mutableStateOf(false) }
    var colours by rememberSaveable { mutableStateOf(false) }
    var saveError by remember { mutableStateOf(false) }
    val starter: StudyHomeViewModel = viewModel(key = "kids-study-home", factory = StudyHomeViewModel.Factory(
        ManualDependencyGraph.studyUseCases, ManualDependencyGraph.workspaceRepository, ManualDependencyGraph.workspaceFileRepository,
        ManualDependencyGraph.studySourceResolver, ManualDependencyGraph.studyLessonStore))
    val busy by starter.busy.collectAsStateWithLifecycle()
    val notice by starter.notice.collectAsStateWithLifecycle()
    val sessions by starter.sessions.collectAsStateWithLifecycle()
    LaunchedEffect(starter) { starter.navigation.collectLatest {
        if (allowStartedLesson) { activeLesson = it; destination = KidsDestination.LEARN; startTutor = true }
        allowStartedLesson = false
    } }
    val lesson: StudyViewModel? = activeLesson?.let { id -> viewModel(key = "kids-lesson-$id", factory = StudyViewModel.Factory(
        id, ManualDependencyGraph.studyUseCases, ManualDependencyGraph.studyTutorGateway,
        notes = ManualDependencyGraph.notebookUseCases, lessons = ManualDependencyGraph.studyLessonStore,
        workspace = ManualDependencyGraph.workspaceRepository, readerStorage = ManualDependencyGraph.storage)) }
    val learningState = lesson?.uiState?.collectAsStateWithLifecycle()?.value
    // Old saved Ask routes enter the guided local learning flow.
    LaunchedEffect(destination) { if (destination == KidsDestination.ASK) destination = KidsDestination.LEARN }
    DisposableEffect(lesson) { onDispose { lesson?.cancelTurn("Left Kids lesson"); lesson?.stopReading("Left Kids lesson") } }
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycle, lesson) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_STOP) { lesson?.stopReading("Kids page hidden"); home.cancelVoiceInput() }
        }
        lifecycle.lifecycle.addObserver(observer)
        onDispose { lifecycle.lifecycle.removeObserver(observer) }
    }
    var previousCloudGrant by remember { mutableStateOf(cloudGrant) }
    LaunchedEffect(cloudGrant) {
        if (previousCloudGrant && !cloudGrant) {
            project.stop()
            lesson?.cancelTurn("Parent turned off cloud sharing")
            home.cancelSession()
        }
        previousCloudGrant = cloudGrant
    }
    LaunchedEffect(destination, activeLesson, learningState?.turns?.lastOrNull()?.id, projectState.editing) {
        if (destination != KidsDestination.HOME) listState.scrollToItem(0)
    }
    if (colours) ModalBottomSheet(onDismissRequest = { colours = false }) {
        com.halo.app.ui.screens.settings.PaletteControls()
        Spacer(Modifier.height(24.dp))
    }
    val stopActions: () -> Unit = {
        // Stop is global for the Kids flow: invalidate field STT, pending permission
        // callbacks and camera intake before cancelling model/reader work.
        com.halo.app.ui.components.VoiceFieldCancellation.stop()
        voiceRequest = 0
        allowStartedLesson = false
        startTutor = false
        starter.cancelStart()
        home.cancelSession()
        project.stop()
        lesson?.cancelTurn("Stopped by learner")
        lesson?.stopReading()
    }
    fun openVoice(speak: Boolean) {
        stopActions()
        voiceNotice = null
        voiceTarget = when {
            destination == KidsDestination.CREATE && projectState.editing -> "DRAFT"
            destination == KidsDestination.CREATE -> "IDEA"
            destination == KidsDestination.LEARN && activeLesson != null -> "ANSWER"
            else -> "TOPIC"
        }
        voiceTargetSession = activeLesson
        if (voiceTarget == "TOPIC") {
            if (destination == KidsDestination.HOME) {
                starter.selectedIds.value.toList().forEach(starter::remove)
                topic = ""; sourcesExpanded = false
            }
            activeLesson = null; destination = KidsDestination.LEARN
        }
        voiceSheet = true
        voiceRequest = if (speak) System.nanoTime().coerceAtLeast(1) else 0
    }
    fun applyVoice(value: String) {
        when (voiceTarget) {
            "IDEA" -> project.editIdea(value)
            "DRAFT" -> project.editDraft(value)
            "ANSWER" -> if (activeLesson == voiceTargetSession) lesson?.setDraft(value)
                else { voiceNotice = "The learning step changed. Open voice input again." }
            else -> topic = value.take(300)
        }
    }
    fun command(value: String) {
        commandHeard = value
        val normalized = value.trim().lowercase(java.util.Locale.ROOT).trim('.', '!', '?')
        if (lesson == null && normalized !in setOf("stop", "стоп", "apturi", "zatrzymaj")) {
            voiceNotice = "Start a lesson to use this command."
            return
        }
        when (normalized) {
            "stop", "стоп", "apturi", "zatrzymaj" -> { stopActions(); helpSheet = false }
            "hint", "подсказка", "padoms", "wskazówka" -> if (lesson != null) {
                if (lesson.uiState.value.journey.stage == com.halo.app.ui.screens.study.LearningStage.DISCOVER) {
                    voiceNotice = discoveryHint; lesson.readLearningPrompt(discoveryHint)
                } else { lesson.requestGuidance(TutorStage.HINT); helpSheet = false }
            }
            "repeat", "повтори", "atkārto", "powtórz" -> lesson?.uiState?.value?.turns?.lastOrNull()?.let { lesson.readTurnAloud(it); helpSheet = false }
            "next step", "следующий шаг", "nākamais solis", "następny krok" -> if (lesson != null) {
                if (lesson.uiState.value.journey.stage == com.halo.app.ui.screens.study.LearningStage.DISCOVER)
                    lesson.discoverPriorKnowledge(unknownKnowledge)
                else if (lesson.uiState.value.journey.exercises.any { it.id == lesson.uiState.value.session?.currentExerciseId && it.finished })
                    lesson.nextHomeworkExercise()
                else lesson.advance()
                helpSheet = false
            }
            else -> voiceNotice = "Try: hint, repeat, next step, or stop."
        }
    }
    if (voiceSheet) ModalBottomSheet(onDismissRequest = { voiceSheet = false; voiceRequest = 0 }) {
        Column(Modifier.fillMaxWidth().heightIn(max = 600.dp).verticalScroll(androidx.compose.foundation.rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            val value = when (voiceTarget) { "IDEA" -> projectState.idea; "DRAFT" -> projectState.draft; "ANSWER" -> learningState?.draft.orEmpty(); else -> topic }
            val label = uiText(when (voiceTarget) { "IDEA" -> "My idea"; "DRAFT" -> "My project"; "ANSWER" -> "My thinking"; else -> "What would you like to learn?" })
            Text(label, style = MaterialTheme.typography.titleLarge)
            HaloTextField(value = value, onValueChange = ::applyVoice, kind = VoiceFieldKind.TEXT,
                label = { Text(label) }, minLines = 2, maxLines = 6,
                maxChars = when (voiceTarget) { "TOPIC" -> 300; "IDEA" -> 600; else -> 12000 },
                dictationRequest = voiceRequest, onRequestConsumed = { voiceRequest = 0 })
            voiceNotice?.let { Text(uiText(it)) }
            Button(onClick = { voiceSheet = false; voiceRequest = 0 }, modifier = Modifier.heightIn(min = 56.dp)) { Text(uiText("Done")) }
        }
    }
    if (helpSheet) ModalBottomSheet(onDismissRequest = { helpSheet = false }) {
        Column(Modifier.fillMaxWidth().heightIn(max = 600.dp).verticalScroll(androidx.compose.foundation.rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(uiText("What can I say?"), style = MaterialTheme.typography.titleLarge)
            Text(uiText("During a lesson: hint, repeat, next step, or stop. The same actions are available below."))
            HaloVoiceTextActions(text = "", onText = {}, onCommand = ::command)
            if (commandHeard.isNotBlank()) Text(commandHeard)
            voiceNotice?.let { Text(uiText(it)) }
            listOf("Hint", "Repeat", "Next step", "Stop").forEachIndexed { index, label ->
                TextButton(onClick = { command(listOf("hint", "repeat", "next step", "stop")[index]) },
                    enabled = index == 3 || lesson != null, modifier = Modifier.heightIn(min = 56.dp)) { Text(uiText(label)) }
            }
        }
    }
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {

        LazyColumn(Modifier.weight(1f).widthIn(max = 720.dp).fillMaxWidth(), state = listState,
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item(key = "header") {
                val signal = when {
                    learningState?.reader?.speaking == true -> HaloSignalPresentation(
                        state = HaloSignalState.SPEAKING, glyph = HaloGlyph.SPEAK, label = "Reading aloud")
                    learningState?.isGenerating == true || projectState.operation == "GENERATING" -> HaloSignalPresentation(
                        state = HaloSignalState.THINKING, glyph = HaloGlyph.THINK, label = "Thinking")
                    learningState?.reader?.preparing == true -> HaloSignalPresentation(
                        state = HaloSignalState.WAITING, glyph = HaloGlyph.SPEAK, label = "Preparing voice")
                    else -> HaloSignalPresentation(label = "Ready")
                }
                if (destination == KidsDestination.HOME) {
                    KidsModeHeader(signal, { openVoice(true) },
                        { colours = true },
                        { stopActions(); scope.launch {
                            try { ManualDependencyGraph.kidsPreferences.setEnabled(false) }
                            catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
                            catch (_: Exception) { saveError = true }
                        } },
                        stopActions, saveError)
                } else {
                    KidsActivityHeader(destination, signal,
                        onBack = { stopActions(); destination = KidsDestination.HOME },
                        onStop = stopActions)
                }
            }
            if (destination == KidsDestination.HOME) {
                val recent = sessions.filter { it.learnerLevel in setOf(StudyLearnerLevel.PRIMARY, StudyLearnerLevel.EARLY_PRIMARY) }
                    .maxByOrNull { it.updatedAtEpochMs }
                val hasProject = projectState.editing || projectState.kind != null || projectState.noteId != null
                if (recent != null || hasProject) item(key = "resume") {
                    val projectFirst = hasProject && projectState.updatedAt >= (recent?.updatedAtEpochMs ?: 0)
                    Surface(onClick = {
                        if (projectFirst) destination = KidsDestination.CREATE
                        else { activeLesson = recent?.id; destination = KidsDestination.LEARN }
                    }, modifier = Modifier.fillMaxWidth().heightIn(min = 104.dp)
                        .haloTrace(!projectState.busy && fieldActivity.isEmpty(), MaterialTheme.colorScheme.primary, lapOnAppear = true),
                        shape = com.halo.app.ui.design.HaloShapes.card,
                        color = MaterialTheme.colorScheme.surfaceContainerLow) {
                        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            HaloIcon(if (projectFirst) HaloGlyph.NOTE else HaloGlyph.READ_MODE, null, Modifier.size(32.dp))
                            Column(Modifier.weight(1f)) {
                                Text(uiText("Keep going"), style = MaterialTheme.typography.titleMedium)
                                Text(if (projectFirst) projectState.idea.ifBlank { uiText("My project") } else recent?.goal ?: uiText("My lesson"),
                                    style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }
            if (destination == KidsDestination.HOME) item(key = "choices") {
                KidsHomeChoices {
                    if (it == KidsDestination.LEARN) {
                        activeLesson = null; topic = ""; sourcesExpanded = false
                        starter.selectedIds.value.toList().forEach(starter::remove)
                    }
                    destination = it
                }
            }
            if (destination == KidsDestination.LEARN) item(key = "learn") {
                KidsCard {
                    Text(uiText("My learning adventure"), style = MaterialTheme.typography.titleLarge)
                    if (lesson == null) {
                        KidsLearningStart(topic, { topic = it }, busy || intakeBusy, {
                            allowStartedLesson = true; starter.start(topic, "", level = StudyLearnerLevel.PRIMARY,
                                style = StudySupportStyle.GUIDED, policy = StudyKnowledgePolicy.SOURCE_PLUS_GENERAL)
                        })
                        TextButton(onClick = { sourcesExpanded = !sourcesExpanded }, modifier = Modifier.heightIn(min = 56.dp)) { Text(uiText("Homework or my notes")) }
                        if (sourcesExpanded) KidsLearningSources(starter, onBusyChanged = { intakeBusy = it })
                        sessions.filter { it.learnerLevel in setOf(StudyLearnerLevel.PRIMARY, StudyLearnerLevel.EARLY_PRIMARY) }.take(2).forEach { session ->
                            OutlinedButton(onClick = { activeLesson = session.id }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text(session.goal ?: uiText("Continue learning")) }
                        }
                    } else {
                        KidsLesson(lesson, startTutor, blocking = voiceSheet || helpSheet || colours) { startTutor = false }
                        TextButton(onClick = { activeLesson = null }) { Text(uiText("Choose another topic")) }
                    }
                    notice?.let { Text(uiText(it), color = MaterialTheme.colorScheme.error) }
                }
            }
            if (destination == KidsDestination.CREATE) item(key = "create") {
                KidsCard { KidsCreateFlow(project, onOpenCanvas) }
            }
        }
        KidsVoiceDock(status = when {
            learningState?.reader?.speaking == true -> "Reading aloud"
            fieldActivity.isNotEmpty() -> "Voice input"
            projectState.operation == "SAVING" -> "Saving…"
            learningState?.isGenerating == true || projectState.operation == "GENERATING" -> "Preparing the next step"
            else -> "Talk or type"
        }, busy = fieldActivity.isNotEmpty() || learningState?.reader?.speaking == true || learningState?.isGenerating == true || projectState.busy,
            saving = projectState.operation == "SAVING", onSpeak = { openVoice(true) }, onType = { openVoice(false) },
            onStop = stopActions, onHelp = { voiceNotice = null; helpSheet = true })
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
    val largeText = androidx.compose.ui.platform.LocalDensity.current.fontScale >= 1.3f
    var options by remember { mutableStateOf(false) }
    var gateNotice by remember { mutableStateOf(false) }
    var optionSaveNotice by remember { mutableStateOf<String?>(null) }
    val openOptions = rememberKidsGrownUpGate({ options = true }, { gateNotice = true })
    if (gateNotice) AlertDialog(onDismissRequest = { gateNotice = false },
        title = { Text(uiText("Grown-up space")) },
        text = { Text(uiText("Ask a grown-up to set a device PIN or password in Android settings first.")) },
        confirmButton = { TextButton(onClick = { gateNotice = false }) { Text(uiText("OK")) } })
    if (options) ModalBottomSheet(onDismissRequest = { options = false }) {
        Column(Modifier.fillMaxWidth().heightIn(max = 600.dp)
            .verticalScroll(androidx.compose.foundation.rememberScrollState())
            .navigationBarsPadding().padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(uiText("Kids options"), style = MaterialTheme.typography.titleLarge)
            val preReader by ManualDependencyGraph.kidsPreferences.preReader.collectAsStateWithLifecycle()
            val optionsScope = rememberCoroutineScope()
            OutlinedButton(onClick = { optionsScope.launch {
                try { ManualDependencyGraph.kidsPreferences.setPreReader(!preReader); optionSaveNotice = null }
                catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
                catch (_: Exception) { optionSaveNotice = "Could not save your change. Try again." }
            } }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                Text(uiText(if (preReader) "Turn off pre-reader choices" else "Hear choices before choosing"))
            }
            Text(uiText("First tap reads a choice; tap it again to choose. TalkBack keeps its own controls."),
                style = MaterialTheme.typography.bodyMedium)
            optionSaveNotice?.let { Text(uiText(it), color = MaterialTheme.colorScheme.error) }
            val cloudAllowed by ManualDependencyGraph.kidsPreferences.cloudAllowed.collectAsStateWithLifecycle()
            Text(uiText("Cloud is off by default. For this app session, you can allow learning and creation text, selected photos and documents to reach configured providers. Privacy and source restrictions still apply."),
                style = MaterialTheme.typography.bodyMedium)
            OutlinedButton(onClick = { ManualDependencyGraph.kidsPreferences.setCloudAllowedForSession(!cloudAllowed) },
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                Text(uiText(if (cloudAllowed) "Turn off cloud for Kids" else "Allow cloud for this Kids session"))
            }
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
            if (largeText) HaloSignalFace(signal, kids = true, size = 40.dp,
                contentDescription = uiText("halo"))
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onStop, modifier = Modifier.size(56.dp)) {
                Icon(painterResource(com.halo.app.R.drawable.halo_ic_stop),
                    contentDescription = uiText("Stop"))
            }
            IconButton(onClick = openOptions, modifier = Modifier.size(56.dp)) {
                Icon(painterResource(com.halo.app.R.drawable.halo_ic_settings),
                    contentDescription = uiText("Kids options"))
            }
        }
        if (!largeText) {
            HaloSignalFace(signal, kids = true, size = 160.dp,
                contentDescription = uiText("Talk to Halo"), onClick = onListen)
            Text(uiText("Hi there!"), style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center)
        }
        Text(uiText("What shall we do today?"), style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        if (saveError) Text(uiText("Could not save your change. Try again."),
            color = MaterialTheme.colorScheme.error)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable internal fun KidsLearningStart(topic: String, onTopic: (String) -> Unit, busy: Boolean, onStart: () -> Unit) {
    HaloTextField(value = topic, onValueChange = onTopic, kind = VoiceFieldKind.TEXT, enabled = !busy,
        maxChars = 300, label = { Text(uiText("What would you like to learn?")) },
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
@Composable private fun KidsLesson(model: StudyViewModel, startRequested: Boolean, blocking: Boolean = false, onStarted: () -> Unit) {
    val state by model.uiState.collectAsStateWithLifecycle()
    val nextAction = com.halo.app.ui.screens.study.rememberLearningNextAction(state, kids = true, blocking = blocking)
    LaunchedEffect(state.loading, startRequested, state.session?.id) {
        if (startRequested && !state.loading && state.session != null) onStarted()
    }
    val context = androidx.compose.ui.platform.LocalContext.current
    val accessibility = context.getSystemService(android.content.Context.ACCESSIBILITY_SERVICE) as? android.view.accessibility.AccessibilityManager
    var spokenTurn by remember(model) { mutableStateOf<String?>(null) }
    LaunchedEffect(state.loading, state.turns.lastOrNull { it.exerciseId == state.session?.currentExerciseId }?.id, state.isGenerating) {
        val turn = state.turns.lastOrNull { it.exerciseId == state.session?.currentExerciseId }
        if (!state.loading && !state.isGenerating && turn != null && turn.failure == null && turn.id != spokenTurn) {
            spokenTurn = turn.id
            if (accessibility?.isTouchExplorationEnabled != true && com.halo.app.ui.components.VoiceFieldCancellation.activeFields.value.isEmpty())
                model.readTurnAloud(turn)
        }
    }
    if (state.loading) { LinearProgressIndicator(Modifier.fillMaxWidth()); return }
    state.unavailable?.let { Text(uiText(it), color = MaterialTheme.colorScheme.error); return }
    Text(state.session?.goal ?: uiText("My lesson"), style = MaterialTheme.typography.titleMedium)
    if (state.journey.stage == com.halo.app.ui.screens.study.LearningStage.DISCOVER) {
        com.halo.app.ui.screens.study.LearningJourneyPanel(state, model, kids = true, nextAction = nextAction)
        return
    }
    if (state.isFinished) {
        Text(uiText("Lesson completed"), style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.primary)
    }
    state.turns.lastOrNull { it.exerciseId == state.session?.currentExerciseId }?.let { turn ->
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
                    Text(uiText(if (reader.preparing) "Cancel voice preparation" else if (reader.speaking) "Pause reading" else "Resume reading"))
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
    com.halo.app.ui.screens.study.LearningJourneyPanel(state, model, kids = true, nextAction = nextAction)
    if (state.isGenerating || state.isSubmitting) LinearProgressIndicator(Modifier.fillMaxWidth())
    HaloTextField(value = state.draft, onValueChange = model::setDraft, kind = VoiceFieldKind.TEXT, label = { Text(uiText("My thinking")) },
        modifier = Modifier.fillMaxWidth(), minLines = 2, maxLines = 5, enabled = !state.isFinished && !state.isGenerating && !state.isSubmitting,
        guideDictation = nextAction == com.halo.app.ui.screens.study.LearningNextAction.MIC)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = model::submitAttempt, enabled = state.canSubmitAttempt) { Text(uiText("Try my answer")) }
        OutlinedButton(onClick = model::askQuestion, enabled = state.canSubmitAttempt) { Text(uiText("Ask a question")) }
        OutlinedButton(onClick = { if (state.turns.isEmpty()) model.askTutor() else model.advance() }, enabled = !state.isGenerating && !state.isSubmitting && !state.isFinished) { Text(uiText(if (state.turns.isEmpty()) "Let's begin" else "Next step")) }

    }
    com.halo.app.ui.screens.study.LearningHelpLadder(state, model, kids = true,
        guideHint = nextAction == com.halo.app.ui.screens.study.LearningNextAction.HINT)
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
    // BrandBook v2.3: inherit the user-selected accent; apricot is attention-only.
    val type = MaterialTheme.typography
    MaterialTheme(typography = type.copy(
            bodyLarge = type.bodyLarge.copy(fontSize = androidx.compose.ui.unit.TextUnit(18f, androidx.compose.ui.unit.TextUnitType.Sp)),
            bodyMedium = type.bodyMedium.copy(fontSize = androidx.compose.ui.unit.TextUnit(18f, androidx.compose.ui.unit.TextUnitType.Sp)),
            titleLarge = type.titleLarge.copy(fontSize = androidx.compose.ui.unit.TextUnit(24f, androidx.compose.ui.unit.TextUnitType.Sp)))) {
        CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 56.dp, content = content)
    }
}
