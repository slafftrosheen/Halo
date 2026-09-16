package com.halo.app.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.halo.app.ui.glass.components.HaloGlassBackHeader
import com.halo.app.ui.glass.components.HaloGlassButton
import com.halo.app.ui.glass.components.HaloGlassCard
import com.halo.app.ui.glass.components.HaloGlassDivider
import com.halo.app.ui.glass.components.HaloGlassSection
import com.halo.app.ui.glass.components.HaloGlassSettingRow
import com.halo.app.ui.glass.material.HaloGlassColors
import com.halo.app.ui.glass.material.HaloGlassTokens
import com.halo.app.ui.glass.material.HaloTypography

@Composable
fun VoiceSettingsScreen(
    viewModel: VoiceSettingsViewModel,
    onBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(HaloGlassColors.Background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        HaloGlassBackHeader(title = "Voice", onBack = onBack)

        Spacer(Modifier.height(16.dp))

        HaloGlassSection(title = "SPEECH RECOGNITION") {
            HaloGlassSettingRow(
                title = "Model",
                trailingText = "Whisper Tiny"
            )
            HaloGlassDivider()
            HaloGlassSettingRow(
                title = "Status",
                trailingText = when {
                    uiState.isDownloading -> "Downloading... (${(uiState.downloadProgress * 100).toInt()}%)"
                    uiState.isInstalled -> "Ready"
                    else -> "Not installed"
                }
            )
            if (uiState.isInstalled) {
                HaloGlassDivider()
                val megabytes = uiState.installedSizeBytes / (1024 * 1024)
                HaloGlassSettingRow(
                    title = "Storage",
                    trailingText = "~${megabytes} MB"
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        HaloGlassSection(title = "LANGUAGE") {
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                uiState.availableLocales.forEachIndexed { index, option ->
                    val isSelected = option.tag == uiState.selectedLocale
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(HaloGlassTokens.smallShape)
                            .clickable { viewModel.selectLocale(option.tag) }
                            .padding(vertical = 10.dp, horizontal = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = option.displayName,
                            style = HaloTypography.bodyMedium,
                            color = if (isSelected) HaloGlassColors.TextPrimary else HaloGlassColors.TextSecondary
                        )
                        if (isSelected) {
                            Text(
                                text = "Selected",
                                style = HaloTypography.labelSmall,
                                color = HaloGlassColors.Accent
                            )
                        }
                    }
                    if (index < uiState.availableLocales.lastIndex) {
                        HaloGlassDivider()
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        if (uiState.isDownloading) {
            HaloGlassCard {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Downloading Whisper Tiny...",
                        style = HaloTypography.titleMedium,
                        color = HaloGlassColors.TextPrimary
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = uiState.currentFileDownloading ?: "Downloading model files",
                        style = HaloTypography.bodySmall,
                        color = HaloGlassColors.TextMuted
                    )
                    Spacer(Modifier.height(12.dp))
                    LinearProgressIndicator(
                        progress = { uiState.downloadProgress },
                        modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                        color = HaloGlassColors.Accent,
                        trackColor = HaloGlassColors.Glass
                    )
                }
            }
        } else if (!uiState.isInstalled) {
            HaloGlassCard {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Halo needs the local speech model for private voice input.",
                        style = HaloTypography.bodyMedium,
                        color = HaloGlassColors.TextSecondary,
                        lineHeight = 20.sp
                    )
                    if (uiState.downloadError != null) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "Error: ${uiState.downloadError}",
                            style = HaloTypography.bodySmall,
                            color = HaloGlassColors.Danger
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                    HaloGlassButton(
                        text = "Install Whisper Tiny",
                        onClick = { viewModel.installWhisperTiny() },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        } else {
            HaloGlassCard {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Whisper Tiny is installed and ready for local speech recognition.",
                        style = HaloTypography.bodySmall,
                        color = HaloGlassColors.TextMuted
                    )
                    if (uiState.downloadError != null) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "Error: ${uiState.downloadError}",
                            style = HaloTypography.bodySmall,
                            color = HaloGlassColors.Danger
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    HaloGlassButton(
                        text = "Reinstall Whisper Tiny",
                        onClick = { viewModel.installWhisperTiny() },
                        modifier = Modifier.fillMaxWidth(),
                        isPrimary = false
                    )
                }
            }
        }

        Spacer(Modifier.height(32.dp))
    }
}
