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
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.halo.app.ui.theme.HaloPastelBlue
import com.halo.app.ui.theme.HaloPastelMint
import com.halo.app.ui.theme.HaloWarning
import com.halo.app.ui.workspace.HaloWorkspaceButton
import com.halo.app.ui.workspace.HaloWorkspaceCard
import com.halo.app.ui.workspace.HaloWorkspaceHeader
import com.halo.app.ui.workspace.HaloWorkspaceRow
import com.halo.app.ui.workspace.HaloWorkspaceSection
import com.halo.providers.litert.models.LocalModelDescriptor
import com.halo.providers.litert.models.LocalModelValidation
import com.halo.sdk.provider.AiCapability
import com.halo.sdk.provider.AiModel
import com.halo.sdk.provider.AiExecutionLocation
import com.halo.sdk.provider.AiModelCatalogEntry
import com.halo.sdk.provider.AiModelKind
import com.halo.sdk.provider.ModelCatalogueSource
import com.halo.sdk.provider.ProviderStatus

@Composable
fun IntelligenceScreen(
    viewModel: IntelligenceViewModel,
    onBack: () -> Unit,
    onBrowseModels: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    var openRouterKeyDraft by rememberSaveable(uiState.activeProviderId) {
        mutableStateOf(uiState.openRouterKey)
    }
    var ollamaEndpointDraft by rememberSaveable(uiState.activeProviderId) {
        mutableStateOf(uiState.ollamaEndpoint)
    }
    var modelSearch by rememberSaveable { mutableStateOf("") }
    var modelFilter by rememberSaveable { mutableStateOf(ModelHubFilter.ALL) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 14.dp)
    ) {
        HaloWorkspaceHeader(
            title = "Intelligence",
            subtitle = "Choose a model; Halo handles where and how it runs.",
            onBack = onBack
        )

        Spacer(Modifier.height(20.dp))

        val providerState = uiState.activeProviderStatus.state
        val providerAccent = when (providerState) {
            ProviderStatus.State.READY -> HaloPastelMint
            ProviderStatus.State.LOADING,
            ProviderStatus.State.STANDBY -> HaloPastelBlue
            else -> HaloWarning
        }

        HaloWorkspaceCard(accent = providerAccent) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = uiState.catalogEntries.firstOrNull {
                            it.runtime.ref.providerId == uiState.activeProviderId &&
                                it.runtime.ref.modelId == uiState.activeModelId
                        }?.model?.displayName ?: uiState.activeModelId ?: "No model selected",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = activeTargetSummary(uiState),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = providerState.name.replace("_", " "),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = when (providerState) {
                        ProviderStatus.State.READY -> MaterialTheme.colorScheme.primary
                        ProviderStatus.State.ERROR,
                        ProviderStatus.State.UNAVAILABLE -> MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
            uiState.activeProviderStatus.message?.takeIf { it.isNotBlank() }?.let { message ->
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            HaloWorkspaceButton(
                text = "Check connection",
                onClick = viewModel::testConnection,
                enabled = uiState.activeProviderId.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
                primary = providerState != ProviderStatus.State.READY
            )
        }

        Spacer(Modifier.height(20.dp))

        HaloWorkspaceSection(
            title = "Inference activity",
            trailingText = "${uiState.inferenceRequests} requests",
        ) {
            HaloWorkspaceRow(
                title = "Tokens",
                subtitle = "Input ${uiState.inferenceInputTokens} · output ${uiState.inferenceOutputTokens}",
                trailingText = uiState.inferenceAverageDurationMs?.let { "${it} ms avg" },
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            HaloWorkspaceRow(
                title = "Outcomes",
                subtitle = "${uiState.inferenceFailures} failures · ${uiState.routerResolutions} router resolutions",
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            HaloWorkspaceRow(
                title = "Tool outcomes",
                subtitle = "${uiState.toolFailures} failures · ${uiState.toolUnknownOutcomes} completion unknown",
                trailingText = uiState.toolAverageDurationMs?.let { "${it} ms avg" }
                    ?: "${uiState.toolOutcomes} total",
            )
        }

        Spacer(Modifier.height(20.dp))

        val inferenceProviders = uiState.providers
        HaloWorkspaceSection(title = "Providers & access") {
            inferenceProviders.forEachIndexed { index, provider ->
                HaloWorkspaceRow(
                    title = provider.name,
                    subtitle = providerSetupSummary(provider.id),
                    trailingText = if (provider.isSelected) "Selected" else null,
                    selected = provider.isSelected,
                    onClick = { viewModel.selectProvider(provider.id) }
                )
                if (index < inferenceProviders.lastIndex) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }

        when (uiState.activeProviderId) {
            ProviderIds.OPENROUTER -> {
                Spacer(Modifier.height(20.dp))
                HaloWorkspaceSection(title = "Configuration") {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedTextField(
                            value = openRouterKeyDraft,
                            onValueChange = { openRouterKeyDraft = it },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            label = { Text("OpenRouter API key") },
                            visualTransformation = PasswordVisualTransformation()
                        )
                        Text(
                            text = "Stored locally on this Android host and used only for OpenRouter requests.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        HaloWorkspaceButton(
                            text = "Save and refresh",
                            onClick = { viewModel.setOpenRouterKey(openRouterKeyDraft) },
                            enabled = openRouterKeyDraft.trim() != uiState.openRouterKey.trim(),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            ProviderIds.OLLAMA -> {
                Spacer(Modifier.height(20.dp))
                HaloWorkspaceSection(title = "Configuration") {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedTextField(
                            value = ollamaEndpointDraft,
                            onValueChange = { ollamaEndpointDraft = it },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            label = { Text("Ollama endpoint") },
                            placeholder = { Text("http://192.168.1.10:11434") }
                        )
                        Text(
                            text = "Optional local-network server. On-device LiteRT is Halo's default and does not require Ollama. Use the reachable HTTP endpoint for the Ollama host.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        HaloWorkspaceButton(
                            text = "Save and refresh",
                            onClick = { viewModel.setOllamaEndpoint(ollamaEndpointDraft) },
                            enabled = ollamaEndpointDraft.trim().trimEnd('/') != uiState.ollamaEndpoint.trim().trimEnd('/'),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        HaloWorkspaceSection(
            title = "Models",
            trailingText = if (uiState.isRefreshingModels) "Refreshing" else "${uiState.catalogEntries.size}"
        ) {
            OutlinedTextField(
                value = modelSearch,
                onValueChange = { modelSearch = it },
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                singleLine = true,
                label = { Text("Search local, LAN and cloud models") }
            )
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ModelHubFilter.entries.forEach { filter ->
                    FilterChip(
                        selected = modelFilter == filter,
                        onClick = { modelFilter = filter },
                        label = { Text(filter.label) }
                    )
                }
            }
            val visibleModels = uiState.catalogEntries.filter { entry ->
                entry.matches(modelSearch, modelFilter)
            }
            if (visibleModels.isEmpty()) {
                Text(
                    text = if (uiState.isRefreshingModels) {
                        "Loading model catalog…"
                    } else {
                        "No models match this search and filter."
                    },
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                visibleModels.forEachIndexed { index, entry ->
                    val model = entry.model
                    val active = entry.runtime.ref.providerId == uiState.activeProviderId && model.id == uiState.activeModelId
                    HaloWorkspaceRow(
                        title = model.displayName,
                        subtitle = modelEntrySummary(entry),
                        trailingText = if (active) "Active" else if (entry.source == ModelCatalogueSource.REMOTE_CACHED) "Cached" else null,
                        selected = active,
                        onClick = { viewModel.activateCatalogEntry(entry) }
                    )
                    if (index < visibleModels.lastIndex) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            }

            uiState.catalogErrors.values.firstOrNull()?.let { error ->
                Text(
                    text = "Some providers could not refresh: $error",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            HaloWorkspaceButton(
                text = if (uiState.isRefreshingModels) "Refreshing…" else "Refresh models",
                onClick = viewModel::refreshModels,
                enabled = !uiState.isRefreshingModels,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                primary = false
            )
        }

        if (uiState.activeProviderId == ProviderIds.LITERT || uiState.localModels.isNotEmpty()) {
            Spacer(Modifier.height(20.dp))

            Text(
                text = "On-device model",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 2.dp, vertical = 8.dp)
            )

            HaloWorkspaceButton(
                text = "Browse models for this phone",
                onClick = onBrowseModels,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                uiState.localModels.forEach { model ->
                    val isActive = model.verified && model.id == uiState.activeModelId

                    HaloWorkspaceCard(accent = if (isActive) HaloPastelMint else null) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = model.displayName,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "%.1f GB".format(model.bytes / 1_000_000_000.0),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            text = when {
                                model.validation == LocalModelValidation.VERIFIED_ARTIFACT ->
                                    "Verified artifact · ${model.contextWindowTokens} context"
                                model.validation == LocalModelValidation.UNVERIFIED ->
                                    "Imported · unverified · model default context"
                                else -> "${model.validation} — re-download or delete"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = if (model.valid) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error
                        )
                        model.validationDetail?.takeIf { !model.valid }?.let { detail ->
                            Text(
                                text = detail,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                        Spacer(Modifier.height(4.dp))

                        HaloWorkspaceButton(
                            text = when {
                                isActive -> "Active"
                                model.valid -> "Select"
                                else -> "Damaged — re-download"
                            },
                            onClick = { if (!isActive && model.valid) viewModel.selectLocalModel(model.id) },
                            enabled = !isActive && model.valid,
                            modifier = Modifier.fillMaxWidth(),
                            primary = !isActive && model.valid
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        HaloWorkspaceSection(title = "Runtime policy") {
            PolicySwitchRow(
                "Tools",
                uiState.policy.includeAvailableTools,
                viewModel::setToolsEnabled
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            PolicySwitchRow(
                "Visual context",
                uiState.policy.includeImages,
                viewModel::setImagesEnabled
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            PolicySwitchRow(
                "Workspace context",
                uiState.policy.includeWorkspace,
                viewModel::setWorkspaceEnabled
            )
        }

        Spacer(Modifier.height(20.dp))

        HaloWorkspaceSection(title = "Remote context sharing") {
            Text(
                text = "These switches only permit required context to leave this device when a remote inference provider is selected. They never enable cloud routing on their own.",
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            PolicySwitchRow(
                "Workspace references",
                uiState.policy.allowCloudWorkspace,
                viewModel::setCloudWorkspaceEnabled
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            PolicySwitchRow(
                "Current screen",
                uiState.policy.allowCloudScreenContext,
                viewModel::setCloudScreenEnabled
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            PolicySwitchRow(
                "Images",
                uiState.policy.allowCloudImages,
                viewModel::setCloudImagesEnabled
            )
        }

        Spacer(Modifier.height(32.dp))
    }
}

private fun providerSetupSummary(providerId: String): String = when (providerId) {
    ProviderIds.LITERT -> "Default · on-device LiteRT · no server or API key"
    ProviderIds.OLLAMA -> "Optional local-network server · endpoint required"
    ProviderIds.OPENROUTER -> "Optional cloud inference · API key required"
    else -> "Optional inference provider"
}

@Composable
private fun PolicySwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}


private fun modelCapabilitySummary(model: AiModel): String {
    val badges = buildList {
        if (AiCapability.TEXT in model.inputCapabilities) add("Text")
        if (AiCapability.IMAGE in model.inputCapabilities) add("Vision")
        if (AiCapability.AUDIO in model.inputCapabilities) add("Audio")
        if (AiCapability.VIDEO in model.inputCapabilities) add("Video")
        if (model.supportsTools) add("Tools")
        if (model.supportsStreaming) add("Streaming")
        model.contextLength?.let { length ->
            add(if (length >= 1_000L) "${length / 1_000}k context" else "$length context")
        }
    }
    return badges.joinToString(" · ").ifBlank { "Capabilities not advertised" }
}

private enum class ModelHubFilter(val label: String) {
    ALL("All"), ON_DEVICE("On device"), FREE("Free"), VISION("Vision"), TOOLS("Tools"), LARGE_CONTEXT("Large context")
}

private fun AiModelCatalogEntry.matches(query: String, filter: ModelHubFilter): Boolean {
    val normalized = query.trim()
    if (normalized.isNotEmpty() &&
        !model.displayName.contains(normalized, ignoreCase = true) &&
        !model.id.contains(normalized, ignoreCase = true)
    ) return false
    return when (filter) {
        ModelHubFilter.ALL -> true
        ModelHubFilter.ON_DEVICE -> runtime.location == AiExecutionLocation.ON_DEVICE
        ModelHubFilter.FREE -> model.isFree
        ModelHubFilter.VISION -> AiCapability.IMAGE in model.inputCapabilities
        ModelHubFilter.TOOLS -> model.supportsTools
        ModelHubFilter.LARGE_CONTEXT -> (model.contextLength ?: 0L) >= 100_000L
    }
}

private fun modelEntrySummary(entry: AiModelCatalogEntry): String = buildList {
    add(when (entry.runtime.location) {
        AiExecutionLocation.ON_DEVICE -> "On device"
        AiExecutionLocation.LOCAL_NETWORK -> "Local network"
        AiExecutionLocation.REMOTE -> "Cloud · OpenRouter"
    })
    if (entry.kind == AiModelKind.ROUTER) add("Dynamic router")
    if (entry.source == ModelCatalogueSource.LOCAL_DOWNLOADABLE) add("Download")
    if (entry.runtime.installed == true) add("Installed")
    if (entry.model.isFree) add("Free")
    if (entry.model.supportsTools) add("Tools")
    if (AiCapability.IMAGE in entry.model.inputCapabilities) add("Vision")
    entry.model.contextLength?.let { add(if (it >= 1_000) "${it / 1_000}k context" else "$it context") }
}.joinToString(" · ")

private fun activeTargetSummary(state: IntelligenceUiState): String {
    val entry = state.catalogEntries.firstOrNull {
        it.runtime.ref.providerId == state.activeProviderId && it.runtime.ref.modelId == state.activeModelId
    }
    val base = entry?.let(::modelEntrySummary) ?: "${state.activeProviderName} · target unavailable"
    val resolved = state.lastResolvedModelId
        ?.takeIf { entry?.kind == AiModelKind.ROUTER && it != state.activeModelId }
    return if (resolved == null) base else "$base\nResolved last turn · $resolved"
}
