package com.halo.app.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.halo.app.ui.glass.components.*
import com.halo.app.ui.glass.material.HaloGlassColors
import com.halo.app.ui.glass.material.HaloGlassMaterials
import com.halo.app.ui.glass.material.HaloRuntimeState
import com.halo.app.ui.glass.material.HaloTypography

@Composable
fun HomeScreen(viewModel: HomeViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    var textInput by remember { mutableStateOf("") }
    val isActiveSession = uiState.sessionState != com.halo.sdk.provider.CognitiveSessionState.Idle &&
        uiState.sessionState !is com.halo.sdk.provider.CognitiveSessionState.Completed &&
        uiState.sessionState !is com.halo.sdk.provider.CognitiveSessionState.Failed
    val isProviderReady = uiState.activeProviderStatus == "READY" || uiState.activeProviderStatus == "STANDBY"
    val isListening = uiState.runtimeState == HaloRuntimeState.Capturing || uiState.runtimeState == HaloRuntimeState.Recognizing
    val isSessionActive = uiState.sessionState != com.halo.sdk.provider.CognitiveSessionState.Idle

    fun submitText() {
        val text = textInput.trim()
        if (text.isNotEmpty() && !isActiveSession) {
            viewModel.submitText(text)
            textInput = ""
        }
    }

    fun toggleOrb() {
        // Listening is a speech-control state, not a cognitive-session state.
        // Stop the microphone before cancelling an already-running cognitive turn.
        when {
            isListening -> viewModel.stopListening()
            isActiveSession -> viewModel.cancelSession()
            else -> viewModel.startListening()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    0f to HaloGlassColors.BackgroundSecondary,
                    0.34f to HaloGlassColors.Background,
                    1f to HaloGlassColors.Background
                )
            )
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("HALO", style = HaloTypography.titleLarge, color = HaloGlassColors.TextPrimary, letterSpacing = 4.sp)
                Spacer(Modifier.height(2.dp))
                Text("COGNITIVE RUNTIME", style = HaloTypography.labelSmall, color = HaloGlassColors.TextMuted, letterSpacing = 1.4.sp)
            }
            HaloGlassChip(
                text = if (isProviderReady) "READY" else "SETUP",
                material = HaloGlassMaterials.Interactive.copy(
                    containerColor = if (isProviderReady) HaloGlassColors.Success.copy(alpha = 0.14f) else HaloGlassColors.Glass,
                    contentColor = if (isProviderReady) HaloGlassColors.Success else HaloGlassColors.TextSecondary
                )
            )
        }

        Spacer(Modifier.height(30.dp))

        val presenceLabel = when (uiState.runtimeState) {
            HaloRuntimeState.Listening, HaloRuntimeState.Wakeword -> "Listening for you"
            HaloRuntimeState.Capturing -> "Listening"
            HaloRuntimeState.Recognizing -> "Understanding"
            HaloRuntimeState.Reasoning, HaloRuntimeState.Planning -> "Reasoning"
            HaloRuntimeState.Executing -> "Executing"
            HaloRuntimeState.Speaking -> "Responding"
            HaloRuntimeState.Recovering -> "Recovering"
            HaloRuntimeState.Stopped, HaloRuntimeState.Idle -> "Standing by"
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(HaloGlassColors.Glass, Color.Transparent),
                        radius = 720f
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(vertical = 12.dp)) {
                HaloStateOrb(
                    state = uiState.runtimeState,
                    size = 172.dp,
                    onClick = ::toggleOrb,
                    contentDescription = when {
                        isListening -> "Stop listening"
                        isActiveSession -> "Stop Halo"
                        else -> "Speak to Halo"
                    }
                )

                Spacer(Modifier.height(18.dp))
                Text(
                    presenceLabel,
                    style = HaloTypography.labelLarge,
                    color = HaloGlassColors.TextPrimary,
                    letterSpacing = 1.2.sp
                )
                Spacer(Modifier.height(5.dp))
                Text(uiState.contextualText, style = HaloTypography.bodySmall, color = HaloGlassColors.TextMuted)
                Spacer(Modifier.height(7.dp))
                Text(
                    when {
                        isListening -> "Tap to stop listening"
                        isActiveSession -> "Tap to stop"
                        else -> "Tap Halo to speak"
                    },
                    style = HaloTypography.labelSmall,
                    color = HaloGlassColors.TextMuted
                )
            }
        }

        if (isSessionActive) {
            Spacer(Modifier.height(12.dp))
            SessionContextPanel(uiState)
        }

        Spacer(Modifier.height(22.dp))

        HaloGlassCard {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                HaloGlassTextField(
                    value = textInput,
                    onValueChange = { textInput = it },
                    modifier = Modifier.weight(1f),
                    placeholder = "Ask Halo anything…",
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { submitText() }),
                    maxLines = 3,
                    enabled = !isActiveSession
                )
                Spacer(Modifier.width(8.dp))
                if (isActiveSession) {
                    HaloGlassIconButton.Danger(
                        imageVector = Icons.Default.Stop,
                        contentDescription = "Stop",
                        onClick = viewModel::cancelSession
                    )
                } else {
                    HaloGlassIconButton.Primary(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Submit",
                        onClick = ::submitText,
                        enabled = textInput.isNotBlank()
                    )
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            HaloGlassButton.Secondary(modifier = Modifier.weight(1f), text = "Devices", onClick = viewModel::navigateToDevices)
            HaloGlassButton.Secondary(modifier = Modifier.weight(1f), text = "Intelligence", onClick = viewModel::navigateToIntelligence)
        }

        Spacer(Modifier.height(24.dp))
        Text(
            text = if (uiState.activeProvider == "No provider") "Choose an intelligence provider to start using Halo."
            else "Intelligence · ${uiState.activeProvider}",
            style = HaloTypography.labelSmall,
            color = if (isProviderReady) HaloGlassColors.TextMuted else HaloGlassColors.Warning,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun SessionContextPanel(uiState: HomeUiState) {
    HaloGlassSection(title = "CONTEXT") {
        when (val session = uiState.sessionState) {
            is com.halo.sdk.provider.CognitiveSessionState.Streaming -> Text(
                session.text.ifBlank { "Generating…" }, style = HaloTypography.bodyLarge,
                color = HaloGlassColors.TextPrimary, lineHeight = 22.sp
            )
            is com.halo.sdk.provider.CognitiveSessionState.Completed -> HaloMarkdownText(
                markdown = session.response.text.ifBlank { "Completed" }, textColor = HaloGlassColors.TextPrimary,
                modifier = Modifier.fillMaxWidth()
            )
            else -> Text(uiState.contextualText, style = HaloTypography.bodyLarge, color = HaloGlassColors.TextPrimary)
        }
        if (uiState.sessionState is com.halo.sdk.provider.CognitiveSessionState.Completed ||
            uiState.sessionState is com.halo.sdk.provider.CognitiveSessionState.Streaming
        ) {
            Spacer(Modifier.height(8.dp))
            Text("via ${uiState.activeProvider}", style = HaloTypography.labelSmall, color = HaloGlassColors.TextMuted)
        }
        if (uiState.sessionState is com.halo.sdk.provider.CognitiveSessionState.Reasoning) {
            Spacer(Modifier.height(12.dp))
            HaloGlassIndeterminateProgress(modifier = Modifier.fillMaxWidth())
        }
    }
}
