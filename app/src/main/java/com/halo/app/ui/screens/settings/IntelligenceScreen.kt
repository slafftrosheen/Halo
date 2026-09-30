package com.halo.app.ui.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.halo.app.ai.ProviderControlRepository

/** Overview observes only small selection/status projections, never the full catalogue. */
@Composable
fun IntelligenceScreen(control: ProviderControlRepository, onBack: () -> Unit,
    onBrowseModels: () -> Unit, onProviderSetup: () -> Unit, onRoles: () -> Unit,
    onDevice: () -> Unit, onPolicy: () -> Unit, onDiagnostics: () -> Unit) {
    val providerId by control.router.selectedProviderId.collectAsState()
    val modelId by control.router.selectedModelId.collectAsState()
    val rows by control.rows.collectAsState()
    val active = rows.firstOrNull { it.descriptor.providerId == providerId }
    Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        TextButton(onClick = onBack) { Text("Back") }
        Text("Intelligence", style = MaterialTheme.typography.headlineMedium)
        Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp)) {
            Text(active?.descriptor?.displayName ?: providerId ?: "Choose an AI provider", style = MaterialTheme.typography.titleMedium)
            Text(modelId ?: "No model selected")
            Text(active?.connection?.name?.replace('_', ' ') ?: "Configuration unavailable")
            if (active?.configured == false) Text("This provider needs setup", color = MaterialTheme.colorScheme.error)
            TextButton(onClick = onBrowseModels) { Text("Change model") }
        } }
        TextButton(onClick = onProviderSetup) { Text("Providers and credentials") }
        TextButton(onClick = onBrowseModels) { Text("Browse models") }
        TextButton(onClick = onRoles) { Text("Model roles and routing") }
        TextButton(onClick = onDevice) { Text("On-device models") }
        TextButton(onClick = onPolicy) { Text("Context sharing and advanced settings") }
        TextButton(onClick = onDiagnostics) { Text("Usage and diagnostics") }
    }
}
