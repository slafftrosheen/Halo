package com.halo.app.ui.screens.home

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
    onOpenPendingUpdates: () -> Unit = {},
    onOpenProvider: (String) -> Unit = {},
    onOpenLearningSuggestions: () -> Unit = {},
    onConfigureVision: () -> Unit = {},
    onContextPrivacy: () -> Unit = {},
    onAssistantSetup: () -> Unit = {},
    onOpenNotificationInbox: () -> Unit = {},
) {
    val dashboard by viewModel.dashboard.collectAsStateWithLifecycle()
    val showLearning = dashboard.showLearning
    val selectedContext by (viewModel.workspace?.workingSet ?: kotlinx.coroutines.flow.flowOf(emptyList<String>())).collectAsStateWithLifecycle(emptyList())
    val context = androidx.compose.ui.platform.LocalContext.current
    var pendingPhoto by rememberSaveable { mutableStateOf<String?>(null) }
    val captureMessage by viewModel.captureMessage.collectAsStateWithLifecycle()
    val visualAttachment by viewModel.visualAttachment.collectAsStateWithLifecycle()
    val imagePicker = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri -> uri?.let(viewModel::attachSharedImage) }
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
    var textInput by rememberSaveable { mutableStateOf("") }
    val reducedMotion by rememberReducedMotion()
    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(viewModel) {
        viewModel.refreshProviderDiagnostics()
    }

    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshDashboard()
                viewModel.refreshProviderDiagnostics()
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
            isSpeechProcessing -> Unit
            !uiState.microphonePermissionGranted -> onRequestMicrophonePermission()
            !uiState.speechReady -> viewModel.navigateToVoice()
            else -> viewModel.startListening()
        }
    }

    fun submitText() {
        val text = textInput.trim()
        if (text.isBlank() || isActiveSession || isListening || isSpeechProcessing || isSpeaking) return
        viewModel.submitText(text)
        textInput = ""
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

    val responseText = when (val session = uiState.sessionState) {
        is CognitiveSessionState.Streaming -> session.text
        is CognitiveSessionState.Completed -> session.response.text
        is CognitiveSessionState.Failed -> uiState.contextualText
        else -> uiState.presentedResponse.orEmpty()
    }.trim()

    var showDetails by rememberSaveable { mutableStateOf(false) }
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
                Text(
                    "A little clarity for your day.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            when {
                providerNeedsAttention || connectionNeedsAttention -> TextButton(onClick = { chatProviderId?.let(onOpenProvider) ?: viewModel.navigateToIntelligence() }) { Text("Configure") }
                !uiState.microphonePermissionGranted -> TextButton(onClick = onRequestMicrophonePermission) { Text("Enable voice") }
                !uiState.speechReady -> TextButton(onClick = viewModel::navigateToVoice) { Text("Set up voice") }
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
            modifier = Modifier.fillMaxWidth().height(if (responseText.isNotBlank() || isActiveSession) 112.dp else 192.dp),
            contentAlignment = Alignment.Center,
        ) {
            val basePresentation = uiState.sessionState.toSignalPresentation(
                fallback = uiState.runtimeState,
                detail = uiState.contextualText,
                activityLevel = uiState.orb.inputLevel,
                reducedMotion = reducedMotion
            )
            val waitingForUserOrResource = runtimeTaskStatus?.let { status ->
                status.contains("waiting", ignoreCase = true) ||
                    status.contains("approval", ignoreCase = true) ||
                    status.contains("resource", ignoreCase = true) ||
                    status.contains("capability", ignoreCase = true)
            } == true
            val signalPresentation = when {
                waitingForUserOrResource -> basePresentation.copy(
                    state = HaloSignalState.WAITING,
                    glyph = HaloGlyph.APPROVAL,
                    label = "Waiting",
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
                size = if (responseText.isNotBlank() || isActiveSession) 96.dp else 176.dp,
                onClick = ::togglePresence
            )
        }

        Spacer(Modifier.height(10.dp))

        Text(
            text = if (uiState.runtimeState == HaloRuntimeState.Idle && !providerNeedsAttention && !connectionNeedsAttention && !isSpeechProcessing && runtimeTaskStatus == null) { if (responseText.isBlank()) "Hello, I'm Halo" else "Ready when you are" } else presenceLabel,
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
                        "Didn't use that speech input",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        "Reason: ${notice.reasonText} · Try again",
                        style = MaterialTheme.typography.bodySmall
                    )
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
                }) { Text("Run") } }, dismissButton = { TextButton(onClick = { quickAction = null }) { Text("Cancel") } })
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            TextButton(enabled = "halo_open_app" in dashboard.quickTools, onClick = { quickValue = ""; quickAction = "Open app" }) { Text("Open app") }
            TextButton(enabled = "halo_timer" in dashboard.quickTools, onClick = { quickValue = "120"; quickAction = "Timer" }) { Text("Timer") }
            TextButton(enabled = "halo_create_empty_note" in dashboard.quickTools, onClick = viewModel::createNote) { Text("New note") }
            TextButton(onClick = { imagePicker.launch("image/*") }) { Text("Vision") }
        }
        TextButton(onClick = onOpenNotificationInbox) { Text(if (dashboard.notificationCount > 0) "Inbox · ${dashboard.notificationCount} notifications · ${dashboard.replyableNotificationCount} replyable" else "Inbox") }
        if (dashboard.assistantActive) Text("Native assistant active", style = MaterialTheme.typography.labelSmall)
        else TextButton(onClick = onAssistantSetup) { Text("Set up native assistant") }
        var showActions by remember { mutableStateOf(false) }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Box {
                TextButton(onClick = { showActions = true }) { Text(if (dashboard.pendingUpdateCount > 0 || (showLearning && dashboard.learningCount > 0)) "Actions \u00b7 Review" else "Actions") }
                DropdownMenu(expanded = showActions, onDismissRequest = { showActions = false }) {
                    DropdownMenuItem(text = { Text("Take a photo") }, onClick = {
                        showActions = false
                        if (context.checkSelfPermission(android.Manifest.permission.CAMERA) == android.content.pm.PackageManager.PERMISSION_GRANTED) launchCamera()
                        else cameraPermission.launch(android.Manifest.permission.CAMERA)
                    })
                    DropdownMenuItem(text = { Text("Attach image") }, onClick = { showActions = false; imagePicker.launch("image/*") })
                    DropdownMenuItem(text = { Text("New note") }, onClick = { showActions = false; viewModel.createNote() })
                    DropdownMenuItem(text = { Text("Connection & context") }, onClick = { showActions = false; showDetails = !showDetails })
                    if (showLearning && dashboard.learningCount > 0) {
                        DropdownMenuItem(text = { Text("Learning suggestions · ${dashboard.learningCount}") }, onClick = { showActions = false; onOpenLearningSuggestions() })
                    }
                    if (dashboard.pendingUpdateCount > 0) {
                        DropdownMenuItem(text = { Text("Waiting updates (${dashboard.pendingUpdateCount})") }, onClick = { showActions = false; onOpenPendingUpdates() })
                    }
                DropdownMenuItem(text = { Text("New conversation") }, onClick = {
                    showActions = false
                    viewModel.newConversation()
                })
                DropdownMenuItem(text = { Text("Clear conversation") }, onClick = {
                    showActions = false
                    viewModel.clearConversation()
                })
                }
            }
        }
            }
        }
        Spacer(Modifier.height(16.dp))
        captureMessage?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
        visualAttachment?.let { attachment ->
            Row { TextButton(onClick = onConfigureVision) { Text("Configure Vision") }
                TextButton(onClick = onContextPrivacy) { Text("Image privacy") } }
            Row(verticalAlignment = Alignment.CenterVertically) {
                com.halo.app.capture.VisualAttachmentThumbnail(attachment, Modifier.size(48.dp))
                Text("${attachment.source} · ${attachment.ref.bytes ?: 0} bytes", modifier = Modifier.weight(1f))
                androidx.compose.material3.TextButton(onClick = viewModel::saveVisualAttachment) { Text("Save to Notebook") }
                androidx.compose.material3.TextButton(onClick = viewModel::removeVisualAttachment) { Text("Remove") }
            }
            Text("Ask about this image…", style = MaterialTheme.typography.bodySmall)
            androidx.compose.material3.TextButton(onClick = viewModel::navigateToIntelligence) { Text("Configure Vision model") }
        }
        uiState.inputStatus.rejection?.let { Text(it.replace('_', ' '), style = MaterialTheme.typography.bodySmall) }
        uiState.longTaskStatus?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        uiState.longTaskProgress?.let { com.halo.app.interaction.StructuredPresentationRenderer(it, Modifier.fillMaxWidth()) }
        if (responseText.isNotBlank()) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 24.dp),
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.34f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.72f)),
                tonalElevation = 1.dp,
            ) {
                Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            HaloIcon(
                                glyph = HaloGlyph.HALO,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.secondary,
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "Halo",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                        if (
                            uiState.sessionState is CognitiveSessionState.Streaming ||
                            uiState.sessionState is CognitiveSessionState.Completed
                        ) {
                            Text(
                                text = uiState.activeProvider,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    /*
                     * MINING-P2-001: a committed structured answer renders as a bounded card, with the
                     * assistant's own text kept underneath as the fallback. Text-only hosts, speech and
                     * the notebook never see the component tree.
                     */
                    uiState.structuredPresentation?.let { presentation ->
                        com.halo.app.interaction.StructuredPresentationRenderer(
                            presentation = presentation,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(12.dp))
                    }
                    if (uiState.sessionState is CognitiveSessionState.Streaming) {
                        Text(responseText, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.fillMaxWidth())
                    } else {
                        HaloMarkdownText(markdown = responseText, textColor = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.fillMaxWidth())
                    }
                    if (!isActiveSession) {
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            TextButton(onClick = { clipboard.setText(AnnotatedString(responseText)) }) { Text("Copy") }
                            TextButton(onClick = { viewModel.saveResponse(responseText) }) {
                                // Explicit host action; saving is never automatic (Part N).
                                Text(
                                    if (uiState.structuredPresentation != null || uiState.researchResult) "Save to Notebook" else "Save",
                                )
                            }
                        }
                    }
                }
            }
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
                Surface(
                    onClick = { modeMenuExpanded = true },
                    modifier = Modifier.heightIn(min = 48.dp),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
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
                            text = currentMode.name.lowercase().replaceFirstChar { it.uppercase() },
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

        HaloComposer(
            value = textInput,
            onValueChange = { textInput = it },
            onSubmit = ::submitText,
            onVoice = ::togglePresence,
            onStop = viewModel::cancelSession,
            enabled = !isListening && !isSpeechProcessing && !isSpeaking && !isActiveSession,
            voiceEnabled = !isSpeechProcessing && !isActiveSession,
            voiceActive = isListening,
            voiceContentDescription = when {
                !uiState.microphonePermissionGranted -> "Grant microphone access"
                !uiState.speechReady -> "Set up voice input"
                isListening -> "Stop listening"
                isSpeaking -> "Interrupt response and listen"
                else -> "Voice input"
            },
            showStop = isActiveSession
        )

    }
    }
}

