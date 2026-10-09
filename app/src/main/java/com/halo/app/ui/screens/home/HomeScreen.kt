package com.halo.app.ui.screens.home

import com.halo.app.language.uiText

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.halo.app.ui.glass.components.HaloMarkdownText
import com.halo.app.ui.glass.material.HaloRuntimeState
import com.halo.app.ui.presence.HaloSignalFace
import com.halo.app.ui.presence.HaloSignalState
import com.halo.app.ui.state.toSignalPresentation
import androidx.compose.material3.TextButton
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import com.halo.app.ui.icons.HaloGlyph
import com.halo.app.ui.icons.HaloIcon
import com.halo.app.ui.theme.rememberReducedMotion
import com.halo.app.ui.workspace.HaloWorkspaceButton
import com.halo.app.ui.workspace.HaloWorkspaceCard
import com.halo.runtime.cognition.context.CognitiveOperatingMode
import com.halo.sdk.provider.CognitiveSessionState
import com.halo.app.ai.label
import com.halo.sdk.provider.ProviderStatus

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onRequestMicrophonePermission: () -> Unit,
    onOpenProvider: (String) -> Unit = {},
    onConfigureVision: () -> Unit = {},
    onContextPrivacy: () -> Unit = {},
    onAssistantSetup: () -> Unit = {},
    onOpenNotificationInbox: () -> Unit = {},
    onOpenCapture: (String) -> Unit = {},
    onDailyAction: (com.halo.app.daily.DailyAssistantAction) -> Unit = {},
) {
    val dashboard by viewModel.dashboard.collectAsStateWithLifecycle()
    val daily by viewModel.dailyAssistant.collectAsStateWithLifecycle()
    val selectedContext by (viewModel.workspace?.workingSet ?: kotlinx.coroutines.flow.flowOf(emptyList<String>())).collectAsStateWithLifecycle(emptyList())
    val context = androidx.compose.ui.platform.LocalContext.current
    var pendingPhoto by rememberSaveable { mutableStateOf<String?>(null) }
    val captureDraft by viewModel.captureQuestionDraft.collectAsStateWithLifecycle()
    val captureMessage by viewModel.captureMessage.collectAsStateWithLifecycle()
    val visualAttachment by viewModel.visualAttachment.collectAsStateWithLifecycle()
    val nextActions by viewModel.nextActions.collectAsStateWithLifecycle()
    val contextChips by viewModel.contextChips.collectAsStateWithLifecycle()
    val composerFocus by viewModel.composerFocus.collectAsStateWithLifecycle()
    val composerFocusRequester = remember { androidx.compose.ui.focus.FocusRequester() }
    LaunchedEffect(composerFocus) { if(composerFocus > 0) composerFocusRequester.requestFocus() }
    val imagePicker = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri -> uri?.let(viewModel::attachImportedImage) }
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
            } catch (error: Exception) {
                val path = pendingPhoto
                pendingPhoto = null
                if (path != null) viewModel.discardPhoneCapture(path)
                viewModel.captureMessage.value = "No camera app is available"
            }
        }
    }
    val cameraPermission = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) launchCamera() else viewModel.captureMessage.value = "Camera permission was not granted"
    }
    val clipboard = LocalClipboardManager.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val providerRows by viewModel.providerRows.collectAsStateWithLifecycle()
    val chatProviderId by viewModel.chatProviderId.collectAsStateWithLifecycle()
    val chatProvider = providerRows.firstOrNull { it.descriptor.providerId == chatProviderId }
    val connectionNeedsAttention = chatProvider?.connection in setOf(
        com.halo.app.ai.ProviderConnectionState.AUTH_FAILED, com.halo.app.ai.ProviderConnectionState.OFFLINE,
        com.halo.app.ai.ProviderConnectionState.RATE_LIMITED, com.halo.app.ai.ProviderConnectionState.ERROR,
    )

    val listeningRequested by viewModel.listeningRequested.collectAsStateWithLifecycle()
    var drafts by rememberSaveable { mutableStateOf(hashMapOf<String, String>()) }
    val draftKey = uiState.conversationId.orEmpty()
    val textInput = drafts[draftKey].orEmpty()
    var pendingHistoryDraft by remember(draftKey) { mutableStateOf<String?>(null) }
    fun updateDraft(value: String) { drafts = HashMap(drafts).apply { put(draftKey, value) } }
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
    LaunchedEffect(captureDraft) { captureDraft?.let { updateDraft(it); viewModel.captureQuestionDraft.value = null } }
    val reducedMotion by rememberReducedMotion()
    val lifecycleOwner = LocalLifecycleOwner.current


    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshDashboard()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val isListening = uiState.speechActive || listeningRequested
    val isSpeechProcessing = uiState.speechProcessing
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

    val runtimeTaskStatus = uiState.runtimeTaskStatus
    val presenceLabel = when {
        isSpeechProcessing -> "Transcribing"
        runtimeTaskStatus != null &&
            !isListening && !isSpeaking && !isActiveSession -> runtimeTaskStatus
        else -> when (uiState.runtimeState) {
            HaloRuntimeState.Idle -> "Ready"
            HaloRuntimeState.Listening, HaloRuntimeState.Wakeword -> "Listening"
            HaloRuntimeState.Capturing -> "Hearing you"
            HaloRuntimeState.Recognizing -> "Understanding"
            HaloRuntimeState.Reasoning -> "Thinking"
            HaloRuntimeState.Planning -> "Planning"
            HaloRuntimeState.Executing -> "Working"
            HaloRuntimeState.Speaking -> "Responding"
            HaloRuntimeState.Recovering -> "Recovering"
            HaloRuntimeState.Stopped -> "Offline"
        }
    }

    val responseText = (if (uiState.sessionState is CognitiveSessionState.Failed && uiState.presentedIdentity?.source != ResponsePresentationSource.INCOMPLETE) uiState.contextualText
        else uiState.presentedResponse.orEmpty()).trim()

    var showDetails by rememberSaveable { mutableStateOf(false) }
    var confirmClear by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(draftKey) { confirmClear = false }
    if (confirmClear) androidx.compose.material3.AlertDialog(onDismissRequest = { confirmClear = false },
        title = { Text(uiText("Clear this conversation?")) }, text = { Text(uiText("This removes its messages. Your other conversations and Notebook are kept.")) },
        confirmButton = { TextButton(onClick = { confirmClear = false; updateDraft(""); viewModel.clearConversation() }) { Text(uiText("Clear conversation")) } },
        dismissButton = { TextButton(onClick = { confirmClear = false }) { Text(uiText("Keep messages")) } })
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).imePadding()) {
    Column(
        modifier = Modifier.weight(1f).fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(
                    "Halo",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(uiText("A little clarity for your day."),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            when {
                providerNeedsAttention || connectionNeedsAttention -> TextButton(onClick = { chatProviderId?.let(onOpenProvider) ?: viewModel.navigateToIntelligence() }) { Text(uiText("Configure")) }
                !uiState.microphonePermissionGranted -> TextButton(onClick = onRequestMicrophonePermission) { Text(uiText("Enable voice")) }
                !uiState.speechReady -> TextButton(onClick = viewModel::navigateToVoice) { Text(uiText("Set up voice")) }
            }
        }

        Spacer(Modifier.height(20.dp))

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            shadowElevation = 0.dp,
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
        Box(
            modifier = Modifier.fillMaxWidth().height(if (responseText.isNotBlank() || isActiveSession) 72.dp else 96.dp),
            contentAlignment = Alignment.Center,
        ) {
            val basePresentation = uiState.sessionState.toSignalPresentation(
                fallback = uiState.runtimeState,
                detail = uiState.contextualText,
                activityLevel = uiState.orb.inputLevel,
                reducedMotion = reducedMotion
            )
            val waitingGlyph = when (uiState.runtimeWaitingState) {
                com.halo.runtime.cognition.agent.AgentRunState.WAITING_FOR_APPROVAL -> HaloGlyph.APPROVAL
                com.halo.runtime.cognition.agent.AgentRunState.WAITING_FOR_CLARIFICATION -> HaloGlyph.QUESTION
                com.halo.runtime.cognition.agent.AgentRunState.WAITING_FOR_CAPABILITY -> HaloGlyph.DEVICE
                com.halo.runtime.cognition.agent.AgentRunState.WAITING_FOR_RESOURCE -> HaloGlyph.STORAGE
                com.halo.runtime.cognition.agent.AgentRunState.SUSPENDED -> HaloGlyph.PAUSE
                else -> null
            }
            val signalPresentation = when {
                waitingGlyph != null -> basePresentation.copy(
                    state = HaloSignalState.WAITING,
                    glyph = waitingGlyph,
                    label = runtimeTaskStatus ?: "Waiting",
                    detail = runtimeTaskStatus,
                )
                providerNeedsAttention && uiState.sessionState == CognitiveSessionState.Idle -> basePresentation.copy(
                    state = HaloSignalState.OFFLINE,
                    glyph = HaloGlyph.OFFLINE,
                    label = "Setup needed",
                )
                connectionNeedsAttention && uiState.sessionState == CognitiveSessionState.Idle -> basePresentation.copy(
                    state = HaloSignalState.WARNING, glyph = HaloGlyph.OFFLINE,
                    label = "Connection needs attention", detail = chatProvider?.connection?.label,
                )
                else -> basePresentation
            }
            HaloSignalFace(
                presentation = signalPresentation,
                size = if (responseText.isNotBlank() || isActiveSession) 64.dp else 88.dp,
                onClick = ::togglePresence
            )
        }

        Spacer(Modifier.height(10.dp))

        Text(
            text = uiText(if (uiState.runtimeState == HaloRuntimeState.Idle && !providerNeedsAttention && !connectionNeedsAttention && !isSpeechProcessing && runtimeTaskStatus == null) { if (responseText.isBlank()) "Hello, I'm Halo" else "Ready when you are" } else presenceLabel),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = when {
                isActiveSession || isSpeechProcessing -> uiState.contextualText
                providerNeedsAttention -> "Choose a provider to start a conversation."
                connectionNeedsAttention -> "${chatProvider?.descriptor?.displayName}: ${chatProvider?.connection?.label}"
                else -> "What would you like to explore?"
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))
        uiState.rejectionNotice?.let { notice ->
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.85f),
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
            ) {
                Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                    Text(
                          if (notice.outputFailure) "Speech output unavailable" else "Didn't use that speech input",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                          notice.reasonText,
                        style = MaterialTheme.typography.bodySmall
                      )
                      notice.transcript?.let { transcript ->
                          Text(transcript, style = MaterialTheme.typography.bodySmall)
                          TextButton(enabled = notice.conversationId == null || notice.conversationId == uiState.conversationId,
                              onClick = { updateDraft(transcript); viewModel.dismissSpeechNotice() }) { Text(uiText("Edit as text")) }
                      }
                      Row {
                          if (!notice.outputFailure) TextButton(onClick = ::togglePresence) { Text(uiText("Try again")) }
                          TextButton(onClick = { viewModel.navigateToVoice() }) { Text(uiText("Speech settings")) }
                          TextButton(onClick = { viewModel.dismissSpeechNotice() }) { Text(uiText("Dismiss")) }
                      }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
        if (showDetails) {
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
        ) {
            Text(
                "${uiState.activeProvider}  ·  ${selectedContext.size} in context",
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        }
        var quickAction by remember { mutableStateOf<String?>(null) }
        var quickValue by remember { mutableStateOf("") }
        quickAction?.let { action ->
            androidx.compose.material3.AlertDialog(onDismissRequest = { quickAction = null }, title = { Text(action) },
                text = { androidx.compose.material3.OutlinedTextField(value = quickValue, onValueChange = { quickValue = it.take(100) },
                    label = { Text(if (action == "Timer") "Seconds" else "Application name") }) },
                confirmButton = { TextButton(onClick = {
                    val command = if (action == "Timer") quickValue.toIntOrNull()?.takeIf { it in 1..86400 }?.let { "Set a timer for $it seconds" }
                        else quickValue.trim().takeIf { it.isNotBlank() }?.let { "Open $it" }
                    if (command != null) { viewModel.submitText(command); quickAction = null }
                }) { Text(uiText("Run")) } }, dismissButton = { TextButton(onClick = { quickAction = null }) { Text(uiText("Cancel")) } })
        }
        if (showDetails) {
            if (dashboard.assistantActive) Text(uiText("Native assistant active"), style = MaterialTheme.typography.labelSmall)
            else TextButton(onClick = onAssistantSetup) { Text(uiText("Set up native assistant")) }
        }
        var showActions by remember { mutableStateOf(false) }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Box {
                TextButton(onClick = { showActions = true }) { Text(uiText("Conversation & tools")) }
                DropdownMenu(expanded = showActions, onDismissRequest = { showActions = false }) {
                    DropdownMenuItem(text = { Text(uiText("New note")) }, onClick = { showActions = false; viewModel.createNote() })
                    DropdownMenuItem(text = { Text(uiText("Timer")) }, enabled = "halo_timer" in dashboard.quickTools, onClick = { showActions = false; quickValue = "120"; quickAction = "Timer" })
                    DropdownMenuItem(text = { Text(uiText("Connection & context")) }, onClick = { showActions = false; showDetails = !showDetails })
                    DropdownMenuItem(text = { Text(uiText("Open app")) }, enabled = "halo_open_app" in dashboard.quickTools, onClick = { showActions = false; quickValue = ""; quickAction = "Open app" })
                    DropdownMenuItem(text = { Text(uiText("Inbox")) }, onClick = { showActions = false; onOpenNotificationInbox() })
                DropdownMenuItem(text = { Text(uiText("New conversation")) }, onClick = {
                    showActions = false
                    viewModel.newConversation()
                })
                DropdownMenuItem(text = { Text(uiText("Clear conversation")) }, onClick = {
                    showActions = false
                    confirmClear = true
                })
                }
            }
        }
            }
        }
        Spacer(Modifier.height(16.dp))
        captureMessage?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
        val captureRecovery by com.halo.app.di.ManualDependencyGraph.contextualCapture.notice.collectAsStateWithLifecycle()
        captureRecovery?.let { Text(it,style=MaterialTheme.typography.bodyMedium) }
        val contextualCapture by com.halo.app.di.ManualDependencyGraph.contextualCapture.state.collectAsStateWithLifecycle()
        contextualCapture?.let { TextButton(onClick = com.halo.app.di.ManualDependencyGraph.contextualCapture::reopen) { Text(uiText("Review captured image")) } }
        uiState.inputStatus.rejection?.let { Text(it.replace('_', ' '), style = MaterialTheme.typography.bodySmall) }
        ContinuityControls(viewModel, isActiveSession || isSpeaking, onOpenCapture)
        uiState.longTaskStatus?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        uiState.longTaskProgress?.let { com.halo.app.interaction.StructuredPresentationRenderer(it, Modifier.fillMaxWidth()) }
        if(responseText.isNotBlank() && contextualCapture?.selectedAction == com.halo.app.capture.CaptureAction.EXTRACT)
            Text(uiText("Model-extracted draft · Verify visible text and uncertain values before using them"),style=MaterialTheme.typography.bodySmall)
        if (responseText.isNotBlank()) HomeResponseCard(uiState, responseText, isActiveSession,
            onCopy = { uiState.presentedIdentity?.takeIf { it.turnId != null }?.let { clipboard.setText(AnnotatedString(it.text)) } },
            onSave = { uiState.presentedIdentity?.let(viewModel::saveResponse) })
        val conversationTurns by viewModel.conversationTurns.collectAsStateWithLifecycle()
        var showHistory by rememberSaveable(uiState.conversationId) { mutableStateOf(false) }
        if (conversationTurns.isNotEmpty()) {
            TextButton(onClick = { showHistory = !showHistory }) { Text(if (showHistory) "Hide messages" else "Conversation history") }
            if (showHistory) conversationTurns.filter { it.conversationId == uiState.conversationId }.forEach { turn ->
                Text(turn.userText, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 12.dp))
                Text(turn.assistantText, style = MaterialTheme.typography.bodyMedium)
                TextButton(onClick = { clipboard.setText(AnnotatedString(turn.assistantText)) }) { Text(uiText("Copy reply")) }
                TextButton(onClick={if(textInput.isBlank() || textInput == turn.userText) updateDraft(turn.userText) else pendingHistoryDraft=turn.userText},
                    enabled=!isActiveSession && !isListening && !isSpeechProcessing && !isSpeaking) { Text(uiText("Edit as a new message")) }
            }
        }
        if(!isActiveSession && !isListening && !isSpeechProcessing && !isSpeaking) Row {
            nextActions.forEach { action -> TextButton(onClick={ viewModel.performNextAction(action) }) { Text(action.title) } }
        }

        DailyAssistantSection(daily, compact = isActiveSession || isListening || isSpeechProcessing || isSpeaking || responseText.isNotBlank()) { action ->
            viewModel.recordDailyAction(action)
            onDailyAction(action)
        }

    }
    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp)) {
        val currentMode by viewModel.selectedMode.collectAsStateWithLifecycle()
        var modeMenuExpanded by remember { mutableStateOf(false) }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            Box {
                TextButton(
                    onClick = { modeMenuExpanded = true },
                    modifier = Modifier.heightIn(min = 48.dp),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val glyph = when (currentMode) {
                            CognitiveOperatingMode.AUTO -> HaloGlyph.HALO
                            CognitiveOperatingMode.ASK -> HaloGlyph.QUESTION
                            CognitiveOperatingMode.PLAN -> HaloGlyph.ACTIVITY
                            CognitiveOperatingMode.AGENT -> HaloGlyph.TOOL
                        }
                        HaloIcon(glyph = glyph, contentDescription = currentMode.name, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "Mode: " + if (currentMode == CognitiveOperatingMode.AUTO) "Automatic" else currentMode.name.lowercase().replaceFirstChar { it.uppercase() },
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                DropdownMenu(
                    expanded = modeMenuExpanded,
                    onDismissRequest = { modeMenuExpanded = false }
                ) {
                    CognitiveOperatingMode.values().forEach { mode ->
                        DropdownMenuItem(
                            text = { Text(mode.name.lowercase().replaceFirstChar { it.uppercase() }) },
                            onClick = {
                                viewModel.selectedMode.value = mode
                                modeMenuExpanded = false
                            }
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        ContextContinuityControls(viewModel, onImport={ imagePicker.launch("image/*") }, onCamera={
            if (context.checkSelfPermission(android.Manifest.permission.CAMERA) == android.content.pm.PackageManager.PERMISSION_GRANTED) launchCamera()
            else cameraPermission.launch(android.Manifest.permission.CAMERA)
        }, onPrivacy=onContextPrivacy, onVision=onConfigureVision)
        if (submission.busy || submission.conversationId == draftKey) {
            Text(if (submission.conversationId != draftKey) "A request is running in another conversation. Stop it to send here." else when(submission.phase) {
                TextSubmissionPhase.SUBMITTING -> "Sending…"
                TextSubmissionPhase.ACCEPTED -> "Message accepted"
                TextSubmissionPhase.PROCESSING -> "Working on your message…"
                else -> submission.message.orEmpty()
            }, style = MaterialTheme.typography.bodySmall)
            if (submission.retryable && submission.conversationId == draftKey) TextButton(onClick = ::submitText) { Text(uiText("Retry message")) }
        }
        HaloComposer(
            focusRequester = composerFocusRequester,
            value = textInput,
            onValueChange = ::updateDraft,
            onSubmit = ::submitText,
            onVoice = ::togglePresence,
            onStop = { if (isListening || isSpeechProcessing) viewModel.cancelVoiceInput() else viewModel.cancelSession() },
            enabled = !submission.busy && !isListening && !isSpeechProcessing && !isSpeaking && !isActiveSession,
            voiceEnabled = !isSpeechProcessing && !isActiveSession,
            voiceActive = isListening,
            voiceContentDescription = when {
                !uiState.microphonePermissionGranted -> "Grant microphone access"
                !uiState.speechReady -> "Set up voice input"
                isListening -> "Stop listening"
                isSpeaking -> "Interrupt response and listen"
                else -> "Voice input"
            },
            showStop = isActiveSession || submission.busy || isListening || isSpeechProcessing || isSpeaking
        )

    }
    }
}
