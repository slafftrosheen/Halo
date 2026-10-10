package com.halo.app.ui.screens.settings

import com.halo.app.language.uiText

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
            OutlinedTextField(filter.query, { viewModel.filter.value = filter.copy(query = it) }, singleLine = true, label = { Text(uiText("Search models")) }, modifier = Modifier.fillMaxWidth())
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
                FilterChip(filter.provider == null, { viewModel.filter.value = filter.copy(provider = null) }, label = { Text(uiText("All providers")) })
                providers.forEach { provider -> FilterChip(filter.provider == provider.descriptor.providerId, { viewModel.filter.value = filter.copy(provider = provider.descriptor.providerId) }, label = { Text(provider.descriptor.displayName) }) }
            }
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(filter.location == null, { viewModel.filter.value = filter.copy(location = null) }, label = { Text(uiText("All locations")) })
                AiExecutionLocation.entries.forEach { location -> FilterChip(filter.location == location, { viewModel.filter.value = filter.copy(location = location) }, label = { Text(location.name.replace('_', ' ')) }) }
            }
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(filter.capability == null, { viewModel.filter.value = filter.copy(capability = null) }, label = { Text(uiText("All capabilities")) })
                listOf(AiCapability.TEXT, AiCapability.IMAGE, AiCapability.TOOLS, AiCapability.AUDIO).forEach { capability -> FilterChip(filter.capability == capability, { viewModel.filter.value = filter.copy(capability = capability) }, label = { Text(if (capability == AiCapability.IMAGE) "Vision" else capability.name.lowercase().replaceFirstChar { it.uppercase() }) }) }
            }
            }
            if (state.errors) Text(uiText("Some catalogues are unavailable or stale. Retained metadata remains browsable."))
            TextButton(enabled = !state.refreshing, onClick = { (filter.provider ?: selectedProvider)?.let { viewModel.control.refreshModels(it) } }) { Text(if (state.refreshing) "Refreshing…" else "Refresh selected provider metadata") }
            (filter.provider ?: selectedProvider)?.let { id -> TextButton(onClick = { onProvider(id) }) { Text(uiText("Provider setup")) } }
        }
        if (state.entries.isEmpty()) item { Text(uiText("No cached models match. Configure a provider and explicitly refresh its metadata, or download an on-device model.")) }
        items(state.entries, key = { "${it.model.providerId}:${it.model.id}" }) { entry ->
            Card(onClick = { if (entry.runtime.installed != false && providersById[entry.model.providerId]?.configured == true) viewModel.pick(entry) { if (initialRole != null) onBack() } else onProvider(entry.model.providerId) }, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp)) {
                    val selected = entry.model.providerId == selectedProvider && entry.model.id == selectedModel
                    if (providersById[entry.model.providerId]?.configured != true) Text(uiText("Provider setup required"))
                    Text(entry.model.displayName, style = MaterialTheme.typography.titleMedium)
                    Text(providersById[entry.model.providerId]?.descriptor?.displayName ?: entry.model.providerId)
                    Text(listOf(AiCapability.TEXT, AiCapability.IMAGE, AiCapability.TOOLS, AiCapability.AUDIO)
                        .filter { it in state.candidates[entry.runtime.ref]?.effectiveCapabilities.orEmpty() }
                        .joinToString(" · ") { if (it == AiCapability.IMAGE) "Vision" else it.name.lowercase().replaceFirstChar { c -> c.uppercase() } })
                    Text(when (entry.runtime.location) { AiExecutionLocation.ON_DEVICE -> "On device"; AiExecutionLocation.LOCAL_NETWORK -> "Local network"; else -> "Cloud" })
                    if (entry.runtime.installed == false) Text(uiText("Available to download or import · Open provider setup"))
                    state.candidates[entry.runtime.ref]?.let { candidate ->
                        if (AiCapability.TOOLS in candidate.effectiveCapabilities) Text(toolProtocolLabel(candidate.toolProtocol))
                        if (!candidate.eligible) Text(candidate.rejectionReasons.joinToString { it.name.lowercase().replace('_', ' ') })
                    }
                    if (selected) Text(uiText("Conversation default"))
                }
            }
        }
    }
}

