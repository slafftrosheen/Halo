package com.halo.app.ui.screens.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.tween
import androidx.compose.animation.togetherWith
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.halo.app.ui.glass.components.*
import com.halo.app.ui.glass.material.HaloGlassColors
import com.halo.app.ui.glass.material.HaloRuntimeState
import com.halo.app.ui.glass.material.HaloTypography
import com.halo.sdk.provider.CognitiveSessionState

@Composable
fun HomeScreen(viewModel: HomeViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    var textInput by remember { mutableStateOf("") }
    val isActiveSession = uiState.sessionState != CognitiveSessionState.Idle &&
        uiState.sessionState !is CognitiveSessionState.Completed &&
        uiState.sessionState !is CognitiveSessionState.Failed
    val isListening = uiState.speechActive
    val isSessionActive = uiState.sessionState != CognitiveSessionState.Idle

    fun submitText() {
        val text = textInput.trim()
        if (text.isNotEmpty() && !isActiveSession) {
            viewModel.submitText(text)
            textInput = ""
        }
    }

    fun toggleOrb() {
        when {
            uiState.speechActive -> viewModel.stopListening()
            isActiveSession -> viewModel.cancelSession()
            else -> viewModel.startListening()
        }
    }

    val presenceLabel = when (uiState.runtimeState) {
        HaloRuntimeState.Listening, HaloRuntimeState.Wakeword -> "Listening"
        HaloRuntimeState.Capturing -> "Hearing you"
        HaloRuntimeState.Recognizing -> "Understanding"
        HaloRuntimeState.Reasoning, HaloRuntimeState.Planning -> "Thinking"
        HaloRuntimeState.Executing -> "Working"
        HaloRuntimeState.Speaking -> "Responding"
        HaloRuntimeState.Recovering -> "Recovering"
        HaloRuntimeState.Stopped -> "Offline"
        HaloRuntimeState.Idle -> "Ready"
    }

    val hintLabel = when {
        isListening -> "Tap to stop"
        isActiveSession -> "Tap to cancel"
        else -> "Tap to speak"
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
        Text(
            "HALO",
            style = HaloTypography.titleLarge,
            color = HaloGlassColors.TextPrimary,
            letterSpacing = 4.sp,
            modifier = Modifier.padding(top = 8.dp)
        )

        Spacer(Modifier.height(24.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            HaloGlassColors.Glass.copy(alpha = 0.6f),
                            Color.Transparent
                        ),
                        radius = 720f
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(vertical = 16.dp)
            ) {
                HaloStateOrb(
                    state = uiState.runtimeState,
                    size = 172.dp,
                    onClick = ::toggleOrb,
                    contentDescription = when {
                        uiState.speechActive -> "Stop listening"
                        isActiveSession -> "Stop Halo"
                        else -> "Speak to Halo"
                    }
                )

                Spacer(Modifier.height(20.dp))

                // Animated presence label — smooth text crossfade on state change
                AnimatedContent(
                    targetState = presenceLabel,
                    transitionSpec = {
                        (fadeIn(tween(300)) + slideInVertically { it / 3 })
                            .togetherWith(fadeOut(tween(200)) + slideOutVertically { -it / 3 })
                    },
                    label = "presence_label"
                ) { label ->
                    Text(
                        label,
                        style = HaloTypography.labelLarge,
                        color = HaloGlassColors.TextPrimary,
                        letterSpacing = 1.4.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(Modifier.height(4.dp))

                Text(
                    hintLabel,
                    style = HaloTypography.labelSmall,
                    color = HaloGlassColors.TextMuted,
                    letterSpacing = 0.6.sp
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

        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun SessionContextPanel(uiState: HomeUiState) {
    HaloGlassSection(title = "CONTEXT") {
        when (val session = uiState.sessionState) {
            is CognitiveSessionState.Streaming -> Text(
                session.text.ifBlank { "Generating…" }, style = HaloTypography.bodyLarge,
                color = HaloGlassColors.TextPrimary, lineHeight = 22.sp
            )
            is CognitiveSessionState.Completed -> HaloMarkdownText(
                markdown = session.response.text.ifBlank { "Completed" }, textColor = HaloGlassColors.TextPrimary,
                modifier = Modifier.fillMaxWidth()
            )
            else -> Text(uiState.contextualText, style = HaloTypography.bodyLarge, color = HaloGlassColors.TextPrimary)
        }
        if (uiState.sessionState is CognitiveSessionState.Completed ||
            uiState.sessionState is CognitiveSessionState.Streaming
        ) {
            Spacer(Modifier.height(8.dp))
            Text("via ${uiState.activeProvider}", style = HaloTypography.labelSmall, color = HaloGlassColors.TextMuted)
        }
        if (uiState.sessionState is CognitiveSessionState.Reasoning) {
            Spacer(Modifier.height(12.dp))
            HaloGlassIndeterminateProgress(modifier = Modifier.fillMaxWidth())
        }
    }
}
