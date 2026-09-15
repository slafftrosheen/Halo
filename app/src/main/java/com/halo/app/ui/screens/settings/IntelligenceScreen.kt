package com.halo.app.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.halo.app.ui.glass.components.HaloGlassButton
import com.halo.app.ui.glass.components.HaloGlassCard
import com.halo.app.ui.glass.components.HaloGlassDivider
import com.halo.app.ui.glass.components.HaloGlassLazyPage
import com.halo.app.ui.glass.components.HaloGlassSection
import com.halo.app.ui.glass.components.HaloGlassSettingRow
import com.halo.app.ui.glass.components.HaloGlassSwitch
import com.halo.app.ui.glass.material.HaloGlassColors
import com.halo.app.ui.glass.material.HaloTypography

@Composable
fun IntelligenceScreen(
    viewModel: IntelligenceViewModel,
    onBack: () -> Unit = {},
    onChangeModel: (String) -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()

    HaloGlassLazyPage(title = "Intelligence") {
        item {
            HaloGlassCard(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(4.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(uiState.activeProviderName, style = HaloTypography.titleMedium, color = HaloGlassColors.TextPrimary)
                    Text(uiState.activeModelId?.ifBlank { null } ?: "No model selected", style = HaloTypography.bodyMedium, color = HaloGlassColors.TextSecondary)
                }
            }
        }

        item {
            HaloGlassSection(title = "PROVIDER") {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    uiState.providers.forEachIndexed { index, provider ->
                        HaloGlassSettingRow(
                            title = provider.name,
                            trailingText = if (provider.isSelected) "Selected" else null,
                            onClick = { viewModel.selectProvider(provider.id) }
                        )
                        if (index < uiState.providers.lastIndex) {
                            HaloGlassDivider()
                        }
                    }
                }
            }
        }

        item {
            HaloGlassSection(
                title = "MODELS",
                trailing = {
                    Text(
                        text = if (uiState.isRefreshingModels) "Refreshing" else "${uiState.availableModels.size}",
                        style = HaloTypography.labelSmall,
                        color = HaloGlassColors.TextMuted
                    )
                }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                ) {
                    if (uiState.availableModels.isEmpty()) {
                        Text(
                            text = if (uiState.isRefreshingModels) {
                                "Loading models from ${uiState.activeProviderName}..."
                            } else {
                                "No models advertised by ${uiState.activeProviderName}."
                            },
                            style = HaloTypography.bodySmall,
                            color = HaloGlassColors.TextSecondary,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    } else {
                        uiState.availableModels.forEachIndexed { index, model ->
                            HaloGlassSettingRow(
                                title = model.displayName,
                                value = model.contextLength?.let { "${it / 1000}k context" },
                                trailingText = if (model.id == uiState.activeModelId) "Active" else null,
                                onClick = {
                                    onChangeModel(model.id)
                                    viewModel.selectModel(model.id)
                                }
                            )
                            if (index < uiState.availableModels.lastIndex) {
                                HaloGlassDivider()
                            }
                        }
                    }

                    Spacer(Modifier.height(8.dp))
                    HaloGlassButton(
                        text = if (uiState.isRefreshingModels) "Refreshing…" else "Refresh Models",
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        onClick = viewModel::refreshModels
                    )
                }
            }
        }

        item {
            HaloGlassSection(title = "RUNTIME POLICY") {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    PolicySwitchRow("Memory", uiState.isMemoryEnabled, viewModel::setMemoryEnabled)
                    PolicySwitchRow("Tools", uiState.isToolsEnabled, viewModel::setToolsEnabled)
                    PolicySwitchRow("Agent mode", uiState.isAgentEnabled, viewModel::setAgentEnabled)
                }
            }
        }

        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun PolicySwitchRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, modifier = Modifier.weight(1f), style = HaloTypography.bodyMedium, color = HaloGlassColors.TextPrimary)
        HaloGlassSwitch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
