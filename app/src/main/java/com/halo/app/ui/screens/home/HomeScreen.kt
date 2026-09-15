package com.halo.app.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons

import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.input.ImeAction

import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.halo.app.ui.glass.components.*
import com.halo.app.ui.glass.material.HaloGlassColors
import com.halo.app.ui.glass.material.HaloGlassMaterials
import com.halo.app.ui.glass.material.HaloGlassTokens
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
    val isDeviceConnected = uiState.connectionState == "Connected" || uiState.connectionState == "Ready"

    fun submitText() {
        val text = textInput.trim()
        if (text.isNotEmpty() && !isActiveSession) {
            viewModel.submitText(text)
            textInput = ""
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(0f to HaloGlassColors.BackgroundSecondary, 0.34f to HaloGlassColors.Background, 1f to HaloGlassColors.Background))
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
                text = uiState.connectionState.uppercase(),
                material = HaloGlassMaterials.Interactive.copy(
                    containerColor = if (isDeviceConnected) HaloGlassColors.Success.copy(alpha = 0.14f) else HaloGlassColors.Glass,
                    contentColor = if (isDeviceConnected) HaloGlassColors.Success else HaloGlassColors.TextSecondary
                )
            )
        }

        Spacer(Modifier.height(24.dp))

        val presenceLabel = when (uiState.runtimeState) {
            HaloRuntimeState.Listening, HaloRuntimeState.Wakeword -> "Listening for you"
            HaloRuntimeState.Capturing -> "Capturing input"
            HaloRuntimeState.Recognizing -> "Understanding context"
            HaloRuntimeState.Reasoning, HaloRuntimeState.Planning -> "Reasoning about next steps"
            HaloRuntimeState.Executing -> "Running routines"
            HaloRuntimeState.Speaking -> "Responding"
            HaloRuntimeState.Recovering -> "Recovering runtime"
            HaloRuntimeState.Stopped, HaloRuntimeState.Idle -> "Standing by"
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(HaloGlassColors.Glass, Color.Transparent),
                        radius = 600f
                    )
                )
        ) {
            HaloGlassCard {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = presenceLabel,
                        style = HaloTypography.labelSmall,
                        color = HaloGlassColors.TextMuted,
                        letterSpacing = 1.1.sp
                    )

                    Spacer(Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(24.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .pointerInput(Unit) {
                                    detectTapGestures(
                                        onLongPress = {
                                            viewModel.navigateToConsole()
                                        }
                                    )
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            HaloStateOrb(
                                state = uiState.runtimeState,
                                size = 100.dp
                            )
                        }

                        Column(
                            modifier = Modifier.weight(1f),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            HaloFloatingMic(
                                state = uiState.runtimeState,
                                onClick = {
                                    when {
                                        isActiveSession -> viewModel.cancelSession()
                                        isListening -> viewModel.stopListening()
                                        else -> viewModel.startListening()
                                    }
                                }
                            )
                            Spacer(Modifier.height(10.dp))
                            Text(
                                uiState.runtimeState.name.uppercase(),
                                style = HaloTypography.labelLarge,
                                color = HaloGlassColors.TextPrimary,
                                letterSpacing = 1.2.sp
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                when {
                                    isActiveSession -> "Tap to stop"
                                    isListening -> "Listening… tap to stop"
                                    else -> "Tap to speak"
                                },
                                style = HaloTypography.labelSmall,
                                color = HaloGlassColors.TextSecondary
                            )
                        }
                    }

                    Spacer(Modifier.height(8.dp))
                    Text(
                        uiState.contextualText,
                        style = HaloTypography.bodySmall,
                        color = HaloGlassColors.TextMuted
                    )
                }
            }
        }

        if (uiState.recentEvents.isNotEmpty()) {
            val lastEvent = uiState.recentEvents.first()
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(lastEvent.command, style = HaloTypography.labelSmall, color = HaloGlassColors.TextMuted, maxLines = 1, modifier = Modifier.weight(1f))
                Text(lastEvent.status, style = HaloTypography.labelSmall, color = HaloGlassColors.TextSecondary, maxLines = 1)
            }
        }

        Spacer(Modifier.height(16.dp))

        if (uiState.sessionState != com.halo.sdk.provider.CognitiveSessionState.Idle) {
            HaloGlassSection(title = "CURRENT CONTEXT") {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    ContextTag("VOICE", true)
                    ContextTag("CAMERA", uiState.capabilities.contains("Camera"))
                    ContextTag("HISTORY", true)
                }
                Spacer(Modifier.height(4.dp))
                when (val session = uiState.sessionState) {
                    is com.halo.sdk.provider.CognitiveSessionState.Streaming -> Text(
                        session.text.ifBlank { "Generating…" },
                        style = HaloTypography.bodyLarge,
                        color = HaloGlassColors.TextPrimary,
                        lineHeight = 22.sp
                    )
                    is com.halo.sdk.provider.CognitiveSessionState.Completed -> HaloMarkdownText(
                        markdown = session.response.text.ifBlank { "Completed" },
                        textColor = HaloGlassColors.TextPrimary,
                        modifier = Modifier.fillMaxWidth()
                    )
                    else -> Text(uiState.contextualText, style = HaloTypography.bodyLarge, color = HaloGlassColors.TextPrimary)
                }
                if (uiState.sessionState is com.halo.sdk.provider.CognitiveSessionState.Completed || uiState.sessionState is com.halo.sdk.provider.CognitiveSessionState.Streaming) {
                    Spacer(Modifier.height(8.dp))
                    Text("via ${uiState.activeProvider}", style = HaloTypography.labelSmall, color = HaloGlassColors.TextMuted)
                }
                if (uiState.sessionState is com.halo.sdk.provider.CognitiveSessionState.Reasoning) {
                    Spacer(Modifier.height(12.dp))
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = HaloGlassColors.Accent, trackColor = HaloGlassColors.Glass)
                }
            }
            Spacer(Modifier.height(16.dp))
        }

        HaloGlassCard {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = textInput,
                    onValueChange = { textInput = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Ask Halo anything…", style = HaloTypography.bodyMedium, color = HaloGlassColors.TextMuted) },
                    textStyle = HaloTypography.bodyMedium,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { submitText() }),
                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = HaloGlassColors.TextPrimary, unfocusedTextColor = HaloGlassColors.TextPrimary, focusedBorderColor = Color.Transparent, unfocusedBorderColor = Color.Transparent),
                    maxLines = 3,
                    enabled = !isActiveSession
                )
                if (isActiveSession) {
                    IconButton(onClick = viewModel::cancelSession) { Icon(Icons.Default.Stop, contentDescription = "Stop", tint = HaloGlassColors.Danger) }
                } else {
                    IconButton(onClick = ::submitText, enabled = textInput.isNotBlank()) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Submit", tint = if (textInput.isNotBlank()) HaloGlassColors.Accent else HaloGlassColors.TextMuted)
                    }
                }
            }
        }

        Spacer(Modifier.height(20.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            HaloGlassButton.Secondary(modifier = Modifier.weight(1f), text = "Devices", onClick = viewModel::navigateToDevices)
            HaloGlassButton.Secondary(modifier = Modifier.weight(1f), text = "Intelligence", onClick = viewModel::navigateToIntelligence)
        }
        Spacer(Modifier.height(20.dp))

        HaloGlassSection(title = "RUNTIME", modifier = Modifier.clickable { viewModel.navigateToIntelligence() }, trailing = {
            Text(if (isProviderReady) "ONLINE" else "CHECK", style = HaloTypography.labelSmall, color = if (isProviderReady) HaloGlassColors.Success else HaloGlassColors.Warning)
        }) {
            RuntimeMetricRow("Intelligence", uiState.activeProvider, isProviderReady)
            HaloGlassDivider()
            RuntimeMetricRow("Runtime", uiState.runtimeState.name, uiState.runtimeState != HaloRuntimeState.Recovering)
            HaloGlassDivider()
            RuntimeMetricRow("Device", uiState.connectionState, isDeviceConnected)
            uiState.batteryPercent?.let { battery ->
                HaloGlassDivider()
                RuntimeMetricRow("Battery", if (uiState.isCharging) "$battery% · CHARGING" else "$battery%", battery > 20)
            }
        }

        Spacer(Modifier.height(16.dp))
        HaloGlassSection(title = "RECENT ACTIVITY", trailing = { Icon(Icons.Default.History, contentDescription = null, tint = HaloGlassColors.TextMuted) }) {
            if (uiState.recentEvents.isEmpty()) {
                Text("Your cognitive timeline will appear here.", style = HaloTypography.bodySmall, color = HaloGlassColors.TextMuted)
            } else {
                uiState.recentEvents.take(4).forEachIndexed { index, event ->
                    if (index > 0) HaloGlassDivider()
                    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Text(event.command, style = HaloTypography.labelLarge, color = HaloGlassColors.TextPrimary)
                        Spacer(Modifier.height(2.dp))
                        Text(event.status, style = HaloTypography.labelSmall, color = HaloGlassColors.TextMuted)
                    }
                }
            }
        }
        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun RuntimeMetricRow(label: String, value: String, healthy: Boolean) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = HaloTypography.bodyMedium, color = HaloGlassColors.TextSecondary)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(7.dp).background(if (healthy) HaloGlassColors.Success else HaloGlassColors.Warning, HaloGlassTokens.circleShape))
            Spacer(Modifier.width(8.dp))
            Text(value, style = HaloTypography.bodyMedium, color = HaloGlassColors.TextPrimary)
        }
    }
}

@Composable
private fun ContextTag(label: String, active: Boolean) {
    if (!active) return
    HaloGlassChip(text = label, material = HaloGlassMaterials.Interactive.copy(containerColor = HaloGlassColors.Accent.copy(alpha = 0.10f), contentColor = HaloGlassColors.Accent))
}