@Composable
fun ModelRolesScreen(control: com.halo.app.ai.ProviderControlRepository, onBack: () -> Unit, onPick: (AiModelRole) -> Unit,
    evidence: com.halo.app.ai.LocalRoleEvidenceController? = null, onExplore: (AiModelRole) -> Unit = {}) {
    val measured by (evidence?.state ?: remember { kotlinx.coroutines.flow.MutableStateFlow(com.halo.app.ai.LocalRoleEvidenceState()) }).collectAsStateWithLifecycle()
    val message by (evidence?.message ?: remember { kotlinx.coroutines.flow.MutableStateFlow<String?>(null) }).collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { evidence?.invalidate() }
    val bindings by control.router.roleBindings.collectAsStateWithLifecycle()
    val candidates by control.roleCandidates.collectAsStateWithLifecycle()
    val catalogue by control.catalogue.state.collectAsStateWithLifecycle()
    val selectedProvider by control.router.selectedProviderId.collectAsStateWithLifecycle()
    val selectedModel by control.router.selectedModelId.collectAsStateWithLifecycle()
    val routing by control.router.routingPolicy.collectAsStateWithLifecycle()
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            HaloScreenHeader(uiText("Model roles"), navigation = onBack)
            message?.let { Text(it) }
            Text("Routing: ${routing.name.lowercase().replace('_', ' ')}")
            AiRoutingPolicy.entries.forEach { policy -> TextButton(onClick = { (control.router as? com.halo.engines.ai.DefaultAiProviderRouter)?.setRoutingPolicy(policy) }) { Text(policy.name.lowercase().replace('_', ' ') + if (routing == policy) " · Selected" else "") } }
        }
        items(AiModelRole.entries.toList(), key = { it.name }) { role ->
            val binding = bindings[role] ?: control.router.roleBinding(role)
            val target = control.roleTarget(role, candidates)
            val ref = if (role == AiModelRole.CHAT) AiModelRef(selectedProvider.orEmpty(), selectedModel.orEmpty()) else target?.ref
            val model = catalogue.models.firstOrNull { it.runtime.ref == ref }
            Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp)) {
                Text(uiText(when(role) {
                    AiModelRole.CHAT -> "Conversation"
                    AiModelRole.FAST -> "Quick replies"
                    AiModelRole.REASONING -> "Careful thinking"
                    AiModelRole.AGENTIC -> "Actions and tools"
                    AiModelRole.VISION -> "Images"
                }), style = MaterialTheme.typography.titleMedium)
                Text(when (role) {
                    AiModelRole.CHAT -> "Normal conversation · Conversation default"
                    AiModelRole.FAST -> "Quick conversational responses"
                    AiModelRole.REASONING -> "Deeper analysis · Independent model assignment"
                    AiModelRole.AGENTIC -> "Tasks that need tools"
                    AiModelRole.VISION -> "Images and visual context"
                })
                if (role != AiModelRole.CHAT) Text(roleModeLabel(binding.selection))
                Text(uiText("Configured"), style = MaterialTheme.typography.labelMedium)
                Text(model?.model?.displayName ?: when {
                    binding.selection == AiRoleSelection.DISABLED && role != AiModelRole.CHAT -> "Off"
                    binding.selection == AiRoleSelection.EXPLICIT -> "Configured model unavailable"
                    else -> "No compatible model available"
                })
                if (role != AiModelRole.CHAT && binding.selection == AiRoleSelection.EXPLICIT && target?.eligible != true && model != null)
                    Text(uiText("Configured model unavailable"))
                target?.let {
                    Text(when (it.executionLocation) { AiExecutionLocation.ON_DEVICE -> "On device"; AiExecutionLocation.LOCAL_NETWORK -> "Local network"; AiExecutionLocation.REMOTE -> "Cloud" })
                    if (role == AiModelRole.AGENTIC) Text(toolProtocolLabel(it.toolProtocol))
                    if (!it.eligible) Text(it.rejectionReasons.joinToString { reason -> reason.name.lowercase().replace('_', ' ') })
                }
                if (role != AiModelRole.CHAT) listOf(AiRoleSelection.INHERIT_DEFAULT, AiRoleSelection.AUTOMATIC, AiRoleSelection.DISABLED).forEach { mode ->
                    TextButton(onClick = { control.router.setRoleBinding(AiRoleBinding(role, null, null, mode)) }) { Text(roleModeLabel(mode)) }
                }
                Text(uiText("Measured"), style = MaterialTheme.typography.labelMedium)
                measured.current.forEach { row -> Text("${row.artifactId}: " + when(role) {
                    AiModelRole.FAST -> "${row.fastVerdict} · ${row.warmMedianMs ?: "—"} ms"
                    AiModelRole.CHAT -> "${row.chatPassed}/${row.chatTotal}"
                    AiModelRole.REASONING -> "${row.reasoningPassed}/${row.reasoningTotal}"
                    AiModelRole.AGENTIC -> "${row.toolProtocol} · ${row.agenticVerdict}"
                    AiModelRole.VISION -> row.visionVerdict
                } + if (!row.stable) " · Measured but unstable" else "") }
                if (measured.current.isEmpty()) Text(uiText("Not measured on this device"))
                val suggestion = measured.recommendations[role]
                suggestion?.recommended?.let { recommended ->
                    Text("Suggested: ${recommended.modelId}", style = MaterialTheme.typography.labelMedium)
                    Text(if (suggestion.confidence == LocalRoleRecommendationConfidence.COMPARATIVE)
                        "Best measured on this device" else "Only measured candidate")
                    Text(when(role) {
                        AiModelRole.FAST -> "CHAT 3/3 and three warm samples; lowest measured warm latency"
                        AiModelRole.CHAT -> "CHAT 3/3; reasoning, context and latency evidence"
                        AiModelRole.REASONING -> "CHAT and reasoning probes 3/3; stable execution"
                        AiModelRole.AGENTIC -> "Proven initial tool and continuation loop; native protocol preferred"
                        AiModelRole.VISION -> "Verified image evidence from Vision Lab"
                    })
                    TextButton(onClick = { evidence?.applyRoleRecommendation(role, recommended) }) { Text("Use for ${role.name.lowercase()}") }
                }
                TextButton(onClick = { onPick(role) }) { Text(uiText("Choose model")) }
                TextButton(onClick = { onExplore(role) }) { Text(uiText("Explore local candidates")) }
            } }
        }
    }
}

internal fun roleModeLabel(mode: AiRoleSelection): String = when (mode) {
    AiRoleSelection.INHERIT_DEFAULT -> "Use conversation model"
    AiRoleSelection.AUTOMATIC -> "Choose automatically"
    AiRoleSelection.EXPLICIT -> "Use selected model"
    AiRoleSelection.DISABLED -> "Off"
}

internal fun toolProtocolLabel(protocol: ToolCallProtocol): String = when (protocol) {
    ToolCallProtocol.NATIVE -> "Native tool loop"
    ToolCallProtocol.HALO_PROMPT -> "Halo prompt tool loop"
    ToolCallProtocol.NONE -> "No tool loop"
}