@Composable
private fun HaloComposer(
    value: String,
    onValueChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onVoice: () -> Unit,
    onStop: () -> Unit,
    enabled: Boolean,
    voiceEnabled: Boolean,
    voiceActive: Boolean,
    voiceContentDescription: String,
    showStop: Boolean
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onVoice,
                enabled = voiceEnabled && !showStop
            ) {
                Icon(
                    imageVector = if (voiceActive) Icons.Default.Stop else Icons.Default.Mic,
                    contentDescription = voiceContentDescription,
                    tint = if (voiceActive) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.secondary
                    }
                )
            }

            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                enabled = enabled,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 6.dp, vertical = 12.dp),
                textStyle = MaterialTheme.typography.bodyLarge.copy(
                    color = MaterialTheme.colorScheme.onSurface
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.secondary),
                maxLines = 4,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { onSubmit() }),
                decorationBox = { inner ->
                    if (value.isEmpty()) {
                        Text(
                            text = "Ask Halo…",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    inner()
                }
            )

            IconButton(
                onClick = if (showStop) onStop else onSubmit,
                enabled = showStop || (enabled && value.isNotBlank())
            ) {
                Icon(
                    imageVector = if (showStop) Icons.Default.Stop else Icons.AutoMirrored.Filled.Send,
                    contentDescription = if (showStop) "Stop" else "Send",
                    tint = if (showStop) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondary
                )
            }
        }
    }
}
