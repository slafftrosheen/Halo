package com.halo.app.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.halo.app.ui.glass.components.*
import com.halo.app.ui.glass.material.HaloGlassColors
import com.halo.app.ui.glass.material.HaloGlassTokens
import com.halo.app.ui.glass.material.HaloTypography

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.foundation.clickable
import androidx.compose.ui.graphics.Color

@Composable
fun HomeScreen(viewModel: HomeViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    var textInput by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(HaloGlassColors.Background)
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
            .padding(bottom = 140.dp) // padding for navigation pill overlay
    ) {
        // TOP Header
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "HALO", 
                style = HaloTypography.titleLarge,
                color = HaloGlassColors.TextPrimary,
                letterSpacing = 4.sp
            )
            
            Text(
                text = uiState.connectionState,
                style = HaloTypography.labelMedium,
                color = if (uiState.connectionState == "Connected") HaloGlassColors.Accent else HaloGlassColors.TextMuted
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        // CENTER Action: State Orb
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            HaloFloatingMic(
                state = uiState.runtimeState,
                onClick = { 
                    if (uiState.sessionState is com.halo.sdk.provider.CognitiveSessionState.Idle || 
                        uiState.sessionState is com.halo.sdk.provider.CognitiveSessionState.Completed ||
                        uiState.sessionState is com.halo.sdk.provider.CognitiveSessionState.Failed) {
                        viewModel.submitText("Summarize my recent activity.")
                    } else {
                        viewModel.cancelSession()
                    }
                }
            )
        }

        Spacer(modifier = Modifier.height(24.dp))
        
        // Contextual Text / AI Response
        Column(
            modifier = Modifier.fillMaxWidth().heightIn(min = 100.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Context Indicators
            if (uiState.sessionState != com.halo.sdk.provider.CognitiveSessionState.Idle) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ContextTag("🎙 Voice", true)
                    ContextTag("📷 Camera", uiState.capabilities.contains("Camera"))
                    ContextTag("● History", true)
                }
            }

            Text(
                text = uiState.contextualText,
                style = HaloTypography.bodyLarge,
                color = HaloGlassColors.TextPrimary,
                modifier = Modifier.padding(horizontal = 8.dp)
            )
            
            if (uiState.sessionState is com.halo.sdk.provider.CognitiveSessionState.Reasoning) {
                Spacer(Modifier.height(16.dp))
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(0.5f),
                    color = HaloGlassColors.Accent,
                    trackColor = HaloGlassColors.Glass
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        Spacer(modifier = Modifier.weight(1f))

        // TEXT INPUT SURFACE
        HaloGlassCard {
            Row(
                modifier = Modifier.fillMaxWidth().padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = textInput,
                    onValueChange = { textInput = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("What do you need?", style = HaloTypography.bodyMedium, color = HaloGlassColors.TextMuted) },
                    textStyle = HaloTypography.bodyMedium,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = HaloGlassColors.TextPrimary,
                        unfocusedTextColor = HaloGlassColors.TextPrimary,
                        focusedBorderColor = Color.Transparent,
                        unfocusedBorderColor = Color.Transparent
                    ),
                    maxLines = 3
                )
                
                if (uiState.sessionState != com.halo.sdk.provider.CognitiveSessionState.Idle && 
                    uiState.sessionState !is com.halo.sdk.provider.CognitiveSessionState.Completed &&
                    uiState.sessionState !is com.halo.sdk.provider.CognitiveSessionState.Failed) {
                    IconButton(onClick = { viewModel.cancelSession() }) {
                        Icon(Icons.Default.Stop, contentDescription = "Stop", tint = HaloGlassColors.Danger)
                    }
                } else {
                    IconButton(
                        onClick = { 
                            if (textInput.isNotBlank()) {
                                viewModel.submitText(textInput)
                                textInput = ""
                            }
                        },
                        enabled = textInput.isNotBlank()
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Submit", tint = if (textInput.isNotBlank()) HaloGlassColors.Accent else HaloGlassColors.TextMuted)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(48.dp))
        
        // Intelligence Indicator
        Row(
            modifier = Modifier.fillMaxWidth().clickable { viewModel.navigateToIntelligence() },
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Intelligence: ", style = HaloTypography.labelMedium, color = HaloGlassColors.TextSecondary)
            Text(uiState.activeProvider, style = HaloTypography.labelMedium, color = HaloGlassColors.Accent)
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Device Status
        HaloGlassSection(
            title = "Device Status",
            modifier = Modifier.clickable { viewModel.navigateToDevices() }
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Wearable", style = HaloTypography.bodyMedium, color = HaloGlassColors.TextSecondary)
                Text(uiState.connectionState, style = HaloTypography.bodyMedium, color = HaloGlassColors.TextPrimary)
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))

        // Recent Activity
        HaloGlassSection(title = "Timeline") {
            if (uiState.recentEvents.isEmpty()) {
                Text(
                    text = "No activity yet.",
                    style = HaloTypography.bodyMedium,
                    color = HaloGlassColors.TextMuted,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            } else {
                uiState.recentEvents.take(5).forEach { event ->
                    Column(modifier = Modifier.padding(vertical = 8.dp)) {
                        Text(event.command, style = HaloTypography.labelLarge, color = HaloGlassColors.TextPrimary)
                        Text(event.status, style = HaloTypography.labelSmall, color = HaloGlassColors.TextMuted)
                    }
                }
            }
        }
    }
}

@Composable
private fun ContextTag(label: String, active: Boolean) {
    if (!active) return
    Text(
        text = label,
        style = HaloTypography.labelSmall,
        color = HaloGlassColors.Accent,
        modifier = Modifier
            .padding(horizontal = 4.dp)
            .background(HaloGlassColors.Accent.copy(alpha = 0.1f), HaloGlassTokens.pillShape)
            .padding(horizontal = 8.dp, vertical = 2.dp)
    )
}
