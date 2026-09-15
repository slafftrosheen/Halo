package com.halo.app.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.GetValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.state.asStateFlow
import androidx.lifecycle.viewModelScope
import com.halo.app.ui.glass.components.HaloGlassButton
import com.halo.app.ui.glass.components.HaloGlassCard
import com.halo.app.ui.glass.components.HaloGlassPage
import com.halo.app.ui.glass.components.HaloGlassSection
import com.halo.app.ui.glass.components.HaloGlassSettingRow
import com.halo.app.ui.glass.components.HaloGlassMetric
import com.halo.app.ui.glass.material.HaloGlassColors
import com.halo.app.ui.glass.material.HaloTypography
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

@Composable
fun IntelligenceScreen(
    viewModel: IntelligenceViewModel,
    onBack: () -> Unit = {},
    onChangeModel: (String) -> Unit = { }
) {
    val uiState by viewModel.uiState.collectAsState()

    HaloGlassPage(
        title = "AI Model",
        onBack = onBack
    ) {
        // Active model hero card
        item {
            HaloGlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = uiState.activeProviderName,
                        style = HaloTypography.titleMedium,
                        color = HaloGlassColors.TextPrimary
                    )

                    Spacer(Modifier.width(8.dp))

                    // Model info pill
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .background(
                                if (uiState.activeModelId != null) {
                                    HaloGlassColors.Success
                                } else {
                                    HaloGlassColors.Warning
                                }
                            ) {
                            Text(
                                text = uiState.activeModelId?.takeIf { it.isNotBlank() }
                                    ?.split("/")
                                    .takeLast()
                                    ?.uppercaseFirst()
                                    ?: "None",
                                style = HaloTypography.bodySmall,
                                color = HaloGlassColors.OnSurface
                            )
                        },
                        contentAlignment = Alignment.Center
                    )

                    Spacer(Modifier.width(8.dp))

                    // Stats row: latency / context / cost
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp),
                        verticalArrangement = Arrangement.Center
                    ) {
                        _statItem(
                            icon = "⚡",
                            label = "Latency",
                            value = "${if (uiState.temperature > 0.5f) "High" else "Low"}",
                            color = HaloGlassColors.TextMuted
                        )
                        Spacer(Modifier.width(16.dp))
                        _statItem(
                            icon = "📄",
                            label = "Context",
                            value = "${uiState.llamacppContext ?: 4096}k",
                            color = HaloGlassColors.TextMuted
                        )
                        Spacer(Modifier.width(16.dp))
                        _statItem(
                            icon = "$",
                            label = "Cost",
                            value = "${if (uiState.policy.includeAvailableTools) "Tools" else "Basic"}",
                            color = HaloGlassColors.TextMuted
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(24.dp))

        // Performance section
        item {
            HaloGlassSection(title = "PERFORMANCE") {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(16.dp)
                ) {
                    _performanceMetric(
                        label = "Tokens/s",
                        value = uiState.activeProviderStatus.name,
                        icon = "📊"
                    )
                    _performanceMetric(
                        label = "Tokens total",
                        value = uiState.availableModels.size.toString(),
                        icon = "📈"
                    )
                    _performanceMetric(
                        label = "Queue depth",
                        value = "${if (uiState.isRefreshingModels) "Refreshing" else "Idle"}",
                        icon = "📨"
                    )
                }
            }
        }

        Spacer(Modifier.height(24.dp))

        // Available models list
        item {
            HaloGlassSection(title = "AVAILABLE MODELS") {
                if (uiState.availableModels.isEmpty()) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(16.dp)
                    ) {
                        Text(
                            text = "No models available",
                            style = HaloTypography.bodySmall,
                            color = HaloGlassColors.TextSecondary
                        )
                    }
                } else {
                    // Scrollable list with active-state highlighting
                    var scrollState = rememberScrollState()
                    LazyColumn(
                        state = scrollState,
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(uiState.availableModels.size) { index ->
                            val model = uiState.availableModels[index]

                            HaloGlassSettingRow(
                                text = model.displayName,
                                trailingIcon = if (model.id == uiState.activeModelId) {
                                    Some("✓")
                                } else {
                                    null
                                },
                                onClick = {
                                    onChangeModel(model.id)
                                    viewModel.selectModel(model.id)
                                }
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(24.dp))

        // Routing toggles
        item {
            HaloGlassSection(title = "ROUTING") {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(16.dp)
                ) {
                    _routingToggle(
                        label = "Auto-fallback",
                        isOn = uiState.policy.includeAvailableTools,
                        onToggle = { viewModel.setToolsEnabled(it) }
                    )
                    _routingToggle(
                        label = "Prefer on-device",
                        isOn = uiState.isMemoryEnabled,
                        onToggle = { viewModel.setMemoryEnabled(it) }
                    )
                }
            }
        }

        Spacer(Modifier.height(24.dp))

        // Change model button
        item {
            HaloGlassButton(
                text = "Change Model",
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                onClick = {
                    onChangeModel(uiState.activeModelId ?: "")
                    // Open model picker dialog
                    viewModel.navigateTo("ai_settings")
                }
            )
        }

        Spacer(Modifier.height(24.dp))
    }
}

private fun _statItem(icon: String, label: String, value: String, color: androidx.compose.ui.graphics.Color) {
    Column(
        modifier = Modifier
            .padding(bottom = 8.dp)
            .width(72.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Center
    ) {
        Text(text = icon, style = HaloTypography.bodyLarge)
        Spacer(Modifier.height(4.dp))
        Text(text = label, style = HaloTypography.labelSmall, color = color)
        Spacer(Modifier.height(2.dp))
        Text(text = value, style = HaloTypography.bodyMedium)
    }
}

private fun _performanceMetric(label: String, value: String, icon: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text(text = icon, style = HaloTypography.bodyLarge, margin = Modifier(4.dp))
        Spacer(Modifier.width(8.dp))
        Column(
            modifier = Modifier.width(0.dp, balance = true),
            verticalArrangement = Arrangement.Center
        ) {
            Text(text = label, style = HaloTypography.labelSmall, color = HaloGlassColors.TextMuted)
            Spacer(Modifier.height(2.dp))
            Text(text = value, style = HaloTypography.bodyMedium, color = HaloGlassColors.TextPrimary)
        }
        Spacer(Modifier.width(8.dp))
        Text(text = icon, style = HaloTypography.bodyLarge, margin = Modifier(4.dp))
    }
}

private fun _routingToggle(
    label: String,
    isOn: Boolean,
    onToggle: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = HaloTypography.labelSmall,
            color = HaloGlassColors.TextPrimary
        )
        Spacer(Modifier.width(40.dp))
        androidx.compose.material3.Switch(
            checked = isOn,
            onCheckedChange = onToggle,
            colors = androidx.compose.ui.graphics.ColorSet(
                defaultColor = HaloGlassColors.Accent,
                trackColor = HaloGlassColors.Surface
            )
        )
    }
}