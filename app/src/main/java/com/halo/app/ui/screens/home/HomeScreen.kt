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
import com.halo.sdk.provider.ProviderStatus

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onRequestMicrophonePermission: () -> Unit,
    onOpenPendingUpdates: () -> Unit = {},
) {
    val pendingUpdates by com.halo.app.di.ManualDependencyGraph.attentionQueue.pending.collectAsState()
    val selectedContext by (viewModel.workspace?.workingSet ?: kotlinx.coroutines.flow.flowOf(emptyList<String>())).collectAsState(emptyList())
    val context = androidx.compose.ui.platform.LocalContext.current
    var pendingPhoto by rememberSaveable { mutableStateOf<String?>(null) }
    val captureMessage by viewModel.captureMessage.collectAsState()
    val visualAttachment by com.halo.app.di.ManualDependencyGraph.visualAttachments.state.collectAsState()
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
    val listeningRequested by viewModel.listeningRequested.collectAsState()
    var textInput by rememberSaveable { mutableStateOf("") }
    val reducedMotion by rememberReducedMotion()
    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(viewModel) {
        viewModel.refreshProviderDiagnostics()
    }

    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(horizontal = 24.dp, vertical = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if(pendingUpdates.isNotEmpty()) TextButton(onClick = onOpenPendingUpdates) { Text("${pendingUpdates.size} updates waiting") }

        uiState.longTaskStatus?.let { Text(it, style = MaterialTheme.typography.labelSmall) }
        uiState.longTaskProgress?.let { com.halo.app.interaction.StructuredPresentationRenderer(it, Modifier.fillMaxWidth()) }
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
                    "Your personal intelligence",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            when {
                providerNeedsAttention -> TextButton(onClick = viewModel::navigateToIntelligence) { Text("Set up") }
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
            shadowElevation = 2.dp,
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
        Box(
            modifier = Modifier.fillMaxWidth().height(172.dp),
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
                else -> basePresentation
            }
            HaloSignalFace(
                presentation = signalPresentation,
                size = 160.dp,
                onClick = ::togglePresence
            )
        }

        Spacer(Modifier.height(10.dp))

        Text(
            text = presenceLabel,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = if (isActiveSession || isSpeechProcessing) uiState.contextualText else "Tap the presence or use the composer",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))
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
        var showActions by remember { mutableStateOf(false) }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            TextButton(onClick = {
                    if (context.checkSelfPermission(android.Manifest.permission.CAMERA) ==
                        android.content.pm.PackageManager.PERMISSION_GRANTED) launchCamera()
                    else cameraPermission.launch(android.Manifest.permission.CAMERA)
            }) { Text("Capture") }
            TextButton(onClick = viewModel::createNote) { Text("New note") }
            Box {
                TextButton(onClick = { showActions = true }) { Text("More") }
                DropdownMenu(expanded = showActions, onDismissRequest = { showActions = false }) {
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                com.halo.app.capture.VisualAttachmentThumbnail(attachment, Modifier.size(48.dp))
                Text("${attachment.source} · ${attachment.ref.bytes ?: 0} bytes", modifier = Modifier.weight(1f))
                androidx.compose.material3.TextButton(onClick = viewModel::saveVisualAttachment) { Text("Save to Notebook") }
                androidx.compose.material3.TextButton(onClick = viewModel::removeVisualAttachment) { Text("Remove") }
            }
            Text("Ask about this image…", style = MaterialTheme.typography.bodySmall)
            androidx.compose.material3.TextButton(onClick = viewModel::navigateToIntelligence) { Text("Configure Vision model") }
        }
        androidx.compose.material3.TextButton(onClick = { imagePicker.launch("image/*") }) { Text("Attach image") }
        uiState.inputStatus.rejection?.let { Text(it.replace('_', ' '), style = MaterialTheme.typography.bodySmall) }
        AnimatedVisibility(visible = responseText.isNotBlank()) {
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
                    HaloMarkdownText(
                        markdown = responseText,
                        textColor = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.fillMaxWidth(),
                    )
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

        val currentMode by viewModel.selectedMode.collectAsState()
        var modeMenuExpanded by remember { mutableStateOf(false) }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            Box {
                Surface(
                    onClick = { modeMenuExpanded = true },
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
                            text = "${currentMode.name.lowercase().replaceFirstChar { it.uppercase() }} ▾",
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

        Spacer(Modifier.height(24.dp))
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
        tonalElevation = 3.dp,
        shadowElevation = 2.dp,
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
