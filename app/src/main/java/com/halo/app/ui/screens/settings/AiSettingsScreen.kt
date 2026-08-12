package com.halo.app.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.halo.app.ui.glass.components.HaloGlassSection
import com.halo.app.ui.glass.components.HaloGlassDivider
import com.halo.app.ui.glass.components.HaloGlassChip
import com.halo.app.ui.glass.material.HaloGlassColors
import com.halo.app.ui.glass.material.HaloTypography
import com.halo.sdk.provider.AiCapability

@Composable
fun AiSettingsScreen(viewModel: SettingsViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(HaloGlassColors.Background)
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
            .padding(bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("AI", style = HaloTypography.displayMedium, color = HaloGlassColors.TextPrimary)
        
        Text(
            "Configure the cognitive backbone of Halo.",
            style = HaloTypography.bodyMedium,
            color = HaloGlassColors.TextSecondary
        )

        HaloGlassSection(title = "CURRENT PROVIDER") {
            val current = uiState.aiProviders.find { it.isSelected }
            if (current != null) {
                ProviderRow(current, onSelect = {})
            } else {
                Text("No provider selected", color = HaloGlassColors.TextMuted, modifier = Modifier.padding(12.dp))
            }
        }

        HaloGlassSection(title = "AVAILABLE PROVIDERS") {
            uiState.aiProviders.forEachIndexed { index, provider ->
                ProviderRow(
                    provider = provider,
                    onSelect = { viewModel.selectAiProvider(provider.id) }
                )
                if (index < uiState.aiProviders.size - 1) {
                    HaloGlassDivider()
                }
            }
        }
        
        val selectedProvider = uiState.aiProviders.find { it.isSelected }
        if (selectedProvider != null) {
            HaloGlassSection(title = "CAPABILITIES") {
                CapabilityRow("Text", selectedProvider.capabilities.contains(AiCapability.TEXT))
                HaloGlassDivider()
                CapabilityRow("Image", selectedProvider.capabilities.contains(AiCapability.IMAGE))
                HaloGlassDivider()
                CapabilityRow("Audio", selectedProvider.capabilities.contains(AiCapability.AUDIO))
                HaloGlassDivider()
                CapabilityRow("Video", selectedProvider.capabilities.contains(AiCapability.VIDEO))
                HaloGlassDivider()
                CapabilityRow("Multimodal", selectedProvider.capabilities.contains(AiCapability.MULTIMODAL))
            }

            HaloGlassSection(title = "INTEGRATION") {
                val status = if (selectedProvider.isAvailable) "Ready" else "Unavailable"
                val handoff = if (selectedProvider.id.contains("assistant")) "Handoff only" else "Full integration"
                
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Status", style = HaloTypography.bodyMedium, color = HaloGlassColors.TextSecondary)
                    Text(status, style = HaloTypography.bodyMedium, color = if (selectedProvider.isAvailable) HaloGlassColors.Success else HaloGlassColors.Danger)
                }
                HaloGlassDivider()
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Mode", style = HaloTypography.bodyMedium, color = HaloGlassColors.TextSecondary)
                    Text(handoff, style = HaloTypography.bodyMedium, color = HaloGlassColors.TextPrimary)
                }
            }
        }

        HaloGlassSection(title = "PRIVACY") {
            val privacyText = if (uiState.provider.contains("Assistant")) {
                "When using Phone Assistant, requests are processed by your system's default assistant app. Privacy is managed according to your system settings. Halo does not receive the full conversation history."
            } else {
                "Requests are processed locally on your device by the configured AI model. No data leaves the phone. This is the most private mode."
            }
            Text(
                text = privacyText,
                style = HaloTypography.labelMedium,
                color = HaloGlassColors.TextMuted
            )
        }
    }
}

@Composable
private fun ProviderRow(provider: AiProviderUiState, onSelect: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = provider.isAvailable) { onSelect() }
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(provider.name, color = if (provider.isAvailable) HaloGlassColors.TextPrimary else HaloGlassColors.TextMuted, style = HaloTypography.bodyMedium)
            Text(if (provider.isAvailable) "Available" else "Not configured", color = HaloGlassColors.TextMuted, style = HaloTypography.labelMedium)
        }
        RadioButton(
            selected = provider.isSelected,
            onClick = null,
            enabled = provider.isAvailable,
            colors = RadioButtonDefaults.colors(
                selectedColor = HaloGlassColors.Accent,
                unselectedColor = HaloGlassColors.TextMuted
            )
        )
    }
}

@Composable
private fun CapabilityRow(name: String, supported: Boolean) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(name, color = HaloGlassColors.TextPrimary, style = HaloTypography.bodyMedium)
        if (supported) {
            Text("✓", color = HaloGlassColors.Success, style = HaloTypography.bodyLarge)
        } else {
            Text("—", color = HaloGlassColors.TextMuted, style = HaloTypography.bodyLarge)
        }
    }
}
