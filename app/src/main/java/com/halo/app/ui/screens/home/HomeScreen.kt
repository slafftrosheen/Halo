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
) {
    val dashboard by viewModel.dashboard.collectAsStateWithLifecycle()
    val daily by viewModel.dailyAssistant.collectAsStateWithLifecycle()
    val selectedContext by (viewModel.workspace?.workingSet ?: kotlinx.coroutines.flow.flowOf(emptyList<String>())).collectAsStateWithLifecycle(emptyList())
    val context = androidx.compose.ui.platform.LocalContext.current
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
    var drafts by rememberSaveable { mutableStateOf(hashMapOf<String, String>()) }
    val draftKey = uiState.conversationId.orEmpty()
    val textInput = drafts[draftKey].orEmpty()
    var pendingHistoryDraft by remember(draftKey) { mutableStateOf<String?>(null) }
    fun updateDraft(value: String) { drafts = HashMap(drafts).apply { put(draftKey, value) } }
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
        if (draftKey.isNotBlank() && drafts[draftKey].isNullOrBlank() && !drafts[""].isNullOrBlank()) {
            drafts = HashMap(drafts).apply { put(draftKey, remove("").orEmpty()) }
        }
    }
    val submission by viewModel.textSubmission.collectAsStateWithLifecycle()
    var clearedSubmission by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(submission) {
        val key = submission.conversationId ?: return@LaunchedEffect
        if (submission.accepted && clearedSubmission != submission.interactionId) {
            if (drafts[key]?.trim() == submission.draft) drafts = HashMap(drafts).apply { put(key, "") }
            clearedSubmission = submission.interactionId
        }
        if (submission.needsRecovery && drafts[key].isNullOrBlank()) drafts = HashMap(drafts).apply { put(key, submission.draft) }
    }
    LaunchedEffect(captureDraft) { captureDraft?.let {
        if (textInput.isNotBlank() && textInput != it) pendingHistoryDraft = it else updateDraft(it)
        viewModel.captureQuestionDraft.value = null
    } }
    val reducedMotion by rememberReducedMotion()
    val shortScreen = androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp < 480
    val keyboardVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    val lifecycleOwner = LocalLifecycleOwner.current


    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshDashboard()
                viewModel.refreshToolCatalog()
            } else if (event == Lifecycle.Event.ON_STOP) {
                viewModel.onHomeStopped()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer); viewModel.onHomeStopped() }
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

    fun submitText() {
        val text = textInput.trim()
        if (text.isBlank() || submission.busy || isActiveSession || isListening || isSpeechProcessing || isSpeaking) return
        viewModel.submitText(text)
    }

    val currentMode by viewModel.selectedMode.collectAsStateWithLifecycle()
    val presentation = homeFlowPresentation(uiState, submission, draftKey,
        listeningRequested, providerNeedsAttention || connectionNeedsAttention)
    // Aura is visual presence, not a claim of connectivity, speech capture or tool execution.
    val showReadyAura = uiState.runtimeState == HaloRuntimeState.Idle &&
        !isListening && !isSpeechProcessing && !isSpeaking &&
        !providerNeedsAttention && !connectionNeedsAttention && !presentation.needsReview
    val canSend = !changingConversation && !submission.busy && !isListening && !isSpeechProcessing && !isSpeaking && !isActiveSession
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
    val pendingQuestion = homePendingQuestion(submission, uiState.conversationId, currentTurns)
    val priorSavedReply = pendingQuestion != null && uiState.presentedIdentity?.source == ResponsePresentationSource.COMMITTED_CONVERSATION
    val liveReplyIdentity = homeVisibleReplyIdentity(uiState.presentedIdentity, responseText, priorSavedReply)
    if (showHistory) HomeConversationHistory(savedConversations, uiState.conversationId,
        onSelect = { showHistory = false; viewModel.openConversation(it) },
        onNew = { showHistory = false; viewModel.newConversation() }, onDismiss = { showHistory = false })
    webSearchDraft?.let { query -> com.halo.app.ui.online.GroundedWebSearchDialog(query) { webSearchDraft = null } }
    if (showToday) androidx.compose.material3.ModalBottomSheet(onDismissRequest = { showToday = false }) {
        Column(Modifier.fillMaxWidth().heightIn(max = 520.dp).verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp)) {
            Text(androidx.compose.ui.res.stringResource(com.halo.app.R.string.home_your_day), style = MaterialTheme.typography.titleLarge)
            DailyAssistantSection(daily, compact = true) { action ->
                viewModel.recordDailyAction(action); onDailyAction(action); showToday = false
            }
        }
    }

    var modeMenuExpanded by remember { mutableStateOf(false) }
    var quickAction by remember { mutableStateOf<String?>(null) }
    var quickValue by remember { mutableStateOf("") }
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
                HomeToolAction.NOTEBOOK, HomeToolAction.STUDIO, HomeToolAction.STUDY, HomeToolAction.CONNECTIONS -> viewModel.openToolScreen(action)
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
                catch (_: android.content.ActivityNotFoundException) { viewModel.captureMessage.value = context.getString(com.halo.app.R.string.home_tools_open_failed) }
                catch (_: SecurityException) { viewModel.captureMessage.value = context.getString(com.halo.app.R.string.home_tools_open_failed) }
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
    var confirmClear by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(draftKey) { confirmClear = false }
    if (confirmClear) androidx.compose.material3.AlertDialog(onDismissRequest = { confirmClear = false },
        title = { Text(uiText("Clear this conversation?")) },
        text = { Text(uiText("This removes its messages. Your other conversations and Notebook are kept.")) },
        confirmButton = { HaloActionButton(onClick = { confirmClear = false; updateDraft(""); viewModel.clearConversation() },
            style = HaloActionStyle.STOP) { Text(uiText("Clear conversation")) } },
        dismissButton = { TextButton(onClick = { confirmClear = false }) { Text(uiText("Keep messages")) } })
    val actingOnPhone = (uiState.sessionState as? CognitiveSessionState.ExecutingTool)
        ?.toolId?.let(::homeIsPhoneAction) == true
    val feedScroll = rememberScrollState()
    var followLatest by rememberHomeFeedFollowing(feedScroll, draftKey)
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
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
        .haloStateAura(light, MaterialTheme.colorScheme.primaryContainer,
            androidx.compose.ui.graphics.Color(0xFFFF9F6B), MaterialTheme.colorScheme.error)
        .haloAssistantEdge(active = actingOnPhone && !lightStopped, attention = presentation.needsReview,
            accent = MaterialTheme.colorScheme.primaryContainer, apricot = androidx.compose.ui.graphics.Color(0xFFFF9F6B)),
        contentAlignment = Alignment.TopCenter) {
    Column(Modifier.widthIn(max = 760.dp).fillMaxSize().imePadding()) {
        // H01: keep the wordmark and Updates above the scrollable conversation.
        // Only the feed moves; actions and the composer stay anchored below.
        if (!shortScreen || !keyboardVisible) Box(Modifier.fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = if (shortScreen) 4.dp else 12.dp)) {
            HomeChatHeader(mode = uiText(homeModeLabel(currentMode)),
                modeEnabled = !changingConversation && !isActiveSession && !isListening && !isSpeechProcessing,
                onMode = { modeMenuExpanded = true }, onHistory = { showHistory = true },
                onNew = viewModel::newConversation, onUpdates = onOpenNotificationInbox,
                onActivity = onOpenActivity, onClear = { confirmClear = true },
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
                }, surfaceSwitcher = surfaceSwitcher)

        }
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(feedScroll)
            .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)) {
            HomePresenceHeader(
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
                        TextButton(onClick = ::submitText, enabled = canSend && textInput.isNotBlank()) { Text(uiText("Retry")) }
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
            if (showTaskDetails) executingToolId?.let { id ->
                if (homeFxTool(id) != null) HomeToolExecutionFx(id, viewModel::cancelSession)
                else if (homeShouldShowUnmappedPhoneAction(id))
                    HomeUnmappedPhoneAction(viewModel::cancelSession)
            }
            // The recorded plan, when it exists, appears beside the active
            // action. No steps are inferred from a text request or tool name.
            val showActivePlan = homeShouldPromoteActivePlan(
                uiState.sessionState, uiState.longTaskProgress != null)
            if (showTaskDetails && showActivePlan) uiState.longTaskProgress?.let { plan ->
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
            when {
                providerNeedsAttention || connectionNeedsAttention -> HaloFocusAction(
                    text = uiText("Set up answers"), glyph = HaloGlyph.MODEL, enabled = true,
                    active = canSend && !presentation.needsReview,
                    style = if (canSend && !presentation.needsReview) HaloActionStyle.PRIMARY else HaloActionStyle.SECONDARY,
                    onClick = { chatProviderId?.let(onOpenProvider) ?: viewModel.navigateToIntelligence() })
                !uiState.microphonePermissionGranted -> TextButton(onClick = onRequestMicrophonePermission) { Text(uiText("Enable voice")) }
                !uiState.speechReady -> TextButton(onClick = viewModel::navigateToVoice) { Text(uiText("Set up voice")) }
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
                    if (homeShowsHistoricalReply(turn, liveReplyIdentity)) HomeTranscriptMessage(
                        "${turn.id}:assistant", turn.assistantText, user = false,
                        onSave = if (canEditMessage) ({ viewModel.saveResponse(PresentedAssistantResponse(
                            turn.conversationId, turn.id, turn.metadata["interactionId"], turn.metadata["requestId"],
                            turn.assistantText, ResponsePresentationSource.COMMITTED_CONVERSATION)) }) else null,
                        onEdit = if (canEditMessage && turn.userText.isNotBlank()) ({ prepareEarlierQuestion(turn.userText) }) else null,
                        canRead = canEditMessage)
                }
            }
            pendingQuestion?.let { HomeTranscriptMessage("${submission.interactionId}:pending", it, user = true) }
            if (homeShouldShowThinkingSkeleton(uiState.sessionState, if (priorSavedReply) "" else responseText,
                    presentation.needsReview)) HomeThinkingSkeleton()
            HomeAutoReadAnswers(uiState, responseText, isActiveSession)
            if (responseText.isNotBlank() && !priorSavedReply) {
                HomeResponseCard(uiState, responseText, isActiveSession,
                    cloudReply = cloudHandoffProviderId(
                        uiState.presentedIdentity?.providerId,
                        providerRows.associate { it.descriptor.providerId to it.descriptor.location }
                    ) != null,
                    onCopy = { uiState.presentedIdentity?.takeIf { it.turnId != null }?.let { clipboard.setText(AnnotatedString(it.text)) } },
                    onSave = { uiState.presentedIdentity?.let(viewModel::saveResponse) },
                    onEdit = if (canEditMessage) currentTurns.firstOrNull { it.id == uiState.presentedIdentity?.turnId }
                        ?.userText?.takeIf(String::isNotBlank)?.let { question -> ({ prepareEarlierQuestion(question) }) } else null)
            } else if (currentTurns.isEmpty() && pendingQuestion == null && canSend && !presentation.needsReview && !submission.needsRecovery && textInput.isBlank()) {
                HomeWelcome(compact = true, onDraft = { updateDraft(it); composerFocusRequester.requestFocus() })
            }
            uiState.rejectionNotice?.let { notice ->
                val stoppedByUser = notice.reasonText == "Input interrupted. You can try again or type your question."
                Surface(
                    shape = com.halo.app.ui.design.HaloShapes.control,
                    color = if (stoppedByUser) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.errorContainer,
                    contentColor = if (stoppedByUser) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
                ) {
                    Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                        Text(
                              uiText(when {
                                  stoppedByUser -> "Listening stopped"
                                  notice.outputFailure -> "Couldn’t play the reply"
                                  else -> "Couldn’t use that voice input"
                              }),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                              uiText(notice.reasonText),
                            style = MaterialTheme.typography.bodyMedium
                          )
                          notice.transcript?.let { transcript ->
                              Text(transcript, style = MaterialTheme.typography.bodyMedium)
                              TextButton(enabled = notice.conversationId == null || notice.conversationId == uiState.conversationId,
                                  onClick = { updateDraft(transcript); viewModel.dismissSpeechNotice() }) { Text(uiText("Edit as text")) }
                          }
                          androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                              if (!notice.outputFailure) TextButton(onClick = ::togglePresence) { Text(uiText("Try again")) }
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
            if(!isActiveSession && !isListening && !isSpeechProcessing && !isSpeaking) androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                nextActions.forEach { action -> TextButton(onClick={ viewModel.performNextAction(action) }) { Text(action.title) } }
            }


        }
        if (unseenReply) Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.End) {
            TextButton(onClick = {
                scope.launch {
                    feedScroll.animateScrollTo(feedScroll.maxValue)
                    followLatest = true
                    unseenReply = false
                }
            }, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(uiText("New reply ↓"))
            }
        }
        Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)
            .background(MaterialTheme.colorScheme.surfaceContainerLow, androidx.compose.foundation.shape.RoundedCornerShape(26.dp)).padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
            HaloComposer(
                focusAllowed = true,
                focusRequester = composerFocusRequester,
                value = textInput, onValueChange = ::updateDraft, onSubmit = ::submitText,
                onVoice = ::togglePresence,
                onStop = {
                    com.halo.app.ui.fx.HaloMotionEvents.stop()
                    when {
                        isListening || isSpeechProcessing -> viewModel.cancelVoiceInput()
                        isSpeaking -> viewModel.stopSpeakingAndListen()
                        else -> viewModel.cancelSession()
                    }
                },
                enabled = canSend && !presentation.needsReview && !providerNeedsAttention && !connectionNeedsAttention,
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
                onAttach = { focusManager.clearFocus(); showContext = true },
                onTools = { focusManager.clearFocus(); toolGroup = HomeToolGroup.CREATE.name; toolEntry = null
                    viewModel.refreshToolCatalog(); showActions = true },
            )
            // The field must be attached before a navigation event requests focus.
            LaunchedEffect(composerFocus) { if (composerFocus > 0) composerFocusRequester.requestFocus() }
        }
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
