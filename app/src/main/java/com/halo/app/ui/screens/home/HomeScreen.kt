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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.halo.app.ui.glass.components.HaloMarkdownText
import com.halo.app.ui.glass.material.HaloRuntimeState
import com.halo.app.ui.presence.HaloPresence
import com.halo.app.ui.theme.HaloPastelBlue
import com.halo.app.ui.theme.HaloWarning
import com.halo.app.ui.theme.rememberReducedMotion
import com.halo.app.ui.workspace.HaloWorkspaceButton
import com.halo.app.ui.workspace.HaloWorkspaceCard
import com.halo.sdk.provider.CognitiveSessionState
import com.halo.sdk.provider.ProviderStatus

@Composable
fun HomeScreen(viewModel: HomeViewModel) {
    val uiState by viewModel.uiState.collectAsState()
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
        if (!uiState.speechReady) {
            viewModel.navigateToVoice()
            return
        }
        when {
            isListening -> viewModel.stopListening()
            isSpeaking -> viewModel.stopSpeakingAndListen()
            isActiveSession -> viewModel.cancelSession()
            isSpeechProcessing -> Unit
            else -> viewModel.startListening()
        }
    }

    fun submitText() {
        val text = textInput.trim()
        if (text.isBlank() || isActiveSession || isListening || isSpeechProcessing || isSpeaking) return
        viewModel.submitText(text)
        textInput = ""
    }

    val presenceLabel = when {
        isSpeechProcessing -> "Transcribing"
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
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = "halo",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 3.sp,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = uiState.activeProvider,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Text(
                    text = uiState.activeProviderStatus.lowercase(),
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        AnimatedVisibility(visible = providerNeedsAttention) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 18.dp)
            ) {
                HaloWorkspaceCard(accent = HaloWarning) {
                    Text(
                        text = "AI provider needs attention",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = uiState.activeProviderMessage ?: when (uiState.activeProviderState) {
                            ProviderStatus.State.UNCONFIGURED -> "Finish provider setup before using it."
                            ProviderStatus.State.OFFLINE -> "The selected provider is currently offline."
                            ProviderStatus.State.MODEL_MISSING -> "Choose or install a model for this provider."
                            ProviderStatus.State.ERROR -> "The provider reported an error."
                            ProviderStatus.State.UNAVAILABLE -> "The selected provider is unavailable."
                            else -> "Choose and configure an AI provider."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    HaloWorkspaceButton(
                        text = "Open Intelligence",
                        onClick = viewModel::navigateToIntelligence,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        AnimatedVisibility(visible = !uiState.speechReady) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp)
            ) {
                HaloWorkspaceCard(accent = HaloPastelBlue) {
                    Text(
                        text = "Voice input needs setup",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Install the local Whisper Tiny model to enable private voice input.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    HaloWorkspaceButton(
                        text = "Set up Voice",
                        onClick = viewModel::navigateToVoice,
                        modifier = Modifier.fillMaxWidth(),
                        primary = false
                    )
                }
            }
        }

        Spacer(Modifier.height(30.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(210.dp),
            contentAlignment = Alignment.Center
        ) {
            HaloPresence(
                state = uiState.runtimeState,
                size = 184.dp,
                reducedMotion = reducedMotion
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
        Text(
            text = "${uiState.recentEvents.size} recent events",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.82f)
        )

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
