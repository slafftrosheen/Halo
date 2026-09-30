package com.halo.app.ui.screens.settings

import androidx.lifecycle.compose.collectAsStateWithLifecycle

import com.halo.app.ui.design.HaloScreenHeader
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.halo.sdk.provider.*

@Composable
fun ModelBrowserScreen(viewModel: ModelBrowserViewModel, onBack: () -> Unit, onProvider: (String) -> Unit,
    initialProvider: String? = null, initialRole: AiModelRole? = null) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val filter by viewModel.filter.collectAsStateWithLifecycle()
    val providers by viewModel.control.rows.collectAsStateWithLifecycle()
    val selectedProvider by viewModel.control.router.selectedProviderId.collectAsStateWithLifecycle()
    val selectedModel by viewModel.control.router.selectedModelId.collectAsStateWithLifecycle()
    var showFilters by rememberSaveable { mutableStateOf(false) }
    val providersById = remember(providers) { providers.associateBy { it.descriptor.providerId } }
    LaunchedEffect(initialProvider, initialRole) { viewModel.enter(initialProvider, initialRole) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item(key = "header") { HaloScreenHeader(initialRole?.let { "Choose ${it.name.lowercase()} model" } ?: "Models", navigation = onBack) }
        item {
            OutlinedTextField(filter.query, { viewModel.filter.value = filter.copy(query = it) }, singleLine = true, label = { Text("Search models") }, modifier = Modifier.fillMaxWidth())
            TextButton(onClick = { showFilters = !showFilters }) {
                Text(if (showFilters) "Hide filters" else "Filters")
            }
            if (showFilters) {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ModelCostFilter.entries.forEach { cost ->
                    FilterChip(filter.cost == cost, { viewModel.filter.value = filter.copy(cost = cost) },
                        label = { Text(when (cost) { ModelCostFilter.ALL -> "All costs"; ModelCostFilter.FREE -> "Free"; ModelCostFilter.PAID -> "Paid / account dependent" }) })
                }
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
                listOf(AiCapability.TEXT, AiCapability.IMAGE, AiCapability.TOOLS, AiCapability.AUDIO).forEach { capability -> FilterChip(filter.capability == capability, { viewModel.filter.value = filter.copy(capability = capability) }, label = { Text(if (capability == AiCapability.IMAGE) "Vision" else capability.name.lowercase().replaceFirstChar { it.uppercase() }) }) }
            }
            }
            if (state.errors) Text("Some catalogues are unavailable or stale. Retained metadata remains browsable.")
            TextButton(enabled = !state.refreshing, onClick = { (filter.provider ?: selectedProvider)?.let { viewModel.control.refreshModels(it) } }) { Text(if (state.refreshing) "Refreshing…" else "Refresh selected provider metadata") }
            (filter.provider ?: selectedProvider)?.let { id -> TextButton(onClick = { onProvider(id) }) { Text("Provider setup") } }
        }
        if (state.entries.isEmpty()) item { Text("No cached models match. Configure a provider and explicitly refresh its metadata, or download an on-device model.") }
        items(state.entries, key = { "${it.model.providerId}:${it.model.id}" }) { entry ->
            Card(onClick = { if (providersById[entry.model.providerId]?.configured == true) viewModel.pick(entry) else onProvider(entry.model.providerId) }, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp)) {
                    val selected = entry.model.providerId == selectedProvider && entry.model.id == selectedModel
                    if (providersById[entry.model.providerId]?.configured != true) Text("Provider setup required")
                    Text(entry.model.displayName, style = MaterialTheme.typography.titleMedium)
                    Text(providersById[entry.model.providerId]?.descriptor?.displayName ?: entry.model.providerId)
                    Text(listOf(AiCapability.TEXT, AiCapability.IMAGE, AiCapability.TOOLS, AiCapability.AUDIO)
                        .filter { ModelBrowserViewModel.supports(entry.model, it) }
                        .joinToString(" · ") { if (it == AiCapability.IMAGE) "Vision" else it.name.lowercase().replaceFirstChar { c -> c.uppercase() } })
                    Text(when (entry.runtime.location) { AiExecutionLocation.ON_DEVICE -> "On device"; AiExecutionLocation.LOCAL_NETWORK -> "Local network"; else -> "Cloud" })
                    if (selected) Text("Selected for Chat")
                }
            }
        }
    }
}

@Composable
fun ModelRolesScreen(control: com.halo.app.ai.ProviderControlRepository, onBack: () -> Unit, onPick: (AiModelRole) -> Unit) {
    val bindings by control.router.roleBindings.collectAsStateWithLifecycle()
    val routing by control.router.routingPolicy.collectAsStateWithLifecycle()
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
