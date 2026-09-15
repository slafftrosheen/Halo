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
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
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
    val isSessionActive = uiState.sessionState != com.halo.sdk.provider.CognitiveSessionState.Idle

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
        // ── 1. HALO ──
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    "HALO",
                    style = HaloTypography.titleLarge,
                    color = HaloGlassColors.TextPrimary,
                    letterSpacing = 4.sp
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    "COGNITIVE RUNTIME",
                    style = HaloTypography.labelSmall,
                    color = HaloGlassColors.TextMuted,
                    letterSpacing = 1.4.sp
                )
            }
            HaloGlassChip(
                text = uiState.connectionState.uppercase(),
                material = HaloGlassMaterials.Interactive.copy(
                    containerColor = if (isDeviceConnected) HaloGlassColors.Success.copy(alpha = 0.14f) else HaloGlassColors.Glass,
                    contentColor = if (isDeviceConnected) HaloGlassColors.Success else HaloGlassColors.TextSecondary
                )
            )
        }

        Spacer(Modifier.height(32.dp))

        // ── 2. ORB ──
        val presenceLabel = when (uiState.runtimeState) {
            HaloRuntimeState.Listening, HaloRuntimeState.Wakeword -> "Listening for you"
            HaloRuntimeState.Capturing -> "Capturing input"
            HaloRuntimeState.Recognizing -> "Understanding context"
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
                        radius = 600f
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(vertical = 16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onLongPress = { viewModel.navigateToConsole() }
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {
                    HaloStateOrb(
                        state = uiState.runtimeState,
                        size = 120.dp
                    )
                }

                Spacer(Modifier.height(16.dp))

                // ── 3. CONTEXTUAL TEXT ──
                Text(
                    presenceLabel,
                    style = HaloTypography.labelLarge,
                    color = HaloGlassColors.TextPrimary,
                    letterSpacing = 1.2.sp
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    uiState.contextualText,
                    style = HaloTypography.bodySmall,
                    color = HaloGlassColors.TextMuted
                )
            }
        }

        // ── 4. PROGRESSIVE DISCLOSURE: session context ──
        if (isSessionActive) {
            Spacer(Modifier.height(12.dp))
            SessionContextPanel(uiState)
        }

        Spacer(Modifier.height(20.dp))

        // ── 5. ACTION ──
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            HaloFloatingMic(
                state = uiState.runtimeState,
                size = 72.dp,
                onClick = {
                    when {
                        isActiveSession -> viewModel.cancelSession()
                        isListening -> viewModel.stopListening()
                        else -> viewModel.startListening()
                    }
                }
            )
            Spacer(Modifier.height(8.dp))
            Text(
                when {
                    isActiveSession -> "Tap to stop"
                    isListening -> "Listening…"
                    else -> "Tap to speak"
                },
                style = HaloTypography.labelSmall,
                color = HaloGlassColors.TextSecondary
            )
        }

        Spacer(Modifier.height(16.dp))

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

        // ── 6. DEVICE · INTELLIGENCE ──
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            HaloGlassButton.Secondary(
                modifier = Modifier.weight(1f),
                text = "Devices",
                onClick = viewModel::navigateToDevices
            )
            HaloGlassButton.Secondary(
                modifier = Modifier.weight(1f),
                text = "Intelligence",
                onClick = viewModel::navigateToIntelligence
            )
        }

        Spacer(Modifier.height(24.dp))

        // ── 7. RUNTIME FOOTER ──
        CompactRuntimeFooter(
            provider = uiState.activeProvider,
            providerStatus = uiState.activeProviderStatus,
            isProviderReady = isProviderReady,
            runtimeState = uiState.runtimeState.name,
            isRuntimeHealthy = uiState.runtimeState != HaloRuntimeState.Recovering,
            device = uiState.connectionState,
            isDeviceConnected = isDeviceConnected,
            batteryPercent = uiState.batteryPercent,
            isCharging = uiState.isCharging,
            onOpenIntelligence = { viewModel.navigateToIntelligence() }
        )

        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun SessionContextPanel(uiState: HomeUiState) {
    HaloGlassSection(title = "CONTEXT") {
        if (uiState.capabilities.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                uiState.capabilities.forEach { capability ->
                    ContextTag(capability.uppercase())
                }
            }
            Spacer(Modifier.height(8.dp))
        }
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
            else -> Text(
                uiState.contextualText,
                style = HaloTypography.bodyLarge,
                color = HaloGlassColors.TextPrimary
            )
        }
        if (uiState.sessionState is com.halo.sdk.provider.CognitiveSessionState.Completed ||
            uiState.sessionState is com.halo.sdk.provider.CognitiveSessionState.Streaming
        ) {
            Spacer(Modifier.height(8.dp))
            Text(
                "via ${uiState.activeProvider}",
                style = HaloTypography.labelSmall,
                color = HaloGlassColors.TextMuted
            )
        }
        if (uiState.sessionState is com.halo.sdk.provider.CognitiveSessionState.Reasoning) {
            Spacer(Modifier.height(12.dp))
            HaloGlassIndeterminateProgress(modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun CompactRuntimeFooter(
    provider: String,
    providerStatus: String,
    isProviderReady: Boolean,
    runtimeState: String,
    isRuntimeHealthy: Boolean,
    device: String,
    isDeviceConnected: Boolean,
    batteryPercent: Int?,
    isCharging: Boolean,
    onOpenIntelligence: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpenIntelligence)
            .background(HaloGlassColors.Glass.copy(alpha = 0.3f), HaloGlassTokens.smallShape)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        FooterRow(
            label = "Intelligence",
            value = provider,
            indicatorColor = if (isProviderReady) HaloGlassColors.Success else HaloGlassColors.Warning
        )
        FooterRow(
            label = "Runtime",
            value = runtimeState,
            indicatorColor = if (isRuntimeHealthy) HaloGlassColors.Success else HaloGlassColors.Warning
        )
        FooterRow(
            label = "Device",
            value = device,
            indicatorColor = if (isDeviceConnected) HaloGlassColors.Success else HaloGlassColors.TextMuted
        )
        if (batteryPercent != null) {
            FooterRow(
                label = "Battery",
                value = if (isCharging) "$batteryPercent% · Charging" else "$batteryPercent%",
                indicatorColor = if (batteryPercent > 20) HaloGlassColors.Success else HaloGlassColors.Warning
            )
        }
    }
}

@Composable
private fun FooterRow(label: String, value: String, indicatorColor: Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(6.dp)
                    .background(indicatorColor, HaloGlassTokens.circleShape)
            )
            Spacer(Modifier.width(8.dp))
            Text(label, style = HaloTypography.labelSmall, color = HaloGlassColors.TextMuted)
        }
        Text(value, style = HaloTypography.labelSmall, color = HaloGlassColors.TextSecondary)
    }
}

@Composable
private fun ContextTag(label: String) {
    HaloGlassChip(
        text = label,
        material = HaloGlassMaterials.Interactive.copy(
            containerColor = HaloGlassColors.Accent.copy(alpha = 0.10f),
            contentColor = HaloGlassColors.Accent
        )
    )
}
