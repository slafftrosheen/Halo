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
import com.halo.app.ui.presence.HaloOrb
import androidx.compose.material3.TextButton
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import com.halo.app.ui.theme.HaloPastelBlue
import com.halo.app.ui.theme.HaloWarning
import com.halo.app.ui.theme.rememberReducedMotion
import com.halo.app.ui.workspace.HaloWorkspaceButton
import com.halo.app.ui.workspace.HaloWorkspaceCard
import com.halo.sdk.provider.CognitiveSessionState
import com.halo.sdk.provider.ProviderStatus

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onRequestMicrophonePermission: () -> Unit
) {
    val selectedContext by (viewModel.workspace?.workingSet ?: kotlinx.coroutines.flow.flowOf(emptyList<String>())).collectAsState(emptyList())
    val context = androidx.compose.ui.platform.LocalContext.current
    var pendingPhoto by rememberSaveable { mutableStateOf<String?>(null) }
    val captureMessage by viewModel.captureMessage.collectAsState()
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
        Text("Halo", style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)

        if (providerNeedsAttention) {
            TextButton(onClick = viewModel::navigateToIntelligence) { Text("Set up intelligence") }
        } else if (!uiState.microphonePermissionGranted) {
            TextButton(onClick = onRequestMicrophonePermission) { Text("Enable voice input") }
        } else if (!uiState.speechReady) {
            TextButton(onClick = viewModel::navigateToVoice) { Text("Set up voice") }
        }

        Spacer(Modifier.height(30.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(168.dp),
            contentAlignment = Alignment.Center
        ) {
            HaloOrb(
                presentation = uiState.orb.copy(reducedMotion = reducedMotion),
                size = 140.dp
            )
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .size(52.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                onClick = ::togglePresence,
                enabled = !isSpeechProcessing
            ) {
                Icon(
                    imageVector = if (isListening || isActiveSession) Icons.Default.Stop else Icons.Default.Mic,
                    contentDescription = when {
                        !uiState.microphonePermissionGranted -> "Grant microphone access"
                        !uiState.speechReady -> "Set up voice input"
                        isListening || isActiveSession -> "Stop"
                        else -> "Talk to Halo"
                    },
                    tint = if (isListening || isActiveSession) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.secondary
                    },
                    modifier = Modifier.padding(14.dp)
                )
            }
        }

        Spacer(Modifier.height(18.dp))

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
        Text("Context · ${selectedContext.size}", style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        var showActions by remember { mutableStateOf(false) }
        Box {
            TextButton(onClick = { showActions = true }) { Text("More") }
            DropdownMenu(expanded = showActions, onDismissRequest = { showActions = false }) {
                DropdownMenuItem(text = { Text("Capture") }, onClick = {
                    showActions = false
                    if (context.checkSelfPermission(android.Manifest.permission.CAMERA) ==
                        android.content.pm.PackageManager.PERMISSION_GRANTED) launchCamera()
                    else cameraPermission.launch(android.Manifest.permission.CAMERA)
                })
                DropdownMenuItem(text = { Text("New note") }, onClick = {
                    showActions = false
                    viewModel.createNote()
                })
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
        captureMessage?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
        uiState.inputStatus.rejection?.let { Text(it.replace('_', ' '), style = MaterialTheme.typography.bodySmall) }
        AnimatedVisibility(visible = responseText.isNotBlank()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 30.dp)
            ) {
                Text(
                    text = "Halo",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.secondary
                )
                Spacer(Modifier.height(8.dp))
                if (!isActiveSession) Row {
                    TextButton(onClick = { clipboard.setText(AnnotatedString(responseText)) }) { Text("Copy") }
                    TextButton(onClick = { viewModel.saveResponse(responseText) }) { Text("Save to Notebook") }
                }
                HaloMarkdownText(
                    markdown = responseText,
                    textColor = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.fillMaxWidth()
                )
                if (
                    uiState.sessionState is CognitiveSessionState.Streaming ||
                    uiState.sessionState is CognitiveSessionState.Completed
                ) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "via ${uiState.activeProvider}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(Modifier.height(30.dp))

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
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
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
