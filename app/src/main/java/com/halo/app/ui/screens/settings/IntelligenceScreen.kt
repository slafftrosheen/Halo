package com.halo.app.ui.screens.settings

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
import com.halo.app.ui.workspace.HaloWorkspaceSection
import com.halo.app.ui.workspace.HaloWorkspaceDestination
import com.halo.sdk.provider.AiExecutionLocation

@Composable
fun IntelligenceScreen(control: ProviderControlRepository, onBack: () -> Unit,
    onBrowseModels: () -> Unit, onProviderSetup: () -> Unit, onRoles: () -> Unit,
    onDevice: () -> Unit, onContext: () -> Unit, onDiagnostics: () -> Unit,
    onVoice: () -> Unit = {}) {
    val providerId by control.router.selectedProviderId.collectAsStateWithLifecycle()
    val modelId by control.router.selectedModelId.collectAsStateWithLifecycle()
    val rows by control.rows.collectAsStateWithLifecycle()
    val active = rows.firstOrNull { it.descriptor.providerId == providerId }
    val local = rows.firstOrNull { it.descriptor.location == AiExecutionLocation.ON_DEVICE }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(24.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
        item(key = "header") { HaloScreenHeader("Intelligence", "Choose how Halo thinks.", navigation = onBack) }
        item(key = "active") {
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Your conversation", style = MaterialTheme.typography.labelLarge)
                    Text(active?.descriptor?.displayName ?: "Choose an AI provider", style = MaterialTheme.typography.titleMedium)
                    Text(modelId ?: "No model selected", style = MaterialTheme.typography.bodyMedium)
                    Text(active?.connection?.label ?: "Setup required", style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = if (active?.configured == true) onBrowseModels else onProviderSetup) {
                        Text(if (active?.configured == true) "Change model" else "Set up provider")
                    }
                }
            }
        }
        item(key = "connections") {
            HaloWorkspaceSection("Models & connections") {
                HaloWorkspaceDestination("Providers", "${rows.count { it.configured }} configured", onProviderSetup)
                HaloWorkspaceDestination("Model library", "Browse available models", onBrowseModels)
                HaloWorkspaceDestination("On device", local?.connection?.label ?: "Setup required", onDevice)
            }
        }
        item(key = "preferences") {
            HaloWorkspaceSection("Preferences") {
                HaloWorkspaceDestination("Context & privacy", "Choose what Halo can use", onContext)
                HaloWorkspaceDestination("Model roles", "Route different kinds of work", onRoles)
                HaloWorkspaceDestination("Voice", "Listening and speaking", onVoice)
            }
        }
        item(key = "diagnostics") { TextButton(onClick = onDiagnostics) { Text("System check") } }
    }
}
