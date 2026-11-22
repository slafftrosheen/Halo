@file:OptIn(androidx.compose.animation.ExperimentalSharedTransitionApi::class)

package com.halo.app.ui.screens.home

import com.halo.app.ui.components.HaloTextField
import com.halo.app.ui.components.VoiceFieldKind
import androidx.compose.ui.graphics.luminance
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.AnimatedVisibilityScope
import com.halo.app.ui.presence.haloSharedOrbModifier
import com.halo.app.ui.fx.haloAura
import com.halo.app.ui.fx.HaloRipple
import com.halo.app.ui.fx.haloStateAura
import com.halo.app.ui.fx.HaloLight
import com.halo.app.ui.fx.haloAssistantEdge
import com.halo.app.ui.fx.haloEdgeGlow

import com.halo.app.language.uiText
import com.halo.app.ui.presence.HaloSignalState
import com.halo.app.ui.components.HaloActionButton
import com.halo.app.ui.components.HaloActionStyle
import com.halo.app.ui.design.HaloFocusAction
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.platform.LocalDensity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.halo.app.ui.glass.material.HaloRuntimeState
import com.halo.app.ui.presence.HaloSignalFace
import com.halo.app.ui.state.toSignalPresentation
import androidx.compose.material3.TextButton
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import com.halo.app.ui.icons.HaloGlyph
import com.halo.app.ui.icons.HaloIcon
import com.halo.app.ui.theme.rememberReducedMotion
import com.halo.runtime.cognition.context.CognitiveOperatingMode
import com.halo.sdk.provider.CognitiveSessionState
import com.halo.app.ai.label
import com.halo.sdk.provider.ProviderStatus

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onRequestMicrophonePermission: () -> Unit,
    onOpenProvider: (String) -> Unit = {},
    onConfigureVision: () -> Unit = {},
    onContextPrivacy: () -> Unit = {},
    onAssistantSetup: () -> Unit = {},
    onOpenNotificationInbox: () -> Unit = {},
    onOpenActivity: () -> Unit = {},
    onOpenCapture: (String) -> Unit = {},
    onDailyAction: (com.halo.app.daily.DailyAssistantAction) -> Unit = {},
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
    allowSharedOrb: Boolean = false,
    surfaceSwitcher: (@Composable () -> Unit)? = null,
    onOpenZone6HomeDestination: ((String) -> Unit)? = null,
    onStartNote: (() -> Unit)? = null,
) {
    val dashboard by viewModel.dashboard.collectAsStateWithLifecycle()
    val daily by viewModel.dailyAssistant.collectAsStateWithLifecycle()
    val selectedContext by (viewModel.workspace?.workingSet ?: kotlinx.coroutines.flow.flowOf(emptyList<String>())).collectAsStateWithLifecycle(emptyList())
    val context = androidx.compose.ui.platform.LocalContext.current
    val toolsOpenFailed = androidx.compose.ui.res.stringResource(com.halo.app.R.string.home_tools_open_failed)
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    var pendingPhoto by rememberSaveable { mutableStateOf<String?>(null) }
    var showCameraDenied by rememberSaveable { mutableStateOf(false) }
    val captureDraft by viewModel.captureQuestionDraft.collectAsStateWithLifecycle()
    val captureMessage by viewModel.captureMessage.collectAsStateWithLifecycle()
    val nextActions by viewModel.nextActions.collectAsStateWithLifecycle()
    val contextChips by viewModel.contextChips.collectAsStateWithLifecycle()
    val composerFocus by viewModel.composerFocus.collectAsStateWithLifecycle()
    val inkSelectionAskPromptRequest by viewModel.inkSelectionAskPromptRequest.collectAsStateWithLifecycle()
    val inkSelectionAskPrompt = uiText("Use the saved source reference and selected crop when attached to explain this region. If my question refers to another region or is ambiguous, ask me which one before answering. If the selection itself is unclear, ask me to describe it.")
    val inkSelectionAskInstruction = uiText("Use my saved ink selection as the referent.")
    val composerFocusRequester = remember { androidx.compose.ui.focus.FocusRequester() }
    val imagePicker = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri -> uri?.let(viewModel::attachImportedImage) }
    val documentPicker = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let(viewModel::importDocumentForContext) }
    val scope = rememberCoroutineScope()
    val camera = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.TakePicture()) { received ->
        val path = pendingPhoto
        pendingPhoto = null
        if (path != null) viewModel.handlePhoneCaptureResult(path, received)
    }
    fun launchCamera() {
        if (viewModel.captures == null) return
        scope.launch {
            try {
                val pending = viewModel.preparePhoneCapture() ?: return@launch
                pendingPhoto = pending.file.absolutePath
                camera.launch(pending.uri)
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                val path = pendingPhoto
                pendingPhoto = null
                if (path != null) viewModel.discardPhoneCapture(path)
                viewModel.captureMessage.value = "No camera app is available"
            }
        }
    }
    val cameraPermission = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) launchCamera() else {
            viewModel.captureMessage.value = "Camera permission was not granted"
            showCameraDenied = true
        }
    }
    // H03 recovery: denial does not dead-end in a message buried in the feed.
    // The preferred route uses the system image picker, which needs no camera permission.
    if (showCameraDenied) androidx.compose.material3.AlertDialog(
        onDismissRequest = { showCameraDenied = false },
        title = { Text(uiText("Camera permission was not granted")) },
        text = { Text(uiText("Choose an image instead, or allow camera access in Android settings.")) },
        confirmButton = {
            TextButton(onClick = {
                showCameraDenied = false
                imagePicker.launch("image/*")
            }) { Text(uiText("Import image")) }
        },
        dismissButton = {
            Column {
                TextButton(onClick = {
                    showCameraDenied = false
                    try {
                        context.startActivity(android.content.Intent(
                            android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            android.net.Uri.parse("package:" + context.packageName)
                        ))
                    } catch (_: android.content.ActivityNotFoundException) {
                        viewModel.captureMessage.value = "Open Android settings to allow camera access."
                    }
                }) { Text(uiText("Open settings")) }
                TextButton(onClick = { showCameraDenied = false }) { Text(uiText("Not now")) }
            }
        }
    )
    val clipboard = LocalClipboardManager.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val conversationTurns by viewModel.conversationTurns.collectAsStateWithLifecycle()
    val savedConversations by viewModel.savedConversations.collectAsStateWithLifecycle()
    val changingConversation by viewModel.conversationChanging.collectAsStateWithLifecycle()
    val toolCatalog by viewModel.toolCatalog.collectAsStateWithLifecycle()
    val importedSource by viewModel.importedSource.collectAsStateWithLifecycle()
    val importingSource by viewModel.importingSource.collectAsStateWithLifecycle()
    val sourceImportFailed by viewModel.sourceImportFailed.collectAsStateWithLifecycle()
    val bindingSource by viewModel.bindingSource.collectAsStateWithLifecycle()
    val currentTurns = conversationTurns.filter { it.conversationId == uiState.conversationId }

    val fxContinuity by viewModel.continuity.collectAsStateWithLifecycle()
    val providerRows by viewModel.providerRows.collectAsStateWithLifecycle()
    val chatProviderId by viewModel.chatProviderId.collectAsStateWithLifecycle()
    val chatProvider = providerRows.firstOrNull { it.descriptor.providerId == chatProviderId }
    val connectionNeedsAttention = chatProvider?.connection in setOf(
        com.halo.app.ai.ProviderConnectionState.AUTH_FAILED, com.halo.app.ai.ProviderConnectionState.OFFLINE,
        com.halo.app.ai.ProviderConnectionState.RATE_LIMITED, com.halo.app.ai.ProviderConnectionState.ERROR,
    )

    val listeningRequested by viewModel.listeningRequested.collectAsStateWithLifecycle()
    val voiceModeActive by viewModel.voiceModeActive.collectAsStateWithLifecycle()
    val voiceModeListeningPaused by viewModel.voiceModeListeningPaused.collectAsStateWithLifecycle()
    val speechOutputPaused by viewModel.speechOutputPaused.collectAsStateWithLifecycle()
    val voiceModeStartRequested by viewModel.voiceModeStartRequested.collectAsStateWithLifecycle()
    val voiceModeStartRequestId by viewModel.voiceModeStartRequestId.collectAsStateWithLifecycle()
    LaunchedEffect(voiceModeStartRequested, voiceModeStartRequestId, uiState.microphonePermissionGranted, uiState.speechReady) {
        if (!voiceModeStartRequested) return@LaunchedEffect
        when {
            !uiState.microphonePermissionGranted -> onRequestMicrophonePermission()
            !uiState.speechReady -> if (viewModel.consumeVoiceModeStartRequest()) viewModel.navigateToVoice()
            else -> if (viewModel.consumeVoiceModeStartRequest()) viewModel.startVoiceMode()
        }
    }
    val drafts by viewModel.composerDrafts.collectAsStateWithLifecycle()
    val draftKey = uiState.conversationId.orEmpty()
    val textInput = drafts[draftKey]?.text.orEmpty()
    var pendingHistoryDraft by remember(draftKey) { mutableStateOf<String?>(null) }
    fun updateDraft(value: String) { viewModel.updateComposerDraft(draftKey, value) }
    fun prepareEarlierQuestion(value: String) {
        if (textInput.isBlank() || textInput == value) updateDraft(value) else pendingHistoryDraft = value
    }
    var visibleTurnCount by rememberSaveable(draftKey) { mutableIntStateOf(20) }

    LaunchedEffect(inkSelectionAskPromptRequest, draftKey) {
        if (viewModel.consumeInkSelectionAskPrompt(inkSelectionAskPromptRequest)) {
            when {
                textInput.isBlank() -> updateDraft(inkSelectionAskPrompt)
                !textInput.contains(inkSelectionAskInstruction, ignoreCase = true) ->
                    updateDraft("$textInput\n\n$inkSelectionAskInstruction")
            }
        }
    }
    pendingHistoryDraft?.let { previousQuestion -> androidx.compose.material3.AlertDialog(
        onDismissRequest={pendingHistoryDraft=null},title={Text(uiText("Replace your draft?"))},
        text={Text(uiText("The earlier question will be copied into the message box for editing. Nothing is sent yet; saved messages stay unchanged."))},
        confirmButton={TextButton({updateDraft(previousQuestion);pendingHistoryDraft=null}) {Text(uiText("Use earlier question"))}},
        dismissButton={TextButton({pendingHistoryDraft=null}) {Text(uiText("Keep my draft"))}}) }
    LaunchedEffect(draftKey) {
        viewModel.loadComposerDraft(draftKey)
    }
    val submission by viewModel.textSubmission.collectAsStateWithLifecycle()
    LaunchedEffect(captureDraft) { captureDraft?.let {
        if (textInput.isNotBlank() && textInput != it) pendingHistoryDraft = it else updateDraft(it)
        viewModel.captureQuestionDraft.value = null
    } }
    val reducedMotion by rememberReducedMotion()
    val shortScreen = androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp < 480
    val keyboardVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    val lifecycleOwner = LocalLifecycleOwner.current


    val shellOwnsOrb = com.halo.app.ui.presence.LocalShellHaloOrb.current
    val startPreferences = remember(context.applicationContext) { HomeStartPreferences(context) }
    val layoutKept = com.halo.app.ui.theme.rememberHaloLayoutLocked()
    var homeStartSnapshot by remember { mutableStateOf<HomeStartSnapshot?>(null) }
    var homeStartDraft by remember { mutableStateOf<HomeStartLayout?>(null) }
    var savingHomeStarts by remember { mutableStateOf(false) }
    var homeStartSaveError by remember { mutableStateOf(false) }
    val homeStartLayout = homeStartDraft ?: homeStartSnapshot?.current ?: HomeStartLayout()
    val homeClock = androidx.compose.runtime.produceState(java.time.ZonedDateTime.now(), shellOwnsOrb) {
        while (shellOwnsOrb) {
            value = java.time.ZonedDateTime.now()
            kotlinx.coroutines.delay(60_000)
        }
    }.value
    val homeDate = homeClock.toLocalDate()
    val showWeather = shellOwnsOrb && onOpenZone6HomeDestination != null &&
        (homeStartDraft != null || homeStartLayout.greeting == HomeGreeting.WEATHER || HomeStart.WEATHER in homeStartLayout.starts)
    val weatherSource = androidx.compose.runtime.produceState<com.halo.app.everyday.collectors.CollectorState?>(null, showWeather) {
        if (!showWeather) {
            value = null
            return@produceState
        }
        try {
            com.halo.app.di.ManualDependencyGraph.collectorRepository.observe().collect { sources ->
                value = sources.firstOrNull { source ->
                    source.source.kind == com.halo.app.everyday.collectors.CollectorKind.WEATHER &&
                        source.source.selected && source.source.enabled && !source.source.muted
                }
            }
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            value = null
        }
    }.value
    val weather = weatherSource?.snapshot?.takeIf { homeClock.toInstant() < it.expiresAt }
        ?.payload?.let { it as? com.halo.app.everyday.collectors.CollectorPayload.Weather }?.fact
        ?.let { fact -> fact.temperatureC?.let { HomeWeather(fact.place.name, it) } }
    LaunchedEffect(shellOwnsOrb, startPreferences) {
        if (shellOwnsOrb && onOpenZone6HomeDestination != null) {
            try {
                homeStartSnapshot = startPreferences.load()
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                homeStartSnapshot = null
            }
        }
    }
    fun editHomeStarts() {
        if (layoutKept) return
        val snapshot = homeStartSnapshot ?: return
        focusManager.clearFocus()
        homeStartSaveError = false
        homeStartDraft = snapshot.current
    }
    val savedHomeStarts = homeStartSnapshot
    val draftHomeStarts = homeStartDraft
    if (savedHomeStarts != null && draftHomeStarts != null) {
        HomeStartEditor(draftHomeStarts, savedHomeStarts.current, savedHomeStarts.restorable(), homeDate, weather,
            savingHomeStarts, homeStartSaveError, onChange = { homeStartDraft = it; homeStartSaveError = false },
            onDismiss = { homeStartDraft = null },
            onApply = {
                if (!savingHomeStarts) {
                    savingHomeStarts = true
                    scope.launch {
                        try {
                            homeStartSnapshot = startPreferences.apply(savedHomeStarts, draftHomeStarts)
                            homeStartDraft = null
                        } catch (cancelled: kotlinx.coroutines.CancellationException) {
                            throw cancelled
                        } catch (_: Exception) {
                            homeStartSaveError = true
                        } finally {
                            savingHomeStarts = false
                        }
                    }
                }
            })
    }
    DisposableEffect(lifecycleOwner, viewModel, shellOwnsOrb) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshDashboard()
                viewModel.refreshToolCatalog()
            } else if (event == Lifecycle.Event.ON_STOP && !shellOwnsOrb) {
                viewModel.onHomeStopped()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            if (!shellOwnsOrb) viewModel.onHomeStopped()
        }
    }

    val isListening = uiState.inputStatus.capturing || uiState.speechActive || listeningRequested
    val isSpeechProcessing = uiState.speechProcessing || uiState.runtimeState == HaloRuntimeState.Recognizing
    val isSpeaking = uiState.runtimeState == HaloRuntimeState.Speaking
    val isActiveSession = uiState.sessionState != CognitiveSessionState.Idle &&
        uiState.sessionState !is CognitiveSessionState.Completed &&
        uiState.sessionState !is CognitiveSessionState.Failed
    val providerNeedsAttention = uiState.activeProvider == "No provider" ||
        uiState.activeProviderState in setOf(
            ProviderStatus.State.UNCONFIGURED,
            ProviderStatus.State.OFFLINE,
            ProviderStatus.State.MODEL_MISSING,
            ProviderStatus.State.UNAVAILABLE,
            ProviderStatus.State.ERROR
        )

    fun togglePresence() {
        when {
            isListening -> viewModel.stopListening()
            isSpeaking -> viewModel.stopSpeakingAndListen()
            isActiveSession -> viewModel.cancelSession()
            isSpeechProcessing -> viewModel.cancelVoiceInput()
            !uiState.microphonePermissionGranted -> onRequestMicrophonePermission()
            !uiState.speechReady -> viewModel.navigateToVoice()
            else -> viewModel.startListening()
        }
    }

    var liveFocusRequest by remember(draftKey) { mutableIntStateOf(0) }
    fun submitText() {
        val currentDraft = viewModel.composerDrafts.value[draftKey] ?: return
        val text = currentDraft.text.trim()
        if (text.isBlank() || submission.busy || isActiveSession || isListening || isSpeechProcessing || isSpeaking) return
        if (onOpenZone6HomeDestination != null && com.halo.app.ui.everyday.isDayReportQuestion(text)) {
            updateDraft("")
            focusManager.clearFocus()
            onOpenZone6HomeDestination(com.halo.app.ui.everyday.EverydayRoutes.BRIEF)
            return
        }
        liveFocusRequest += 1
        viewModel.submitComposerText(draftKey, currentDraft.revision, currentDraft.text)
    }

    val currentMode by viewModel.selectedMode.collectAsStateWithLifecycle()
    val presentation = homeFlowPresentation(uiState, submission, draftKey,
        listeningRequested, providerNeedsAttention || connectionNeedsAttention)
    // Aura is visual presence, not a claim of connectivity, speech capture or tool execution.
    val showReadyAura = uiState.runtimeState == HaloRuntimeState.Idle &&
        !isListening && !isSpeechProcessing && !isSpeaking &&
        !providerNeedsAttention && !connectionNeedsAttention && !presentation.needsReview
    val canSend = !changingConversation && !submission.busy && !isListening && !isSpeechProcessing && !isSpeaking && !isActiveSession
    val canClearChat = canSend && draftKey.isNotBlank() && (currentTurns.isNotEmpty() || textInput.isNotBlank())
    val responseText = (if (uiState.sessionState is CognitiveSessionState.Failed &&
        uiState.presentedIdentity?.source != ResponsePresentationSource.INCOMPLETE) ""
        else uiState.presentedResponse.orEmpty()).trim()
    // Attachments and quick actions live next to the pinned composer, never inside the scrolled feed.
    var showContext by rememberSaveable { mutableStateOf(false) }
    var showActions by rememberSaveable { mutableStateOf(false) }
    var toolGroup by rememberSaveable { mutableStateOf(HomeToolGroup.CREATE.name) }
    var toolEntry by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(viewModel, lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.orbCatalogRequest.collect { request -> request?.let {
                toolGroup = it.group.name; toolEntry = it.entry
                focusManager.clearFocus()
                viewModel.refreshToolCatalog(); showActions = true
                viewModel.consumeOrbCatalog(it)
            } }
        }
    }
    var showSelectionHelp by rememberSaveable { mutableStateOf(false) }
    var showHistory by rememberSaveable { mutableStateOf(false) }
    var showToday by rememberSaveable { mutableStateOf(false) }
    var webSearchDraft by rememberSaveable { mutableStateOf<String?>(null) }
    var showTaskDetails by rememberSaveable(draftKey) { mutableStateOf(false) }
    val offerPrivateChat: ((com.halo.app.data.PersistedConversation) -> Unit)? = onOpenZone6HomeDestination?.let { navigate ->
        { chat ->
            showHistory = false
            focusManager.clearFocus()
            com.halo.app.nativeactions.PrivateLibraryHandoff.offer(
                com.halo.app.nativeactions.PrivateLibraryTarget.Conversation(chat.id, chat.title, chat.updatedAt))
            navigate("vault")
        }
    }
    val privateCurrentChat = savedConversations.firstOrNull { it.id == uiState.conversationId }
        ?.takeIf { !changingConversation }?.let { chat -> offerPrivateChat?.let { protect -> { protect(chat) } } }
    val pendingQuestion = homePendingQuestion(submission, uiState.conversationId, currentTurns)
    val priorSavedReply = pendingQuestion != null && uiState.presentedIdentity?.source == ResponsePresentationSource.COMMITTED_CONVERSATION
    val liveReplyIdentity = homeVisibleReplyIdentity(uiState.presentedIdentity, responseText, priorSavedReply)
    if (showHistory) HomeConversationHistory(savedConversations, uiState.conversationId,
        onSelect = { showHistory = false; viewModel.openConversation(it) },
        onNew = { showHistory = false; viewModel.newConversation() }, onDismiss = { showHistory = false },
        onMakePrivate = offerPrivateChat?.takeUnless { changingConversation })
    webSearchDraft?.let { query -> com.halo.app.ui.online.GroundedWebSearchDialog(query) { webSearchDraft = null } }
    if (showToday) androidx.compose.material3.ModalBottomSheet(onDismissRequest = { showToday = false }) {
        Column(Modifier.fillMaxWidth().heightIn(max = 520.dp).verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp)) {
            Text(androidx.compose.ui.res.stringResource(com.halo.app.R.string.home_your_day), style = MaterialTheme.typography.titleLarge)
            onOpenZone6HomeDestination?.let { open ->
                TextButton({ showToday = false; open(com.halo.app.ui.everyday.EverydayRoutes.BRIEF) }) { Text(uiText("Open your day report")) }
            }
            DailyAssistantSection(daily, compact = true) { action ->
                viewModel.recordDailyAction(action); onDailyAction(action); showToday = false
            }
        }
    }

    var modeMenuExpanded by remember { mutableStateOf(false) }
    var quickAction by remember { mutableStateOf<String?>(null) }
    var quickValue by remember { mutableStateOf("") }
    var startingBriefRead by remember { mutableStateOf(false) }
    val openHomeStart: (HomeStart) -> Unit = { start ->
        val graph = com.halo.app.di.ManualDependencyGraph
        val routes = com.halo.app.ui.everyday.EverydayRoutes
        when (start) {
            HomeStart.TODAY -> onOpenZone6HomeDestination?.invoke(routes.ORGANIZER)
            HomeStart.NOTE -> onStartNote?.invoke()
            HomeStart.STUDY -> onOpenZone6HomeDestination?.invoke("study")
            HomeStart.CAMERA -> onOpenZone6HomeDestination?.invoke("scan?identify=true")
            HomeStart.TIMER -> onOpenZone6HomeDestination?.invoke("${routes.ORGANIZER}?createKind=TIMER")
            HomeStart.WEATHER -> onOpenZone6HomeDestination?.invoke(
                weatherSource?.source?.id?.let { routes.source(it) } ?: routes.SOURCES)
            HomeStart.BRIEF -> if (!startingBriefRead) {
                startingBriefRead = true
                scope.launch {
                    try {
                        (context.applicationContext as com.halo.app.HaloApplication).awaitOrganizerBootstrap()
                        graph.collectorBootstrap.ensureInitialized()
                        val brief = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                            graph.briefService.observeLatestVisible().first()
                                ?: graph.briefService.build(com.halo.app.everyday.briefing.BriefPeriod.ON_DEMAND)
                        }
                        graph.briefReadAloud.read(brief.id)
                    } catch (cancelled: kotlinx.coroutines.CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        onOpenZone6HomeDestination?.invoke(routes.BRIEF)
                    } finally {
                        startingBriefRead = false
                    }
                }
            }
            HomeStart.DRAW -> onOpenZone6HomeDestination?.invoke("studio")
        }
    }
    var showInboxCapture by rememberSaveable { mutableStateOf(false) }
    if (showInboxCapture) com.halo.app.ui.screens.notebook.InboxCaptureDialog { showInboxCapture = false }
    val researchPrompt = androidx.compose.ui.res.stringResource(com.halo.app.R.string.home_tool_research_prompt)
    val mapsPrompt = androidx.compose.ui.res.stringResource(com.halo.app.R.string.home_tool_maps_prompt)
    if (showActions) HomeToolsSheet(toolCatalog, voiceModeActive,
        onRefresh = viewModel::refreshToolCatalog, resolve = viewModel::resolveToolAction,
        onAction = { action ->
            showActions = false
            when (action) {
                HomeToolAction.NEW_NOTE -> viewModel.createNote()
                HomeToolAction.ATTACH -> showContext = true
                HomeToolAction.WEB -> webSearchDraft = textInput
                HomeToolAction.RESEARCH_DRAFT -> prepareEarlierQuestion(researchPrompt)
                HomeToolAction.MAPS_DRAFT -> prepareEarlierQuestion(mapsPrompt)
                HomeToolAction.NOTEBOOK, HomeToolAction.STUDIO, HomeToolAction.FILE_TOOLS, HomeToolAction.STUDY, HomeToolAction.CONNECTIONS -> viewModel.openToolScreen(action)
                HomeToolAction.TIMER -> { quickValue = "120"; quickAction = "Timer" }
                HomeToolAction.OPEN_APP -> { quickValue = ""; quickAction = "Open app" }
                HomeToolAction.START_VOICE -> viewModel.startVoiceMode()
                HomeToolAction.END_VOICE -> viewModel.stopVoiceMode()
                HomeToolAction.INBOX -> showInboxCapture = true
                HomeToolAction.REPEAT_VOICE -> viewModel.repeatLastVoiceResponse()
                HomeToolAction.VOICE_MODE, HomeToolAction.PLANNED -> Unit
            }
        }, onRecovery = { recovery ->
            showActions = false
            if (recovery == HomeToolRecovery.ANDROID_APPS) {
                try { context.startActivity(android.content.Intent(android.provider.Settings.ACTION_APPLICATION_SETTINGS)) }
                catch (_: android.content.ActivityNotFoundException) { viewModel.captureMessage.value = toolsOpenFailed }
                catch (_: SecurityException) { viewModel.captureMessage.value = toolsOpenFailed }
            } else viewModel.openToolRecovery(recovery)
        }, onDismiss = { showActions = false },
        initialGroup = HomeToolGroup.entries.firstOrNull { it.name == toolGroup } ?: HomeToolGroup.CREATE, initialEntry = toolEntry)
    importedSource?.let { source -> HomeImportedSourceDialog(source,
        onAttach = viewModel::attachImportedSource, onStudy = viewModel::openImportedSourceInStudy,
        onNotebook = { viewModel.dismissImportedSource(); viewModel.openToolScreen(HomeToolAction.NOTEBOOK) },
        onDismiss = viewModel::dismissImportedSource, binding = bindingSource) }
    if (sourceImportFailed) androidx.compose.material3.AlertDialog(
        onDismissRequest = { viewModel.sourceImportFailed.value = false },
        title = { Text(androidx.compose.ui.res.stringResource(com.halo.app.R.string.home_attach_import_failed)) },
        text = { Text(androidx.compose.ui.res.stringResource(com.halo.app.R.string.home_attach_original_kept)) },
        confirmButton = { TextButton(onClick = { viewModel.sourceImportFailed.value = false; documentPicker.launch(arrayOf("*/*")) }) {
            Text(uiText("Try again"))
        } }, dismissButton = { TextButton(onClick = { viewModel.sourceImportFailed.value = false }) { Text(uiText("Close")) } })
    if (showSelectionHelp) androidx.compose.material3.AlertDialog(
        onDismissRequest = { showSelectionHelp = false },
        title = { Text(androidx.compose.ui.res.stringResource(com.halo.app.R.string.home_attach_selection)) },
        text = { Text(androidx.compose.ui.res.stringResource(com.halo.app.R.string.home_attach_selection_detail)) },
        confirmButton = { TextButton(onClick = { showSelectionHelp = false; onAssistantSetup() }) {
            Text(androidx.compose.ui.res.stringResource(com.halo.app.R.string.home_attach_screen_setup))
        } }, dismissButton = { Column {
            TextButton(onClick = { showSelectionHelp = false; viewModel.navigateToDevices() }) {
                Text(androidx.compose.ui.res.stringResource(com.halo.app.R.string.home_attach_ink_device))
            }
            TextButton(onClick = { showSelectionHelp = false }) { Text(uiText("Close")) }
        } })
    var clearChatId by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(draftKey) {
        if (clearChatId != draftKey) clearChatId = null
    }
    fun requestClearChat() {
        if (canClearChat) {
            focusManager.clearFocus()
            clearChatId = draftKey
        }
    }
    clearChatId?.takeIf { it == draftKey }?.let { expectedConversation ->
        androidx.compose.material3.AlertDialog(onDismissRequest = { clearChatId = null },
            title = { Text(uiText("Clear this conversation?")) },
            text = { Text(uiText("This removes its messages. Your other conversations and Notebook are kept.")) },
            confirmButton = {
                HaloActionButton(onClick = { clearChatId = null; viewModel.clearConversation(expectedConversation) },
                    enabled = canClearChat, style = HaloActionStyle.STOP) {
                    Text(androidx.compose.ui.res.stringResource(com.halo.app.R.string.home_clear_chat))
                }
            },
            dismissButton = { TextButton(onClick = { clearChatId = null }) { Text(uiText("Keep messages")) } })
    }
    val actingOnPhone = (uiState.sessionState as? CognitiveSessionState.ExecutingTool)
        ?.toolId?.let(::homeIsPhoneAction) == true
    val feedScroll = rememberScrollState()
    var followLatest by rememberHomeFeedFollowing(feedScroll, draftKey)
    LaunchedEffect(liveFocusRequest) { if (liveFocusRequest > 0) followLatest = true }
    var observedResponse by remember(draftKey) { mutableStateOf(responseText) }
    var unseenReply by remember(draftKey) { mutableStateOf(false) }
    // H01: announce a new reply only when the response actually changes while
    // the user is reading earlier content; scrolling upward alone is not news.
    LaunchedEffect(responseText, followLatest) {
        val changed = responseText != observedResponse
        observedResponse = responseText
        if (followLatest) unseenReply = false
        else if (changed && responseText.isNotBlank()) unseenReply = true
    }
    var receiptBloom by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(receiptBloom) { if (receiptBloom != null) { kotlinx.coroutines.delay(1200); receiptBloom = null } }
    val motionStop by com.halo.app.ui.fx.HaloMotionEvents.stopEpoch.collectAsStateWithLifecycle()
    var seenMotionStop by remember { mutableLongStateOf(motionStop) }
    var lightStopped by remember { mutableStateOf(false) }
    var priorSessionActive by remember { mutableStateOf(isActiveSession) }
    LaunchedEffect(motionStop, isActiveSession) {
        if (motionStop != seenMotionStop) { seenMotionStop = motionStop; lightStopped = true }
        else if (isActiveSession && !priorSessionActive) lightStopped = false
        priorSessionActive = isActiveSession
    }
    val light = when {
        lightStopped -> HaloLight.OFF
        receiptBloom != null -> HaloLight.SUCCESS
        presentation.needsReview -> HaloLight.NEEDS_YOU
        uiState.inputStatus.capturing -> HaloLight.LISTENING
        actingOnPhone -> HaloLight.ACTING
        isActiveSession || isSpeechProcessing -> HaloLight.THINKING
        providerNeedsAttention || connectionNeedsAttention -> HaloLight.ERROR
        else -> HaloLight.IDLE
    }
    val changingLabel = androidx.compose.ui.res.stringResource(com.halo.app.R.string.zone5_conversation_changing)
    val homeOwner = com.halo.app.ui.presence.ShellPageOwner("home", objectId = uiState.conversationId,
        helpDescription = androidx.compose.ui.res.stringResource(com.halo.app.R.string.zone5_help_home),
        canonicalToolIds = listOf(com.halo.app.di.tools.host.SavedChatTools.LIST, com.halo.app.di.tools.host.SavedChatTools.SEARCH,
            com.halo.app.di.tools.host.SavedChatTools.GET, com.halo.app.di.tools.host.SavedChatTools.OPEN),
        defaultDisabledActionReason = if (changingConversation) changingLabel else androidx.compose.ui.res.stringResource(com.halo.app.R.string.action_wait),
        disabledActionReasons = mapOf("retry" to androidx.compose.ui.res.stringResource(when {
            providerNeedsAttention || connectionNeedsAttention -> com.halo.app.R.string.action_retry_answers
            textInput.isBlank() -> com.halo.app.R.string.action_enter_message
            else -> com.halo.app.R.string.action_wait
        }), "attach" to if (importingSource) androidx.compose.ui.res.stringResource(com.halo.app.R.string.home_attach_importing) else changingLabel,
            "new-chat" to changingLabel, "tools" to changingLabel))
    val composerContext: @Composable () -> Unit = {
        if (importingSource) {
            androidx.compose.material3.LinearProgressIndicator(Modifier.fillMaxWidth())
            Text(androidx.compose.ui.res.stringResource(com.halo.app.R.string.home_attach_importing), style = MaterialTheme.typography.bodySmall)
        }
        // H03: selected sources remain visible without opening the attachment sheet.
        // A single horizontally scrollable row avoids clipping the pinned composer
        // at 200% font scale, and a tap opens source actions/privacy.
        if (contextChips.isNotEmpty()) Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            contextChips.forEach { chip ->
                androidx.compose.material3.InputChip(
                    selected = true,
                    onClick = { showContext = true },
                    modifier = Modifier.widthIn(max = 260.dp).heightIn(min = 48.dp),
                    trailingIcon = {
                        IconButton(onClick = { viewModel.removeContext(chip.focus.category) }, modifier = Modifier.size(48.dp)) {
                            Icon(androidx.compose.ui.res.painterResource(com.halo.app.R.drawable.halo_ic_close),
                                androidx.compose.ui.res.stringResource(com.halo.app.R.string.home_remove_attachment, chip.title), Modifier.size(18.dp))
                        }
                    },
                    label = {
                        Text(chip.title, maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    }
                )
            }
        }
    }
    val homeComposer: @Composable () -> Unit = {
        Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = if (shellOwnsOrb) 0.dp else 6.dp)
            .background(MaterialTheme.colorScheme.surfaceContainerLow, androidx.compose.foundation.shape.RoundedCornerShape(26.dp)).padding(if (shellOwnsOrb) 2.dp else 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (!shellOwnsOrb) {
                composerContext()
                ComposerDraftNotice(viewModel, draftKey)
            }
            HaloComposer(
                draftIdentity = draftKey, draftRevision = drafts[draftKey]?.revision ?: 0L,
                readAcknowledgedDraft = { viewModel.composerDrafts.value[draftKey] },
                surfaceSwitcher = if (shellOwnsOrb) surfaceSwitcher else null,
                focusAllowed = true,
                focusRequester = composerFocusRequester,
                value = textInput, onValueChange = { updateDraft(it) }, onSubmit = { submitText() },
                onVoice = { togglePresence() },
                onStop = {
                    com.halo.app.ui.fx.HaloMotionEvents.stop()
                    when {
                        isListening || isSpeechProcessing -> viewModel.cancelVoiceInput()
                        isSpeaking -> viewModel.stopSpeakingAndListen()
                        else -> viewModel.cancelSession()
                    }
                },
                enabled = canSend && !presentation.needsReview &&
                    ((!providerNeedsAttention && !connectionNeedsAttention) ||
                        onOpenZone6HomeDestination != null && com.halo.app.ui.everyday.isDayReportQuestion(textInput)),
                voiceEnabled = !changingConversation && !presentation.needsReview && !providerNeedsAttention && !connectionNeedsAttention &&
                    !isListening && !isSpeechProcessing && (!isActiveSession || isSpeaking),
                voiceActive = isListening,
                voiceContentDescription = uiText(when {
                    !uiState.microphonePermissionGranted -> "Enable voice"
                    !uiState.speechReady -> "Set up voice"
                    isListening -> "Listening…"
                    isSpeaking -> "Interrupt reply and talk"
                    else -> "Talk"
                }),
                showStop = isActiveSession || submission.busy || isListening || isSpeechProcessing || isSpeaking,
                onAttach = if (shellOwnsOrb && keyboardVisible) null
                    else ({ focusManager.clearFocus(); showContext = true }),
                onTools = { focusManager.clearFocus(); toolGroup = HomeToolGroup.CREATE.name; toolEntry = null
                    viewModel.refreshToolCatalog(); showActions = true },
            )
            // The field must be attached before a navigation event requests focus.
            LaunchedEffect(composerFocus) { if (composerFocus > 0) composerFocusRequester.requestFocus() }
        }
    }
    if (shellOwnsOrb) {
        com.halo.app.ui.presence.BindShellUtilityBand(homeOwner, content = homeComposer)
        val todayLabel = androidx.compose.ui.res.stringResource(com.halo.app.R.string.home_your_day)
        val editLabel = androidx.compose.ui.res.stringResource(com.halo.app.R.string.home_start_edit)
        val showMeLabel = androidx.compose.ui.res.stringResource(com.halo.app.R.string.home_start_show_me)
        val pageGuidance = com.halo.app.ui.zone6.LocalHaloPageGuidance.current
        com.halo.app.ui.presence.BindShellPageActions(homeOwner, buildList {
            add(com.halo.app.ui.presence.ShellPageAction("attach", "Attach", HaloGlyph.FILE,
                enabled = !changingConversation && !importingSource) { focusManager.clearFocus(); showContext = true })
            add(com.halo.app.ui.presence.ShellPageAction("new-chat", androidx.compose.ui.res.stringResource(com.halo.app.R.string.zone5_new_chat), HaloGlyph.NOTE,
                enabled = !changingConversation, onInvoke = viewModel::newConversation))
            add(com.halo.app.ui.presence.ShellPageAction("history", "History", HaloGlyph.ACTIVITY) { showHistory = true })
            privateCurrentChat?.let { protect ->
                add(com.halo.app.ui.presence.ShellPageAction("private-chat",
                    androidx.compose.ui.res.stringResource(com.halo.app.R.string.halo11_private_lock_chat),
                    HaloGlyph.SECURITY, onInvoke = protect))
            }
            // Hosted Home has one status owner (the shell Orb); retain every recovery
            // callback in its current-page action lease instead of another header/strip.
            addAll(homeRecoveryActions(HomeRecoveryOptions(
                needsReview = presentation.needsReview,
                retryable = submission.retryable,
                needsRecovery = submission.needsRecovery,
                sameConversation = submission.conversationId == draftKey,
                canRetry = canSend && textInput.isNotBlank() && !providerNeedsAttention && !connectionNeedsAttention,
                answersNeedSetup = providerNeedsAttention || connectionNeedsAttention,
                microphoneGranted = uiState.microphonePermissionGranted,
                speechReady = uiState.speechReady),
                onActivity = onOpenActivity, onRetry = { submitText() },
                onAnswers = { chatProviderId?.let(onOpenProvider) ?: viewModel.navigateToIntelligence() },
                onMicrophone = onRequestMicrophonePermission, onVoice = viewModel::navigateToVoice))
            add(com.halo.app.ui.presence.ShellPageAction("tools", "Tools", HaloGlyph.TOOL,
                enabled = !changingConversation) {
                focusManager.clearFocus(); toolGroup = HomeToolGroup.CREATE.name; toolEntry = null
                viewModel.refreshToolCatalog(); showActions = true
            })
            add(com.halo.app.ui.presence.ShellPageAction("mode", homeModeLabel(currentMode), HaloGlyph.MODEL,
                enabled = !changingConversation && !isActiveSession && !isListening && !isSpeechProcessing) { modeMenuExpanded = true })
            add(com.halo.app.ui.presence.ShellPageAction("clear-chat", "Clear conversation", HaloGlyph.TRASH,
                enabled = canClearChat, onInvoke = ::requestClearChat))
            if (daily.items.isNotEmpty()) add(com.halo.app.ui.presence.ShellPageAction("today", todayLabel, HaloGlyph.HOME) { showToday = true })
            if (onOpenZone6HomeDestination != null) {
                add(com.halo.app.ui.presence.ShellPageAction("edit-home", editLabel, HaloGlyph.APPEARANCE,
                    enabled = !layoutKept && homeStartSnapshot != null && !changingConversation && !isListening && !isSpeechProcessing,
                    onInvoke = ::editHomeStarts))
                add(com.halo.app.ui.presence.ShellPageAction("show-me", showMeLabel, HaloGlyph.QUESTION,
                    enabled = !changingConversation && !isActiveSession && !isListening && !isSpeechProcessing) {
                    if (pageGuidance != null) pageGuidance.showControls() else com.halo.app.ui.zone6.Zone6GuideReplay.replay()
                })
            }
        })
        if (modeMenuExpanded) androidx.compose.material3.AlertDialog(
            onDismissRequest = { modeMenuExpanded = false },
            title = { Text(uiText("How halo answers")) },
            text = { Column(Modifier.verticalScroll(rememberScrollState())) {
                CognitiveOperatingMode.entries.forEach { mode ->
                    TextButton(enabled = !changingConversation && !isActiveSession && !isListening && !isSpeechProcessing,
                        onClick = { viewModel.selectedMode.value = mode; modeMenuExpanded = false }) {
                        Column(Modifier.weight(1f)) {
                            Text(uiText(homeModeLabel(mode)), style = MaterialTheme.typography.titleSmall)
                            Text(uiText(homeModeSummary(mode)), style = MaterialTheme.typography.bodySmall)
                        }
                        if (mode == currentMode) HaloIcon(HaloGlyph.SUCCESS, uiText("Selected"))
                    }
                }
            } },
            confirmButton = { TextButton(onClick = { modeMenuExpanded = false }) { Text(uiText("Close")) } })
    }
    val wallpaperVisible = shellOwnsOrb && com.halo.app.wallpaper.hasHaloHomeWallpaper()
    Box(Modifier.fillMaxSize().then(if (wallpaperVisible) Modifier
        else Modifier.background(com.halo.app.ui.theme.haloWorkspaceBackground()))
        .haloStateAura(light, MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.tertiary, MaterialTheme.colorScheme.error)
        .haloAssistantEdge(active = actingOnPhone && !lightStopped, attention = presentation.needsReview,
            accent = MaterialTheme.colorScheme.primaryContainer, apricot = MaterialTheme.colorScheme.tertiary),
        contentAlignment = Alignment.TopCenter) {
    Column(Modifier.widthIn(max = 760.dp).fillMaxSize().then(if (shellOwnsOrb) Modifier else Modifier.imePadding())) {
        val showStarts = shellOwnsOrb && !keyboardVisible && currentTurns.isEmpty() &&
            pendingQuestion == null && responseText.isBlank() && !isActiveSession &&
            !isListening && !isSpeechProcessing && onOpenZone6HomeDestination != null
        if (shellOwnsOrb && !keyboardVisible) {
            val canEditHome = !layoutKept && onOpenZone6HomeDestination != null && homeStartSnapshot != null &&
                !changingConversation && !isListening && !isSpeechProcessing
            val titleModifier = Modifier.padding(horizontal = 20.dp)
                .then(if (canEditHome) homeHeadingEdit(::editHomeStarts) else Modifier)
            if (showStarts) HomeStartHeader(homeStartLayout.greeting, homeClock.hour, homeDate, weather, titleModifier)
            else com.halo.app.ui.zone5.Zone5PageHeading(
                androidx.compose.ui.res.stringResource(com.halo.app.R.string.home_chat_title), modifier = titleModifier)
            Box(Modifier.padding(horizontal = 16.dp)) {
                HomeChatActions(canStartNew = !changingConversation, canClear = canClearChat,
                    onNew = viewModel::newConversation, onClear = ::requestClearChat,
                    onHistory = { showHistory = true })
            }
            if (layoutKept) Text(androidx.compose.ui.res.stringResource(com.halo.app.R.string.layout_kept),
                Modifier.padding(horizontal = 20.dp), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        // Standalone hosts retain local controls; the app shell projects them into Halo Zone.
        if (!shellOwnsOrb && (!shortScreen || !keyboardVisible)) Box(Modifier.fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = if (shortScreen) 4.dp else 12.dp)) {
            HomeChatHeader(mode = uiText(homeModeLabel(currentMode)),
                modeEnabled = !changingConversation && !isActiveSession && !isListening && !isSpeechProcessing,
                onMode = { modeMenuExpanded = true }, onHistory = { showHistory = true },
                onNew = viewModel::newConversation, onUpdates = onOpenNotificationInbox,
                onActivity = onOpenActivity, onClear = ::requestClearChat,
                onToday = if (daily.items.isNotEmpty()) ({ showToday = true }) else null,
                modeMenu = {
                    DropdownMenu(expanded = modeMenuExpanded, onDismissRequest = { modeMenuExpanded = false }) {
                        CognitiveOperatingMode.entries.forEach { mode ->
                            DropdownMenuItem(text = { Column {
                                Text(uiText(homeModeLabel(mode)))
                                Text(uiText(homeModeSummary(mode)), style = MaterialTheme.typography.bodySmall)
                            } }, trailingIcon = { if (mode == currentMode) HaloIcon(HaloGlyph.SUCCESS, null) },
                                onClick = {
                                    if (!isActiveSession && !isListening && !isSpeechProcessing) viewModel.selectedMode.value = mode
                                    modeMenuExpanded = false
                                })
                        }
                    }
                }, surfaceSwitcher = surfaceSwitcher, onMakePrivate = privateCurrentChat,
                chatActionsEnabled = !changingConversation, clearEnabled = canClearChat)

        }
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(feedScroll)
            .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)) {
            if (showStarts) {
                HomeStartButtons(homeStartLayout, enabled = { start ->
                    when (start) {
                        HomeStart.NOTE -> onStartNote != null
                        HomeStart.BRIEF -> !startingBriefRead && !isSpeaking
                        else -> true
                    }
                }, onOpen = openHomeStart)
            }
            if (chatProvider?.connection == com.halo.app.ai.ProviderConnectionState.OFFLINE) {
                com.halo.app.ui.zone6.Zone6OfflineLine(uiText("Online answers are unavailable · saved work stays on this phone"))
            }
            // Page earlier content without hiding turns based on equal answer text.
            if (currentTurns.size > visibleTurnCount) TextButton(onClick = {
                followLatest = false
                visibleTurnCount = (visibleTurnCount + 20).coerceAtMost(currentTurns.size)
            }) { Text(androidx.compose.ui.res.stringResource(com.halo.app.R.string.home_load_earlier)) }
            val canEditMessage = !isActiveSession && !isListening && !isSpeechProcessing && !isSpeaking
            currentTurns.takeLast(visibleTurnCount).forEach { turn ->
                androidx.compose.runtime.key(turn.id) {
                    if (turn.userText.isNotBlank()) HomeTranscriptMessage("${turn.id}:user", turn.userText, user = true,
                        onEdit = if (canEditMessage) ({ prepareEarlierQuestion(turn.userText) }) else null)
                    if (homeShowsHistoricalReply(turn, liveReplyIdentity)) {
                        val storedResponse = remember(turn) { PresentedAssistantResponse(
                            turn.conversationId, turn.id, turn.metadata["interactionId"], turn.metadata["requestId"],
                            turn.assistantText, ResponsePresentationSource.COMMITTED_CONVERSATION,
                            providerId = turn.metadata["providerId"],
                            structured = com.halo.runtime.cognition.interaction.PresentationMetadata.decode(turn.metadata)) }
                        HomeTranscriptMessage(
                        "${turn.id}:assistant", turn.assistantText, user = false,
                        onSave = if (canEditMessage) ({ viewModel.saveResponse(storedResponse) }) else null,
                        onEdit = if (canEditMessage && turn.userText.isNotBlank()) ({ prepareEarlierQuestion(turn.userText) }) else null,
                        canRead = canEditMessage,
                        origin = homeAnswerOrigin(providerRows.firstOrNull {
                            it.descriptor.providerId == turn.metadata["providerId"]
                        }?.descriptor?.location),
                        answerCards = {
                            HomeAnswerCards(storedResponse, turn.userText, providerRows.firstOrNull {
                                it.descriptor.providerId == storedResponse.providerId
                            }?.descriptor?.displayName ?: storedResponse.providerId, viewModel,
                                canEditMessage, ::prepareEarlierQuestion)
                        })
                    }
                }
            }
            pendingQuestion?.let { HomeTranscriptMessage("${submission.interactionId}:pending", it, user = true) }
            if (!shellOwnsOrb) HomePresenceHeader(
                signal = com.halo.app.ui.presence.haloOrbInputPresentation(uiState.sessionState.toSignalPresentation(
                    fallback = uiState.runtimeState, activityLevel = uiState.orb.inputLevel,
                    reducedMotion = reducedMotion), uiState.inputStatus.capturing, listeningRequested || uiState.speechActive,
                    isSpeechProcessing).let { signal ->
                    signal.copy(label = uiText(presentation.status), detail = null,
                        state = if (presentation.needsReview) HaloSignalState.WAITING else signal.state,
                        glyph = if (presentation.needsReview) HaloGlyph.APPROVAL else signal.glyph,
                        artwork = if (presentation.needsReview) null else signal.artwork,
                        conditionGlyph = if (presentation.needsReview) HaloGlyph.APPROVAL else null)
                },
                title = uiText(presentation.status),
                supporting = if (providerNeedsAttention || connectionNeedsAttention)
                    uiText("Choose how halo answers") else null,
                compact = currentTurns.isNotEmpty() || submission.phase != TextSubmissionPhase.READY || responseText.isNotBlank() || presentation.needsReview ||
                    uiState.sessionState is CognitiveSessionState.ExecutingTool,
                micOpen = uiState.inputStatus.capturing,
                inputLevel = uiState.inputStatus.inputLevel,
                aura = false,
                orbModifier = haloSharedOrbModifier(sharedTransitionScope, animatedVisibilityScope, allowSharedOrb),
                orbControl = { face -> com.halo.app.ui.presence.HomeOrbControl(viewModel,
                    onRequestMicrophonePermission, onOpen = { route -> when (route) {
                        "activity" -> onOpenActivity()
                        "voice_settings" -> viewModel.navigateToVoice()
                    } }, face = face, forceReview = presentation.needsReview) },
                action = when {
                    presentation.needsReview -> ({ IconButton(onClick = onOpenActivity, modifier = Modifier.size(48.dp)) {
                        HaloIcon(HaloGlyph.APPROVAL, uiText("Review"))
                    } })
                    submission.retryable && submission.conversationId == draftKey -> ({
                        TextButton(onClick = { submitText() }, enabled = canSend && textInput.isNotBlank()) { Text(uiText("Retry")) }
                    })
                    submission.needsRecovery && submission.conversationId == draftKey -> ({
                        IconButton(onClick = onOpenActivity, modifier = Modifier.size(48.dp)) { HaloIcon(HaloGlyph.ACTIVITY, uiText("Open Activity")) }
                    })
                    else -> null
                },
            )
            // Tool artwork is mounted ONLY while the active cognitive turn owns a
            // matching execution. It disappears when the runtime leaves ExecutingTool;
            // that transition alone is NOT proof of success.
            val executingToolId =
                (uiState.sessionState as? CognitiveSessionState.ExecutingTool)?.toolId
            if (executingToolId != null || uiState.longTaskProgress != null) TextButton(onClick = { showTaskDetails = !showTaskDetails }) {
                Text(androidx.compose.ui.res.stringResource(if (showTaskDetails) com.halo.app.R.string.home_hide_task_details else com.halo.app.R.string.home_task_details))
            }
            executingToolId?.let { id ->
                if (homeFxTool(id) != null) HomeToolExecutionFx(id, viewModel::cancelSession)
                else if (homeShouldShowUnmappedPhoneAction(id))
                    HomeUnmappedPhoneAction(viewModel::cancelSession)
            }
            // The recorded plan, when it exists, appears beside the active
            // action. No steps are inferred from a text request or tool name.
            val showActivePlan = homeShouldPromoteActivePlan(
                uiState.sessionState, uiState.longTaskProgress != null)
            if ((showActivePlan || showTaskDetails) && uiState.workflowSteps.isNotEmpty()) {
                Text(uiText("Plan"), style = MaterialTheme.typography.titleMedium)
                uiState.workflowSummary?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                uiState.workflowSteps.forEachIndexed { index, step -> Text("${index + 1}. $step", style = MaterialTheme.typography.bodyMedium) }
            }
            if (showActivePlan) uiState.longTaskProgress?.let { plan ->
                HomeTaskProgress(plan)
            }
            HomeToolResultFx(uiState.toolReceipt, uiState.conversationId, isActiveSession,
                if (fxContinuity.conversationId == uiState.conversationId) fxContinuity.interactionId else submission.interactionId,
                onConfirmed = { receiptBloom = it })
            // Use the executing turn's provider, not the selected-provider label.
            // A LAN endpoint is not a cloud transfer, even when it is off-device.
            val cloudProvider = cloudHandoffProviderId(
                (uiState.sessionState as? CognitiveSessionState.Reasoning)?.providerId,
                providerRows.associate { it.descriptor.providerId to it.descriptor.location }
            )
            HomeCloudHandoffFx(cloudProvider)
            if (!shellOwnsOrb) when {
                providerNeedsAttention || connectionNeedsAttention -> HaloFocusAction(
                    text = uiText("Set up answers"), glyph = HaloGlyph.MODEL, enabled = true,
                    active = canSend && !presentation.needsReview,
                    style = if (canSend && !presentation.needsReview) HaloActionStyle.PRIMARY else HaloActionStyle.SECONDARY,
                    onClick = { chatProviderId?.let(onOpenProvider) ?: viewModel.navigateToIntelligence() })
                !uiState.microphonePermissionGranted -> TextButton(onClick = onRequestMicrophonePermission) { Text(uiText("Enable voice")) }
                !uiState.speechReady -> TextButton(onClick = viewModel::navigateToVoice) { Text(uiText("Set up voice")) }
            }
            if (homeShouldShowThinkingSkeleton(uiState.sessionState, if (priorSavedReply) "" else responseText,
                    presentation.needsReview)) HomeThinkingSkeleton()
            HomeAutoReadAnswers(uiState, responseText, isActiveSession, submission)
            if (responseText.isNotBlank() && !priorSavedReply) {
                val identity = uiState.presentedIdentity
                val originalQuestion = currentTurns.firstOrNull { it.id == identity?.turnId }?.userText
                val answerCards: (@Composable () -> Unit)? = if (identity?.turnId != null && uiState.nativeReceiptCopy == null) {
                    {
                        HomeAnswerCards(identity, originalQuestion, providerRows.firstOrNull {
                            it.descriptor.providerId == identity.providerId
                        }?.descriptor?.displayName ?: identity.providerId, viewModel, canEditMessage,
                            ::prepareEarlierQuestion, delayEntry = true)
                    }
                } else null
                HomeResponseCard(uiState, responseText, isActiveSession,
                    origin = homeAnswerOrigin(providerRows.firstOrNull {
                        it.descriptor.providerId == uiState.presentedIdentity?.providerId
                    }?.descriptor?.location, walkedNativePath = uiState.nativeReceiptCopy != null),
                    onCopy = { uiState.presentedIdentity?.takeIf { it.turnId != null }?.let { clipboard.setText(AnnotatedString(it.text)) } },
                    onSave = { uiState.presentedIdentity?.let(viewModel::saveResponse) },
                    onEdit = if (canEditMessage) originalQuestion?.takeIf(String::isNotBlank)
                        ?.let { question -> ({ prepareEarlierQuestion(question) }) } else null,
                    answerCards = answerCards)
            } else if (currentTurns.isEmpty() && pendingQuestion == null && canSend && !presentation.needsReview &&
                !submission.needsRecovery && textInput.isBlank() && !(shellOwnsOrb && onOpenZone6HomeDestination != null)) {
                HomeWelcome(compact = true, onDraft = { updateDraft(it); composerFocusRequester.requestFocus() })
            }
            uiState.rejectionNotice?.let { notice ->
                val speechFailure = homeSpeechFailurePresentation(notice)
                val stoppedByUser = notice.reasonText == "Input interrupted. You can try again or type your question."
                Surface(
                    shape = com.halo.app.ui.design.HaloShapes.control,
                    color = if (stoppedByUser) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.errorContainer,
                    contentColor = if (stoppedByUser) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
                ) {
                    Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                        Text(
                              uiText(speechFailure?.title ?: when {
                                  stoppedByUser -> "Listening stopped"
                                  notice.outputFailure -> "Couldn’t play the reply"
                                  else -> "Couldn’t use that voice input"
                              }),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                              uiText(speechFailure?.body ?: notice.reasonText),
                            style = MaterialTheme.typography.bodyMedium
                          )
                          notice.transcript?.let { transcript ->
                              Text(transcript, style = MaterialTheme.typography.bodyMedium)
                              TextButton(enabled = notice.conversationId == null || notice.conversationId == uiState.conversationId,
                                  onClick = { updateDraft(transcript); viewModel.dismissSpeechNotice() }) { Text(uiText("Edit as text")) }
                          }
                          androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                              if (speechFailure?.offerTyping == true) TextButton(onClick = { composerFocusRequester.requestFocus() }) { Text(uiText("Type instead")) }
                              else if (!notice.outputFailure) TextButton(onClick = { togglePresence() }) { Text(uiText("Try again")) }
                              TextButton(onClick = { viewModel.navigateToVoice() }) { Text(uiText("Speech settings")) }
                              TextButton(onClick = { viewModel.dismissSpeechNotice() }) { Text(uiText("Dismiss")) }
                          }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
            captureMessage?.let {
                Text(if (it == "Conversation unavailable") androidx.compose.ui.res.stringResource(com.halo.app.R.string.home_conversation_unavailable)
                    else uiText(it), style = MaterialTheme.typography.bodyMedium)
                if (it in setOf("Microphone permission denied.", "Camera permission was not granted", "Open Android settings to allow camera access."))
                    com.halo.app.ui.components.AndroidPermissionHelp()
            }
            if (uiState.sessionState is CognitiveSessionState.Failed)
                com.halo.app.ui.components.CloudModeResolution(required = false)
            val captureRecovery by com.halo.app.di.ManualDependencyGraph.contextualCapture.notice.collectAsStateWithLifecycle()
            captureRecovery?.let {
                Text(uiText(it), style = MaterialTheme.typography.bodyMedium)
                TextButton(onClick = { com.halo.app.di.ManualDependencyGraph.contextualCapture.discard() }) {
                    Text(uiText("Dismiss"))
                }
            }
            val contextualCapture by com.halo.app.di.ManualDependencyGraph.contextualCapture.state.collectAsStateWithLifecycle()
            contextualCapture?.let { TextButton(onClick = com.halo.app.di.ManualDependencyGraph.contextualCapture::reopen) { Text(uiText("Review captured image")) } }
            if (responseText.isNotBlank() && contextualCapture?.selectedAction == com.halo.app.capture.CaptureAction.EXTRACT)
                Text(uiText("Model-extracted draft · Verify visible text and uncertain values before using them"), style = MaterialTheme.typography.bodyMedium)
            ContinuityControls(viewModel, isActiveSession || isSpeaking, onOpenCapture)
            if (voiceModeActive) Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
                Text(uiText(if (voiceModeListeningPaused) "Voice Mode is on. Listening is paused."
                    else "Voice Mode is on. Halo listens again after a spoken reply."),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (isSpeaking || speechOutputPaused) {
                        TextButton(onClick = if (speechOutputPaused) viewModel::resumeVoiceModeReply else viewModel::pauseVoiceModeReply,
                            modifier = Modifier.heightIn(min = 48.dp)) {
                            Text(uiText(if (speechOutputPaused) "Resume reply" else "Pause reply"))
                        }
                    }
                    TextButton(onClick = viewModel::toggleVoiceModeListening, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text(uiText(if (voiceModeListeningPaused) "Resume listening" else "Pause listening"))
                    }
                    TextButton(onClick = viewModel::stopVoiceMode, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text(uiText("End Voice Mode"))
                    }
                }
            }
            if (showTaskDetails && !showActivePlan) uiState.longTaskProgress?.let {
                HomeTaskProgress(it)
            }
            if (!isActiveSession && !isListening && !isSpeechProcessing && !isSpeaking) {
                HomeNextForYou(nextActions, onPick = { viewModel.performNextAction(it) })
            }


        }
        if (!followLatest) Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.End) {
            TextButton(onClick = {
                scope.launch {
                    followLatest = true
                    if (reducedMotion) feedScroll.scrollTo(feedScroll.maxValue) else feedScroll.animateScrollTo(feedScroll.maxValue)
                    unseenReply = false
                }
            }, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(if (unseenReply) uiText("New reply ↓") else androidx.compose.ui.res.stringResource(com.halo.app.R.string.home_return_to_current))
            }
        }
        if (shellOwnsOrb) Column(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (!keyboardVisible) {
                composerContext()
                HomeComposerNotice(homeComposerNotice(
                    providerNeedsAttention || connectionNeedsAttention, submission, draftKey,
                    active = isListening || isSpeechProcessing || isSpeaking || isActiveSession || presentation.needsReview,
                ))
                if (providerNeedsAttention || connectionNeedsAttention) TextButton(
                    onClick = { chatProviderId?.let(onOpenProvider) ?: viewModel.navigateToIntelligence() },
                    modifier = Modifier.heightIn(min = 48.dp),
                ) { Text(uiText("Set up answers")) }
            }
            ComposerDraftNotice(viewModel, draftKey)
        } else homeComposer()
        // H04: forms are screen-level overlays, independent of chat scrolling.
        quickAction?.let { action ->
            val command = if (action == "Timer") {
                quickValue.toIntOrNull()?.takeIf { it in 1..86400 }
                    ?.let { "Set a timer for $it seconds" }
            } else {
                quickValue.trim().takeIf { it.isNotBlank() }?.let { "Open $it" }
            }
            val ready = canSend && !providerNeedsAttention && !connectionNeedsAttention
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { quickAction = null },
                title = { Text(uiText(action)) },
                text = {
                    HaloTextField(
                        value = quickValue,
                        onValueChange = { quickValue = it.take(100) },
                        label = { Text(uiText(if (action == "Timer") "Seconds" else "Application name")) },
                        kind = if (action == "Timer") VoiceFieldKind.NUMBER else VoiceFieldKind.SHORT,
                        maxChars = 100
                    )
                },
                confirmButton = {
                    HaloActionButton(enabled = ready && command != null, onClick = {
                        if (command != null && ready) {
                            viewModel.submitText(command)
                            quickAction = null
                        }
                    }) { Text(uiText("Run")) }
                },
                dismissButton = {
                    TextButton(onClick = { quickAction = null }) { Text(uiText("Cancel")) }
                }
            )
        }
        // Review confirmation must remain composed even when attachments are closed.
        ContextContinuityControls(viewModel, expanded = false, onImport = {})
        if (showContext) HomeAttachmentSheet(onDismiss = { showContext = false }) {
                    ContextContinuityControls(viewModel, expanded = true,
                        onImport = { showContext = false; imagePicker.launch("image/*") },
                        onCamera = {
                            showContext = false
                            if (context.checkSelfPermission(android.Manifest.permission.CAMERA) ==
                                android.content.pm.PackageManager.PERMISSION_GRANTED) launchCamera()
                            else cameraPermission.launch(android.Manifest.permission.CAMERA)
                        }, onPrivacy = { showContext = false; onContextPrivacy() },
                        onVision = { showContext = false; onConfigureVision() }, showSaveReview = false,
                        onFile = { showContext = false; documentPicker.launch(arrayOf("*/*")) },
                        onAudio = { showContext = false; showInboxCapture = true },
                        onSelection = { showContext = false; showSelectionHelp = true }, importingFile = importingSource)
        }
    }
    }
}
