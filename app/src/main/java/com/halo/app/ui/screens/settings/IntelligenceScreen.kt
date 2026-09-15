package com.halo.app.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.halo.app.ui.glass.components.HaloGlassButton
import com.halo.app.ui.glass.components.HaloGlassCard
import com.halo.app.ui.glass.components.HaloGlassPage
import com.halo.app.ui.glass.components.HaloGlassSection
import com.halo.app.ui.glass.components.HaloGlassSettingRow
import com.halo.app.ui.glass.material.HaloGlassColors
import com.halo.app.ui.glass.material.HaloTypography

@Composable
fun IntelligenceScreen(
    viewModel: IntelligenceViewModel,
    onBack: () -> Unit = {},
    onChangeModel: (String) -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()

    HaloGlassPage(title = "AI Model", onBack = onBack) {
        item {
            HaloGlassCard(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(4.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(uiState.activeProviderName, style = HaloTypography.titleMedium, color = HaloGlassColors.TextPrimary)
                    Text(uiState.activeModelId?.ifBlank { null } ?: "No model selected", style = HaloTypography.bodyMedium, color = HaloGlassColors.TextSecondary)
                    Text("Status: ${uiState.activeProviderStatus.name}", style = HaloTypography.labelSmall, color = HaloGlassColors.TextMuted)
                }
            }
        }

        item {
            HaloGlassSection(title = "PROVIDER") {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    uiState.providers.forEach { provider ->
                        HaloGlassSettingRow(
                            title = provider.displayName,
                            trailingText = if (provider.isSelected) "Selected" else provider.status.name,
                            onClick = { viewModel.selectProvider(provider.id) }
                        )
                        Spacer(Modifier.height(6.dp))
                    }
                }
            }
        }

        item {
            HaloGlassSection(title = "MODEL") {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    if (uiState.availableModels.isEmpty()) {
                        Text(
                            if (uiState.isRefreshingModels) "Loading models…" else "No models reported by this provider.",
                            style = HaloTypography.bodySmall,
                            color = HaloGlassColors.TextSecondary
                        )
                    } else {
                        uiState.availableModels.forEach { model ->
                            HaloGlassSettingRow(
                                title = model.displayName,
                                trailingText = if (model.id == uiState.activeModelId) "Selected" else null,
                                onClick = {
                                    onChangeModel(model.id)
                                    viewModel.selectModel(model.id)
                                }
                            )
                            Spacer(Modifier.height(6.dp))
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    HaloGlassButton(
                        text = if (uiState.isRefreshingModels) "Refreshing…" else "Refresh Models",
                        modifier = Modifier.fillMaxWidth(),
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

        item {
            HaloGlassSection(title = "LOCAL MODEL") {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    Text(uiState.activeLocalModelId ?: "No local model selected", style = HaloTypography.bodyMedium, color = HaloGlassColors.TextPrimary)
                    Spacer(Modifier.height(4.dp))
                    Text("Context: ${uiState.llamacppContext}", style = HaloTypography.labelSmall, color = HaloGlassColors.TextMuted)
                }
            }
        }

        item {
            HaloGlassButton(
                text = "Test Connection",
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                onClick = viewModel::testConnection
            )
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
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = HaloGlassColors.OnSurface,
                checkedTrackColor = HaloGlassColors.Accent,
                uncheckedThumbColor = HaloGlassColors.TextSecondary,
                uncheckedTrackColor = HaloGlassColors.Surface
            )
        )
    }
}
