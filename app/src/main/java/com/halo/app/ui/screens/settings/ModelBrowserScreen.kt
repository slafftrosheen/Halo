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
import androidx.compose.ui.Alignment
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.ui.semantics.Role
import com.halo.app.ui.design.HaloStatusChip
import com.halo.app.ui.design.HaloSegmentedTabs
import com.halo.app.ui.design.HaloShapes
import com.halo.app.ui.icons.HaloIcon
import androidx.compose.ui.unit.dp
import com.halo.sdk.provider.*
import com.halo.app.ui.design.HaloEmptyState
import com.halo.app.ui.design.HaloHeaderAction
import com.halo.app.ui.icons.HaloGlyph

@OptIn(ExperimentalLayoutApi::class)
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
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(com.halo.app.ui.design.HaloSpacing.screen), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item(key = "header") { HaloScreenHeader(uiText("Model library"), initialRole?.let { uiText(modelRoleLabel(it)) } ?: uiText("Choose what runs where"), navigation = onBack) }
        item {
            OutlinedTextField(filter.query, { viewModel.filter.value = filter.copy(query = it) }, singleLine = true, shape = HaloShapes.pill, leadingIcon = { HaloIcon(HaloGlyph.SEARCH, null, Modifier.size(24.dp)) }, label = { Text(uiText("Search models")) }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(12.dp))
            val locations = listOf(null, AiExecutionLocation.ON_DEVICE, AiExecutionLocation.REMOTE)
            HaloSegmentedTabs(
                labels = listOf(uiText("All"), uiText("On this phone"), uiText("In the cloud")),
                selected = locations.indexOf(filter.location).coerceAtLeast(0),
                onSelect = { viewModel.filter.value = filter.copy(location = locations[it]) })
            TextButton(onClick = { showFilters = !showFilters }) {
                Text(uiText(if (showFilters) "Hide filters" else "Filters"))
            }
            if (showFilters) {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ModelCostFilter.entries.forEach { cost ->
                    FilterChip(filter.cost == cost, { viewModel.filter.value = filter.copy(cost = cost) },
                        label = { Text(uiText(when (cost) { ModelCostFilter.ALL -> "All costs"; ModelCostFilter.FREE -> "Free"; ModelCostFilter.PAID -> "Paid / account dependent" })) })
                }
            }
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(filter.provider == null, { viewModel.filter.value = filter.copy(provider = null) }, label = { Text(uiText("All providers")) })
                providers.forEach { provider -> FilterChip(filter.provider == provider.descriptor.providerId, { viewModel.filter.value = filter.copy(provider = provider.descriptor.providerId) }, label = { Text(provider.descriptor.displayName) }) }
            }
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(filter.location == null, { viewModel.filter.value = filter.copy(location = null) }, label = { Text(uiText("All locations")) })
                AiExecutionLocation.entries.forEach { location -> FilterChip(filter.location == location, { viewModel.filter.value = filter.copy(location = location) }, label = { Text(uiText(executionLocationLabel(location))) }) }
            }
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(filter.capability == null, { viewModel.filter.value = filter.copy(capability = null) }, label = { Text(uiText("All capabilities")) })
                listOf(AiCapability.TEXT, AiCapability.IMAGE, AiCapability.TOOLS, AiCapability.AUDIO).forEach { capability -> FilterChip(filter.capability == capability, { viewModel.filter.value = filter.copy(capability = capability) }, label = { Text(uiText(capabilityLabel(capability))) }) }
            }
            }
            if (state.errors) Text(uiText("Some catalogues are unavailable or stale. Retained metadata remains browsable."))
            TextButton(enabled = !state.refreshing && (filter.provider ?: selectedProvider) != null, onClick = { (filter.provider ?: selectedProvider)?.let { viewModel.control.refreshModels(it) } }) { Text(uiText(if (state.refreshing) "Refreshing…" else "Refresh selected provider metadata")) }
            (filter.provider ?: selectedProvider)?.let { id -> TextButton(onClick = { onProvider(id) }) { Text(uiText("Provider setup")) } }
        }
        if (state.entries.isEmpty()) item {
            HaloEmptyState(uiText("No matching models"), uiText("No cached models match. Configure a provider and explicitly refresh its metadata, or download an on-device model."), HaloGlyph.MODEL,
                action = HaloHeaderAction(uiText("Clear filters")) { viewModel.filter.value = ModelBrowserFilter(provider = initialProvider, role = initialRole) })
        }
        items(state.entries, key = { "${it.model.providerId}:${it.model.id}" }) { entry ->
            val needsSetup = entry.runtime.installed == false || providersById[entry.model.providerId]?.configured != true
            Card(shape = HaloShapes.settingsCard,
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    val selected = entry.model.providerId == selectedProvider && entry.model.id == selectedModel
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Surface(shape = HaloShapes.control, color = MaterialTheme.colorScheme.secondaryContainer) {
                            HaloIcon(HaloGlyph.MODEL, null, Modifier.padding(10.dp).size(24.dp))
                        }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(entry.model.displayName, style = MaterialTheme.typography.titleMedium)
                            Text(providersById[entry.model.providerId]?.descriptor?.displayName ?: uiText("Provider unavailable"),
                                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        HaloStatusChip(uiText(executionLocationLabel(entry.runtime.location)))
                        if (selected) HaloStatusChip(uiText("Conversation default"), active = true)
                    }
                    val capabilities = listOf(AiCapability.TEXT, AiCapability.IMAGE, AiCapability.TOOLS, AiCapability.AUDIO)
                        .filter { it in state.candidates[entry.runtime.ref]?.effectiveCapabilities.orEmpty() }
                    if (capabilities.isNotEmpty()) Text(capabilities.map { uiText(capabilityLabel(it)) }.joinToString(" · "),
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (providersById[entry.model.providerId]?.configured != true) Text(uiText("Provider setup required"),
                        style = MaterialTheme.typography.bodyMedium)
                    if (entry.runtime.installed == false) Text(uiText("Available to download or import · Open provider setup"),
                        style = MaterialTheme.typography.bodyMedium)
                    state.candidates[entry.runtime.ref]?.let { candidate ->
                        if (!candidate.eligible) Text(uiText("This model cannot be selected for this task with the current setup."),
                            style = MaterialTheme.typography.bodyMedium)
                        if (AiCapability.TOOLS in candidate.effectiveCapabilities) {
                            var details by rememberSaveable(entry.model.providerId, entry.model.id) { mutableStateOf(false) }
                            TextButton(onClick = { details = !details }, modifier = Modifier.heightIn(min = 48.dp)) {
                                Text(uiText(if (details) "Hide details" else "Details"))
                            }
                            if (details) Text(uiText(toolProtocolLabel(candidate.toolProtocol)),
                                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    TextButton(
                        enabled = needsSetup || state.candidates[entry.runtime.ref]?.eligible == true,
                        onClick = {
                            if (needsSetup) onProvider(entry.model.providerId)
                            else viewModel.pick(entry) { if (initialRole != null) onBack() }
                        }, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text(uiText(if (needsSetup) "Provider setup" else "Choose model"))
                    }
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
    var routingOptions by rememberSaveable { mutableStateOf(false) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(com.halo.app.ui.design.HaloSpacing.screen), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            HaloScreenHeader(uiText("Model roles"), uiText("Which model does which job"), navigation = onBack)
            message?.let { Text(uiText(it)) }
            TextButton(onClick = { routingOptions = !routingOptions }, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(uiText(if (routingOptions) "Hide routing options" else "Routing options"))
            }
            if (routingOptions) Column(Modifier.selectableGroup()) {
                AiRoutingPolicy.entries.forEach { policy ->
                    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).selectable(
                        selected = routing == policy, role = Role.RadioButton,
                        onClick = { (control.router as? com.halo.engines.ai.DefaultAiProviderRouter)?.setRoutingPolicy(policy) }),
                        verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = routing == policy, onClick = null)
                        Text(uiText(routingPolicyLabel(policy)), Modifier.weight(1f))
                    }
                }
            }
        }
        items(AiModelRole.entries.toList(), key = { it.name }) { role ->
            val binding = bindings[role] ?: control.router.roleBinding(role)
            val target = control.roleTarget(role, candidates)
            val ref = if (role == AiModelRole.CHAT) AiModelRef(selectedProvider.orEmpty(), selectedModel.orEmpty()) else target?.ref
            val model = catalogue.models.firstOrNull { it.runtime.ref == ref }
            Card(Modifier.fillMaxWidth(), shape = HaloShapes.card,
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Surface(shape = HaloShapes.control, color = MaterialTheme.colorScheme.secondaryContainer) {
                        HaloIcon(when (role) {
                            AiModelRole.CHAT -> HaloGlyph.CONVERSATION
                            AiModelRole.FAST -> HaloGlyph.ACTION
                            AiModelRole.REASONING -> HaloGlyph.THINK
                            AiModelRole.AGENTIC -> HaloGlyph.TOOL
                            AiModelRole.VISION -> HaloGlyph.SEE
                        }, null, Modifier.padding(10.dp).size(24.dp))
                    }
                    Text(uiText(modelRoleLabel(role)), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                }
                Text(uiText(when (role) {
                    AiModelRole.CHAT -> "Normal conversation · Conversation default"
                    AiModelRole.FAST -> "Quick conversational responses"
                    AiModelRole.REASONING -> "Deeper analysis · Independent model assignment"
                    AiModelRole.AGENTIC -> "Tasks that need tools"
                    AiModelRole.VISION -> "Images and visual context"
                }), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (role != AiModelRole.CHAT) Text(uiText(roleModeLabel(binding.selection)),
                    style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(model?.model?.displayName ?: when {
                    binding.selection == AiRoleSelection.DISABLED && role != AiModelRole.CHAT -> uiText("Off")
                    binding.selection == AiRoleSelection.EXPLICIT -> uiText("Configured model unavailable")
                    else -> uiText("No compatible model available")
                }, style = MaterialTheme.typography.bodyLarge)
                if (role != AiModelRole.CHAT && binding.selection == AiRoleSelection.EXPLICIT && target?.eligible != true && model != null)
                    Text(uiText("Configured model unavailable"))
                target?.let {
                    HaloStatusChip(uiText(executionLocationLabel(it.executionLocation)))
                    if (!it.eligible) Text(uiText("This model cannot be selected for this task with the current setup."))
                }
                var roleOptions by rememberSaveable(role.name) { mutableStateOf(false) }
                if (role != AiModelRole.CHAT) {
                    TextButton(onClick = { roleOptions = !roleOptions }, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text(uiText(if (roleOptions) "Hide role options" else "Role options"))
                    }
                    if (roleOptions) Column(Modifier.selectableGroup()) {
                        listOf(AiRoleSelection.INHERIT_DEFAULT, AiRoleSelection.AUTOMATIC, AiRoleSelection.DISABLED).forEach { mode ->
                            Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).selectable(
                                selected = binding.selection == mode, role = Role.RadioButton,
                                onClick = { control.router.setRoleBinding(AiRoleBinding(role, null, null, mode)) }),
                                verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(selected = binding.selection == mode, onClick = null)
                                Text(uiText(roleModeLabel(mode)), Modifier.weight(1f))
                            }
                        }
                    }
                }
                TextButton(onClick = { onPick(role) }) { Text(uiText("Choose model")) }
                var measuredDetails by rememberSaveable(role.name) { mutableStateOf(false) }
                TextButton(onClick = { measuredDetails = !measuredDetails }) { Text(uiText(if (measuredDetails) "Hide measured details" else "Measured details")) }
                if (measuredDetails) {
                if (role == AiModelRole.AGENTIC) target?.let {
                    Text(uiText(toolProtocolLabel(it.toolProtocol)), style = MaterialTheme.typography.bodyMedium)
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
                TextButton(onClick = { onExplore(role) }) { Text(uiText("Explore local candidates")) }
                }
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


internal fun routingPolicyLabel(policy: AiRoutingPolicy): String = when (policy) {
    AiRoutingPolicy.EXPLICIT -> "Use my selected provider"
    AiRoutingPolicy.AUTOMATIC -> "Choose a provider automatically"
    AiRoutingPolicy.LOCAL_ONLY -> "Use local providers only"
    AiRoutingPolicy.PREFERRED_WITH_FALLBACK -> "Try my preferred provider, then alternatives"
}
