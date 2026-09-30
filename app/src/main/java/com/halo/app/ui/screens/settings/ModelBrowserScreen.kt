package com.halo.app.ui.screens.settings

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.halo.sdk.provider.*

@Composable
fun ModelBrowserScreen(viewModel: ModelBrowserViewModel, onBack: () -> Unit, onProvider: (String) -> Unit,
    initialProvider: String? = null, initialRole: AiModelRole? = null) {
    val state by viewModel.state.collectAsState()
    val filter by viewModel.filter.collectAsState()
    val providers by viewModel.control.rows.collectAsState()
    val selectedProvider by viewModel.control.router.selectedProviderId.collectAsState()
    val selectedModel by viewModel.control.router.selectedModelId.collectAsState()
    LaunchedEffect(initialProvider, initialRole) { viewModel.enter(initialProvider, initialRole) }
    LazyColumn(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { TextButton(onClick = onBack) { Text("Back") }; Text(initialRole?.let { "Choose ${it.name.lowercase()} model" } ?: "Models", style = MaterialTheme.typography.headlineMedium) }
        item {
            OutlinedTextField(filter.query, { viewModel.filter.value = filter.copy(query = it) }, label = { Text("Search models") }, modifier = Modifier.fillMaxWidth())
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(filter.includeRemote, { viewModel.filter.value = filter.copy(includeRemote = !filter.includeRemote) }, label = { Text("All remote models") })
                FilterChip(filter.freeOnly, { viewModel.filter.value = filter.copy(freeOnly = !filter.freeOnly) }, label = { Text("Free models") })
            }
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(filter.provider == null, { viewModel.filter.value = filter.copy(provider = null) }, label = { Text("All providers") })
                providers.forEach { provider -> FilterChip(filter.provider == provider.descriptor.providerId, { viewModel.filter.value = filter.copy(provider = provider.descriptor.providerId) }, label = { Text(provider.descriptor.displayName) }) }
            }
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(filter.location == null, { viewModel.filter.value = filter.copy(location = null) }, label = { Text("All locations") })
                AiExecutionLocation.entries.forEach { location -> FilterChip(filter.location == location, { viewModel.filter.value = filter.copy(location = location) }, label = { Text(location.name.replace('_', ' ')) }) }
            }
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(filter.capability == null, { viewModel.filter.value = filter.copy(capability = null) }, label = { Text("All capabilities") })
                listOf(AiCapability.TEXT, AiCapability.IMAGE, AiCapability.TOOLS).forEach { capability -> FilterChip(filter.capability == capability, { viewModel.filter.value = filter.copy(capability = capability) }, label = { Text(capability.name) }) }
            }
            if (state.errors) Text("Some catalogues are unavailable or stale. Retained metadata remains browsable.")
            TextButton(enabled = !state.refreshing, onClick = { (filter.provider ?: selectedProvider)?.let { viewModel.control.refreshModels(it) } }) { Text(if (state.refreshing) "Refreshing…" else "Refresh selected provider metadata") }
            (filter.provider ?: selectedProvider)?.let { id -> TextButton(onClick = { onProvider(id) }) { Text("Provider setup") } }
        }
        if (state.entries.isEmpty()) item { Text("No cached models match. Configure a provider and explicitly refresh its metadata, or download an on-device model.") }
        items(state.entries, key = { "${it.model.providerId}:${it.model.id}" }) { entry ->
            Card(onClick = { viewModel.pick(entry) }, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp)) {
                    val selected = entry.model.providerId == selectedProvider && entry.model.id == selectedModel
                    Text(entry.model.displayName + if (selected) " · Selected" else "", style = MaterialTheme.typography.titleMedium)
                    Text("${entry.model.providerId} · ${entry.runtime.location.name.replace('_', ' ')} · ${entry.source.name.lowercase()}")
                    Text(if (entry.kind == AiModelKind.ROUTER) "Router / preset · capabilities vary · tools not guaranteed" else "Concrete model · tools ${if (entry.model.supportsTools) "supported" else "unavailable"} · ${entry.runtime.costClass.name.lowercase()}")
                    entry.model.contextLength?.let { Text("Context: $it tokens") }
                }
            }
        }
    }
}

@Composable
fun ModelRolesScreen(control: com.halo.app.ai.ProviderControlRepository, onBack: () -> Unit, onPick: (AiModelRole) -> Unit) {
    val bindings by control.router.roleBindings.collectAsState()
    val routing by control.router.routingPolicy.collectAsState()
    LazyColumn(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            TextButton(onClick = onBack) { Text("Back") }; Text("Model roles and routing", style = MaterialTheme.typography.headlineMedium)
            Text("Routing: ${routing.name.replace('_', ' ')}")
            AiRoutingPolicy.entries.forEach { policy -> TextButton(onClick = { (control.router as? com.halo.engines.ai.DefaultAiProviderRouter)?.setRoutingPolicy(policy) }) { Text(policy.name.replace('_', ' ') + if (routing == policy) " · Selected" else "") } }
        }
        items(AiModelRole.entries.toList(), key = { it.name }) { role ->
            val binding = bindings[role]
            Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp)) {
                Text(role.name, style = MaterialTheme.typography.titleMedium)
                Text("${binding?.selection?.name ?: "INHERIT_DEFAULT"} · ${binding?.modelId ?: "Default model"}")
                if (role != AiModelRole.CHAT) listOf(AiRoleSelection.INHERIT_DEFAULT, AiRoleSelection.AUTOMATIC, AiRoleSelection.DISABLED).forEach { mode ->
                    TextButton(onClick = { control.router.setRoleBinding(AiRoleBinding(role, null, null, mode)) }) { Text(mode.name.replace('_', ' ')) }
                }
                TextButton(onClick = { onPick(role) }) { Text("Choose compatible model") }
            } }
        }
    }
}
