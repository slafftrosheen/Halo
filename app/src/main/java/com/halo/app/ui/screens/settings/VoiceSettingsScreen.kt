package com.halo.app.ui.screens.settings

import androidx.compose.foundation.background
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.halo.app.ui.theme.HaloPastelBlue
import com.halo.app.ui.theme.HaloPastelMint
import com.halo.app.ui.workspace.HaloWorkspaceButton
import com.halo.app.ui.workspace.HaloWorkspaceCard
import com.halo.app.ui.workspace.HaloWorkspaceHeader
import com.halo.app.ui.workspace.HaloWorkspaceRow
import com.halo.app.ui.workspace.HaloWorkspaceSection

@Composable
fun VoiceSettingsScreen(
    viewModel: VoiceSettingsViewModel,
    onBack: () -> Unit,
    onElevenLabs: () -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsState()
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshState()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 14.dp)
    ) {
        HaloWorkspaceHeader(
            title = "Voice",
            subtitle = "Speech input, output and audio routing.",
            onBack = onBack
        )

        Spacer(Modifier.height(20.dp))

        SpeechOutputSettings(viewModel, onElevenLabs)
        Spacer(Modifier.height(20.dp))
        HaloWorkspaceSection(title = "Speech recognition") {
            HaloWorkspaceRow(
                title = "Model",
                trailingText = "Whisper Tiny"
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            HaloWorkspaceRow(
                title = "Status",
                trailingText = when {
                    uiState.isDownloading -> "Downloading ${(uiState.downloadProgress * 100).toInt()}%"
                    uiState.isInstalled -> "Ready"
                    else -> "Not installed"
                },
                selected = uiState.isInstalled
            )
            if (uiState.isInstalled) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                val megabytes = uiState.installedSizeBytes / (1024 * 1024)
                HaloWorkspaceRow(
                    title = "Storage",
                    trailingText = "~$megabytes MB"
                )
            }
        }

        Spacer(Modifier.height(20.dp))

        var languagePicker by remember { mutableStateOf(false) }
        HaloWorkspaceSection(title = "Language") {
            HaloWorkspaceRow(title = uiState.availableLocales.firstOrNull { it.tag == uiState.selectedLocale }?.displayName ?: uiState.selectedLocale,
                trailingText = "Change", onClick = { languagePicker = true })
        }
        if (languagePicker) AlertDialog(onDismissRequest = { languagePicker = false }, title = { Text("Speech recognition language") },
            text = { Column { uiState.availableLocales.forEach { option ->
                TextButton(onClick = { viewModel.selectLocale(option.tag); languagePicker = false }) { Text(option.displayName) }
            } } }, confirmButton = { TextButton(onClick = { languagePicker = false }) { Text("Close") } })

        Spacer(Modifier.height(20.dp))

        HaloWorkspaceSection(title = "Wearable audio") {
            HaloWorkspaceRow(title = "Reply on glasses", trailingText = "On")
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            HaloWorkspaceRow(
                title = "If glasses audio is unavailable",
                trailingText = if (uiState.wearablePhoneFallback) "Use phone speaker" else "Stay silent",
                onClick = { viewModel.setWearablePhoneFallback(!uiState.wearablePhoneFallback) }
            )
        }

        Spacer(Modifier.height(20.dp))

        when {
            uiState.isDownloading -> {
                HaloWorkspaceCard(accent = HaloPastelBlue) {
                    Text(
                        text = "Downloading Whisper Tiny",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = uiState.currentFileDownloading ?: "Downloading model files",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { uiState.downloadProgress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp),
                        color = MaterialTheme.colorScheme.secondary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                }
            }
            !uiState.isInstalled -> {
                HaloWorkspaceCard {
                    Text(
                        text = "Local voice input needs the speech model.",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "The model is stored on this device and works without a cloud speech provider.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (uiState.downloadError != null) {
                        Text(
                            text = "Error: ${uiState.downloadError}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    HaloWorkspaceButton(
                        text = "Install Whisper Tiny",
                        onClick = viewModel::installWhisperTiny,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
            else -> {
                HaloWorkspaceCard(accent = HaloPastelMint) {
                    Text(
                        text = "Whisper Tiny is ready",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Speech recognition remains available locally.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (uiState.downloadError != null) {
                        Text(
                            text = "Error: ${uiState.downloadError}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    HaloWorkspaceButton(
                        text = "Reinstall Whisper Tiny",
                        onClick = viewModel::installWhisperTiny,
                        modifier = Modifier.fillMaxWidth(),
                        primary = false
                    )
                }
            }
        }

        Spacer(Modifier.height(32.dp))
    }
}
