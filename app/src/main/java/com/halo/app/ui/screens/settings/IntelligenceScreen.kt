package com.halo.app.ui.screens.settings

import com.halo.app.language.uiText

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.halo.app.ai.ProviderControlRepository
import com.halo.app.ai.label
import com.halo.app.ui.design.HaloScreenHeader
import com.halo.app.ui.icons.HaloGlyph
import com.halo.app.ui.workspace.HaloWorkspaceSection
import com.halo.app.ui.workspace.HaloWorkspaceDestination
import com.halo.sdk.provider.AiExecutionLocation

@Composable
fun IntelligenceScreen(control: ProviderControlRepository, onBack: () -> Unit,
    onBrowseModels: () -> Unit, onProviderSetup: () -> Unit, onRoles: () -> Unit,
    onDevice: () -> Unit, onContext: () -> Unit, onDiagnostics: () -> Unit,
    onVisionLab: () -> Unit = {}, onAudioLab: () -> Unit = {}) {
    val providerId by control.router.selectedProviderId.collectAsStateWithLifecycle()
    val modelId by control.router.selectedModelId.collectAsStateWithLifecycle()
    val rows by control.rows.collectAsStateWithLifecycle()
    val active = rows.firstOrNull { it.descriptor.providerId == providerId }
    val local = rows.firstOrNull { it.descriptor.location == AiExecutionLocation.ON_DEVICE }
    val step = intelligenceNextStep(active?.configured == true, active?.connection, !modelId.isNullOrBlank(), active?.generation)
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(com.halo.app.ui.design.HaloSpacing.screen), verticalArrangement = Arrangement.spacedBy(24.dp)) {
        item(key = "header") { HaloScreenHeader(uiText("Intelligence"), uiText("Choose how Halo answers"), navigation = onBack, glyph = HaloGlyph.MODEL) }
        item(key = "active") {
            Card(Modifier.fillMaxWidth(), shape = com.halo.app.ui.design.HaloShapes.settingsCard, border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                Column(Modifier.padding(com.halo.app.ui.design.HaloSpacing.screen), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(uiText("Your conversation"), style = MaterialTheme.typography.labelLarge)
                    Text(active?.descriptor?.displayName ?: uiText("Choose an AI provider"), style = MaterialTheme.typography.titleMedium)
                    Text(modelId ?: uiText("No model selected"), style = MaterialTheme.typography.bodyMedium)
                    active?.let { Text(uiText(executionLocationLabel(it.descriptor.location)), style = MaterialTheme.typography.labelMedium) }
                    Text(uiText(active?.connection?.label ?: "Setup required"), style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = if (active?.configured == true) onBrowseModels else onProviderSetup) {
                        Text(uiText(if (active?.configured == true) "Change model" else "Set up provider"))
                    }
                }
            }
        }
        item(key = "next") {
            IntelligenceGuidance(step, onProviderSetup, onBrowseModels,
                { active?.let { control.testConnection(it.descriptor.providerId) } },
                { active?.let { control.checkGeneration(it.descriptor.providerId) } })
        }
        item(key = "connections") {
            HaloSettingsSection(uiText("Models & connections")) {
                HaloWorkspaceDestination(uiText("Providers"), uiText("%1\$d configured providers", rows.count { it.configured }), onProviderSetup)
                HaloWorkspaceDestination(uiText("Model library"), uiText("Browse available models"), onBrowseModels)
                HaloWorkspaceDestination(uiText("On device"), uiText(local?.connection?.label ?: "Setup required"), onDevice)
            }
        }
        item(key = "preferences") {
            HaloSettingsSection(uiText("Preferences")) {
                HaloWorkspaceDestination(uiText("Context & privacy"), uiText("Choose what Halo can use"), onContext)
                HaloWorkspaceDestination(uiText("Model roles"), uiText("Route different kinds of work"), onRoles)
            }
        }
        item(key = "diagnostics") {
            HaloSettingsSection(uiText("Model diagnostics")) {
                HaloWorkspaceDestination(uiText("Vision Lab"), uiText("Verify and compare image models"), onVisionLab)
                HaloWorkspaceDestination(uiText("Local audio test"), uiText("Try an audio file with an installed model"), onAudioLab)
                HaloWorkspaceDestination(uiText("System check"), uiText("Check what works and share a safe report"), onDiagnostics)
            }
        }
    }
}
