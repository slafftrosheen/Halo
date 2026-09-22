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
import androidx.compose.material3.HorizontalDivider
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
import com.halo.providers.llamacpp.DownloadState
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 14.dp)
    ) {
        HaloWorkspaceHeader(
            title = "Intelligence",
            subtitle = "Provider, model and runtime policy.",
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
                        text = uiState.activeProviderName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = uiState.activeModelId?.ifBlank { null } ?: "No model selected",
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

        HaloWorkspaceSection(title = "Provider") {
            uiState.providers.forEachIndexed { index, provider ->
                HaloWorkspaceRow(
                    title = provider.name,
                    trailingText = if (provider.isSelected) "Selected" else null,
                    selected = provider.isSelected,
                    onClick = { viewModel.selectProvider(provider.id) }
                )
                if (index < uiState.providers.lastIndex) {
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
                            text = "Use the reachable HTTP endpoint for the Ollama host on your local network.",
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
            trailingText = if (uiState.isRefreshingModels) "Refreshing" else "${uiState.availableModels.size}"
        ) {
            if (uiState.availableModels.isEmpty()) {
                Text(
                    text = if (uiState.isRefreshingModels) {
                        "Loading models from ${uiState.activeProviderName}…"
                    } else {
                        "No models advertised by ${uiState.activeProviderName}."
                    },
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                uiState.availableModels.forEachIndexed { index, model ->
                    HaloWorkspaceRow(
                        title = model.displayName,
                        subtitle = model.contextLength?.let { "${it / 1000}k context" },
                        trailingText = if (model.id == uiState.activeModelId) "Active" else null,
                        selected = model.id == uiState.activeModelId,
                        onClick = { viewModel.selectModel(model.id) }
                    )
                    if (index < uiState.availableModels.lastIndex) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
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

        if (uiState.activeProviderId == ProviderIds.LLAMACPP || uiState.localModels.isNotEmpty()) {
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
                uiState.localModels.filter {
                    uiState.downloadStates[it.id] == DownloadState.Downloaded
                }.forEach { model ->
                    val downloadState = uiState.downloadStates[model.id] ?: DownloadState.NotDownloaded
                    val isActive = model.id == uiState.activeLocalModelId

                    HaloWorkspaceCard(accent = if (isActive) HaloPastelMint else null) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = model.name,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "${model.sizeMb} MB",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            text = model.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(4.dp))

                        when (downloadState) {
                            DownloadState.Downloaded -> {
                                HaloWorkspaceButton(
                                    text = if (isActive) "Active" else "Select",
                                    onClick = { if (!isActive) viewModel.setActiveLocalModel(model.id) },
                                    enabled = !isActive,
                                    modifier = Modifier.fillMaxWidth(),
                                    primary = !isActive
                                )
                            }
                            DownloadState.Downloading -> {
                                Text(
                                    text = "Downloading…",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                            }
                            else -> {
                                HaloWorkspaceButton(
                                    text = "Download (${model.sizeMb} MB)",
                                    onClick = { viewModel.downloadModel(model.id) },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
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
        }

        Spacer(Modifier.height(32.dp))
    }
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
