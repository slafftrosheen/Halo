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
import com.halo.app.ui.fx.haloEdgeGlow

import com.halo.app.language.uiText
import com.halo.app.ui.presence.HaloSignalState
import com.halo.app.ui.components.HaloActionButton
import com.halo.app.ui.components.HaloActionStyle
import com.halo.app.ui.design.HaloFocusAction
import androidx.compose.ui.semantics.semantics
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
) {
    val dashboard by viewModel.dashboard.collectAsStateWithLifecycle()
    val daily by viewModel.dailyAssistant.collectAsStateWithLifecycle()
    val selectedContext by (viewModel.workspace?.workingSet ?: kotlinx.coroutines.flow.flowOf(emptyList<String>())).collectAsStateWithLifecycle(emptyList())
    val context = androidx.compose.ui.platform.LocalContext.current
    var pendingPhoto by rememberSaveable { mutableStateOf<String?>(null) }
    val captureDraft by viewModel.captureQuestionDraft.collectAsStateWithLifecycle()
    val captureMessage by viewModel.captureMessage.collectAsStateWithLifecycle()
    val nextActions by viewModel.nextActions.collectAsStateWithLifecycle()
    val contextChips by viewModel.contextChips.collectAsStateWithLifecycle()
    val composerFocus by viewModel.composerFocus.collectAsStateWithLifecycle()
    val composerFocusRequester = remember { androidx.compose.ui.focus.FocusRequester() }
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
    LaunchedEffect(captureDraft) { captureDraft?.let {
        if (textInput.isNotBlank() && textInput != it) pendingHistoryDraft = it else updateDraft(it)
        viewModel.captureQuestionDraft.value = null
    } }
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

    val currentMode by viewModel.selectedMode.collectAsStateWithLifecycle()
    val presentation = homeFlowPresentation(uiState, submission, draftKey,
        listeningRequested, providerNeedsAttention || connectionNeedsAttention)
    // Aura is visual presence, not a claim of connectivity, speech capture or tool execution.
    val showReadyAura = uiState.runtimeState == HaloRuntimeState.Idle &&
        !isListening && !isSpeechProcessing && !isSpeaking &&
        !providerNeedsAttention && !connectionNeedsAttention && !presentation.needsReview
    val canSend = !submission.busy && !isListening && !isSpeechProcessing && !isSpeaking && !isActiveSession
    val responseText = (if (uiState.sessionState is CognitiveSessionState.Failed &&
        uiState.presentedIdentity?.source != ResponsePresentationSource.INCOMPLETE) ""
        else uiState.presentedResponse.orEmpty()).trim()
    // Attachments and quick actions live next to the pinned composer, never inside the scrolled feed.
    var showContext by rememberSaveable { mutableStateOf(false) }
    var showActions by remember { mutableStateOf(false) }
    var modeMenuExpanded by remember { mutableStateOf(false) }
    var quickAction by remember { mutableStateOf<String?>(null) }
    var quickValue by remember { mutableStateOf("") }
    var showInboxCapture by rememberSaveable { mutableStateOf(false) }
    if (showInboxCapture) com.halo.app.ui.screens.notebook.InboxCaptureDialog { showInboxCapture = false }
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
    val followThreshold = with(LocalDensity.current) { 140.dp.roundToPx() }
    var followLatest by remember(draftKey) { mutableStateOf(true) }
    // Follow new content while reading the latest turn; a user's upward scroll
    // releases the latch, and returning to the bottom enables it again.
    LaunchedEffect(draftKey,feedScroll,followThreshold) {
        snapshotFlow { Triple(feedScroll.value, feedScroll.maxValue, feedScroll.isScrollInProgress) }
            .collect { (position, bottom, scrolling) ->
                if (scrolling) followLatest = bottom - position <= followThreshold
                else if (followLatest && bottom > position) feedScroll.scrollTo(bottom)
            }
    }
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
        .haloEdgeGlow(active = actingOnPhone, accent = MaterialTheme.colorScheme.primary),
        contentAlignment = Alignment.TopCenter) {
    Column(Modifier.widthIn(max = 760.dp).fillMaxSize().imePadding()) {
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(feedScroll)
            .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)) {
            HomeBrandHeader(onUpdates = onOpenNotificationInbox)
            HomePresenceHeader(
                signal = uiState.sessionState.toSignalPresentation(
                    fallback = uiState.runtimeState, activityLevel = uiState.orb.inputLevel,
                    reducedMotion = reducedMotion).let { signal ->
                    signal.copy(label = uiText(presentation.status), detail = null,
                        state = if (presentation.needsReview) HaloSignalState.WAITING else signal.state,
                        glyph = if (presentation.needsReview) HaloGlyph.APPROVAL else signal.glyph,
                        artwork = if (presentation.needsReview) null else signal.artwork,
                        conditionGlyph = if (presentation.needsReview) HaloGlyph.APPROVAL else null)
                },
                title = uiText(presentation.status),
                supporting = if (providerNeedsAttention || connectionNeedsAttention)
                    uiText("Choose how halo answers") else null,
                compact = responseText.isNotBlank() || presentation.needsReview ||
                    uiState.sessionState is CognitiveSessionState.ExecutingTool,
                micOpen = uiState.inputStatus.capturing,
                inputLevel = uiState.inputStatus.inputLevel,
                aura = showReadyAura && daily.items.isEmpty() && responseText.isBlank(),
                orbModifier = haloSharedOrbModifier(sharedTransitionScope, animatedVisibilityScope, allowSharedOrb),
            )
            // Tool artwork is mounted ONLY while the active cognitive turn owns a
            // matching execution. It disappears when the runtime leaves ExecutingTool;
            // that transition alone is NOT proof of success.
            val executingToolId =
                (uiState.sessionState as? CognitiveSessionState.ExecutingTool)?.toolId
            executingToolId?.let { id ->
                if (homeFxTool(id) != null) HomeToolExecutionFx(id, viewModel::cancelSession)
                else if (homeShouldShowUnmappedPhoneAction(id))
                    HomeUnmappedPhoneAction(viewModel::cancelSession)
            }
            // The recorded plan, when it exists, appears beside the active
            // action. No steps are inferred from a text request or tool name.
            val showActivePlan = homeShouldPromoteActivePlan(
                uiState.sessionState, uiState.longTaskProgress != null)
            if (showActivePlan) uiState.longTaskProgress?.let { plan ->
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = com.halo.app.ui.design.HaloShapes.card,
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                ) {
                    Column(Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(uiText("Recorded action progress"),
                            style = MaterialTheme.typography.titleSmall)
                        com.halo.app.interaction.StructuredPresentationRenderer(
                            plan, Modifier.fillMaxWidth())
                    }
                }
            }
            HomeToolResultFx(uiState.toolReceipt, uiState.conversationId)
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
            if (presentation.needsReview) {
                // Needs you is an actual waiting journal state. Apricot never marks
                // the current or completed step, and Review does not approve by itself.
                val attentionInk = if (MaterialTheme.colorScheme.background.luminance() < 0.5f)
                    androidx.compose.ui.graphics.Color(0xFFFF9F6B)
                    else androidx.compose.ui.graphics.Color(0xFFA84B12)
                Surface(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    border = BorderStroke(1.dp, attentionInk)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(uiText(presentation.status), style = MaterialTheme.typography.titleMedium)
                        Text(uiText("Open Activity to see what halo needs and choose what happens next."),
                            style = MaterialTheme.typography.bodyMedium)
                        HaloActionButton(onClick = onOpenActivity, modifier = Modifier.fillMaxWidth()) {
                            Text(uiText("Review in Activity"))
                        }
                    }
                }
            }
            if (daily.items.isNotEmpty()) DailyAssistantSection(daily, compact = true) { action ->
                viewModel.recordDailyAction(action)
                onDailyAction(action)
            }
            if (homeShouldShowThinkingSkeleton(
                    uiState.sessionState, responseText, presentation.needsReview
                )) HomeThinkingSkeleton()
            // Keep recent turns in the conversation feed. Earlier turns remain available
            // without hiding the conversation behind a development-style disclosure.
            val conversationTurns by viewModel.conversationTurns.collectAsStateWithLifecycle()
            val currentTurns = conversationTurns.filter { it.conversationId == uiState.conversationId }
            var showEarlierMessages by rememberSaveable(uiState.conversationId) { mutableStateOf(false) }
            if (currentTurns.size > 4) TextButton(onClick = { showEarlierMessages = !showEarlierMessages }) {
                Text(uiText(if (showEarlierMessages) "Hide messages" else "Conversation history"))
            }
            (if (showEarlierMessages) currentTurns else currentTurns.takeLast(4)).forEach { turn ->
                if (turn.userText.isNotBlank()) Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.fillMaxWidth().padding(start = 32.dp)
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text(turn.userText, style = MaterialTheme.typography.bodyMedium)
                        TextButton(onClick = {
                            if (textInput.isBlank() || textInput == turn.userText) updateDraft(turn.userText)
                            else pendingHistoryDraft = turn.userText
                        }, enabled = !isActiveSession && !isListening && !isSpeechProcessing && !isSpeaking) {
                            Text(uiText("Edit as a new message"))
                        }
                    }
                }
                if (turn.assistantText.isNotBlank() && turn.assistantText.trim() != responseText) Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.fillMaxWidth().padding(end = 32.dp)
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text(turn.assistantText, style = MaterialTheme.typography.bodyMedium)
                        TextButton(onClick = { clipboard.setText(AnnotatedString(turn.assistantText)) }) {
                            Text(uiText("Copy reply"))
                        }
                    }
                }
            }
            HomeAutoReadAnswers(uiState, responseText, isActiveSession)
            if (responseText.isNotBlank()) {
                HomeResponseCard(uiState, responseText, isActiveSession,
                    cloudReply = cloudHandoffProviderId(
                        uiState.presentedIdentity?.providerId,
                        providerRows.associate { it.descriptor.providerId to it.descriptor.location }
                    ) != null,
                    onCopy = { uiState.presentedIdentity?.takeIf { it.turnId != null }?.let { clipboard.setText(AnnotatedString(it.text)) } },
                    onSave = { uiState.presentedIdentity?.let(viewModel::saveResponse) })
            } else if (canSend && !presentation.needsReview && !submission.needsRecovery && textInput.isBlank()) {
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
            captureMessage?.let { Text(uiText(it), style = MaterialTheme.typography.bodyMedium) }
            val captureRecovery by com.halo.app.di.ManualDependencyGraph.contextualCapture.notice.collectAsStateWithLifecycle()
            captureRecovery?.let { Text(uiText(it), style = MaterialTheme.typography.bodyMedium) }
            val contextualCapture by com.halo.app.di.ManualDependencyGraph.contextualCapture.state.collectAsStateWithLifecycle()
            contextualCapture?.let { TextButton(onClick = com.halo.app.di.ManualDependencyGraph.contextualCapture::reopen) { Text(uiText("Review captured image")) } }
            if (responseText.isNotBlank() && contextualCapture?.selectedAction == com.halo.app.capture.CaptureAction.EXTRACT)
                Text(uiText("Model-extracted draft · Verify visible text and uncertain values before using them"), style = MaterialTheme.typography.bodyMedium)
            ContinuityControls(viewModel, isActiveSession || isSpeaking, onOpenCapture)
            if (!showActivePlan) uiState.longTaskProgress?.let {
                com.halo.app.interaction.StructuredPresentationRenderer(
                    it, Modifier.fillMaxWidth())
            }
            if(!isActiveSession && !isListening && !isSpeechProcessing && !isSpeaking) androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                nextActions.forEach { action -> TextButton(onClick={ viewModel.performNextAction(action) }) { Text(action.title) } }
            }


            quickAction?.let { action ->
                val command = if (action == "Timer") quickValue.toIntOrNull()?.takeIf { it in 1..86400 }?.let { "Set a timer for $it seconds" }
                    else quickValue.trim().takeIf { it.isNotBlank() }?.let { "Open $it" }
                androidx.compose.material3.AlertDialog(onDismissRequest = { quickAction = null }, title = { Text(uiText(action)) },
                    text = { HaloTextField(value = quickValue, onValueChange = { quickValue = it.take(100) },
                        label = { Text(uiText(if (action == "Timer") "Seconds" else "Application name")) }, kind = if (action == "Timer") VoiceFieldKind.NUMBER else VoiceFieldKind.SHORT, maxChars = 100) },
                    confirmButton = { HaloActionButton(enabled = canSend && command != null, onClick = {
                        if (command != null && canSend) { viewModel.submitText(command); quickAction = null }
                    }) { Text(uiText("Run")) } }, dismissButton = { TextButton(onClick = { quickAction = null }) { Text(uiText("Cancel")) } })
            }
        }
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (isActiveSession || isListening || isSpeechProcessing || isSpeaking ||
                presentation.needsReview || submission.needsRecovery || submission.retryable)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(uiText(presentation.status), modifier = Modifier.weight(1f).semantics {
                    liveRegion = androidx.compose.ui.semantics.LiveRegionMode.Polite
                }, style = MaterialTheme.typography.bodyMedium)
                if (presentation.needsReview) HaloActionButton(onClick = onOpenActivity) { Text(uiText("Review")) }
                else if (submission.retryable && submission.conversationId == draftKey)
                    TextButton(onClick = ::submitText, enabled = canSend && textInput.isNotBlank()) { Text(uiText("Retry")) }
            }
            if (submission.needsRecovery && submission.conversationId == draftKey && !submission.retryable)
                TextButton(onClick = onOpenActivity) { Text(uiText("Open Activity")) }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { showContext = true }, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(uiText("Attach") + if (contextChips.isNotEmpty()) " (${contextChips.size})" else "")
                }
                Box {
                    TextButton(onClick = { showActions = true }, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text(uiText("Tools"))
                    }
                    DropdownMenu(expanded = showActions, onDismissRequest = { showActions = false }) {
                        DropdownMenuItem(
                            text = { Text(uiText("How Halo helps") + ": " + uiText(homeModeLabel(currentMode))) },
                            enabled = !isActiveSession && !isListening && !isSpeechProcessing,
                            onClick = { showActions = false; modeMenuExpanded = true },
                        )
                        DropdownMenuItem(text = { Text(uiText("Save to Inbox")) }, onClick = {
                            showActions = false; showInboxCapture = true
                        })
                        DropdownMenuItem(text = { Text(uiText("New note")) }, onClick = {
                            showActions = false; viewModel.createNote()
                        })
                        DropdownMenuItem(text = { Text(uiText("Timer")) },
                            enabled = canSend && "halo_timer" in dashboard.quickTools,
                            onClick = { showActions = false; quickValue = "120"; quickAction = "Timer" })
                        DropdownMenuItem(text = { Text(uiText("Open app")) },
                            enabled = canSend && "halo_open_app" in dashboard.quickTools,
                            onClick = { showActions = false; quickValue = ""; quickAction = "Open app" })
                        DropdownMenuItem(text = { Text(uiText("New conversation")) }, onClick = {
                            showActions = false; viewModel.newConversation()
                        })
                        DropdownMenuItem(text = { Text(uiText("Clear conversation")) }, onClick = {
                            showActions = false; confirmClear = true
                        })
                    }
                    DropdownMenu(expanded = modeMenuExpanded, onDismissRequest = { modeMenuExpanded = false }) {
                        CognitiveOperatingMode.values().forEach { mode ->
                            DropdownMenuItem(
                                text = { Text(uiText(homeModeLabel(mode))) },
                                trailingIcon = {
                                    if (mode == currentMode) Text("✓")
                                },
                                onClick = {
                                    if (!isActiveSession && !isListening && !isSpeechProcessing) {
                                        viewModel.selectedMode.value = mode
                                    }
                                    modeMenuExpanded = false
                                },
                            )
                        }
                    }
                }
                Spacer(Modifier.weight(1f))
                if (selectedContext.isNotEmpty()) Text(uiText("%1\$d sources", selectedContext.size),
                    style = MaterialTheme.typography.labelSmall)
            }
            HaloComposer(
                focusAllowed = !presentation.needsReview && !providerNeedsAttention && !connectionNeedsAttention,
                focusRequester = composerFocusRequester,
                value = textInput, onValueChange = ::updateDraft, onSubmit = ::submitText,
                onVoice = ::togglePresence,
                onStop = {
                    when {
                        isListening || isSpeechProcessing -> viewModel.cancelVoiceInput()
                        else -> viewModel.cancelSession()
                    }
                },
                enabled = canSend,
                voiceEnabled = !isListening && !isSpeechProcessing && (!isActiveSession || isSpeaking),
                voiceActive = isListening,
                voiceContentDescription = uiText(when {
                    !uiState.microphonePermissionGranted -> "Enable voice"
                    !uiState.speechReady -> "Set up voice"
                    isListening -> "Listening…"
                    isSpeaking -> "Interrupt reply and talk"
                    else -> "Talk"
                }),
                showStop = isActiveSession || submission.busy || isListening || isSpeechProcessing || isSpeaking,
            )
            // The field must be attached before a navigation event requests focus.
            LaunchedEffect(composerFocus) { if (composerFocus > 0) composerFocusRequester.requestFocus() }
        }
        // Review confirmation must remain composed even when attachments are closed.
        ContextContinuityControls(viewModel, expanded = false, onImport = {})
        if (showContext) androidx.compose.material3.ModalBottomSheet(
            onDismissRequest = { showContext = false },
        ) {
            Column(Modifier.fillMaxWidth().heightIn(max = 460.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(uiText("Attachments and context"), style = MaterialTheme.typography.titleMedium)
                ContextContinuityControls(viewModel, expanded = true,
                    onImport = { showContext = false; imagePicker.launch("image/*") },
                    onCamera = {
                        showContext = false
                        if (context.checkSelfPermission(android.Manifest.permission.CAMERA) ==
                            android.content.pm.PackageManager.PERMISSION_GRANTED) launchCamera()
                        else cameraPermission.launch(android.Manifest.permission.CAMERA)
                    }, onPrivacy = { showContext = false; onContextPrivacy() },
                    onVision = { showContext = false; onConfigureVision() }, showSaveReview = false)
                TextButton(onClick = { showContext = false }) { Text(uiText("Done")) }
            }
        }
    }
    }
}
