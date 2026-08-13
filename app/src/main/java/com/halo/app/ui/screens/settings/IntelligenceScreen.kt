package com.halo.app.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.halo.app.ui.glass.components.*
import com.halo.app.ui.glass.material.HaloGlassColors
import com.halo.app.ui.glass.material.HaloTypography
import com.halo.sdk.provider.*

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items

/**
 * Unified Intelligence Hub for Halo.
 * Manages active provider, model selection, and cognitive capabilities.
 */
@Composable
fun IntelligenceScreen(viewModel: IntelligenceViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    var showProviderPicker by remember { mutableStateOf(false) }
    var showModelPicker by remember { mutableStateOf(false) }
    var advancedExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(HaloGlassColors.Background)
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
            .padding(bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        Text("Intelligence", style = HaloTypography.displayMedium, color = HaloGlassColors.TextPrimary)

        // 1. ACTIVE PROVIDER
        HaloGlassSection(title = "ACTIVE PROVIDER") {
            Column(Modifier.padding(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(uiState.activeProviderName, style = HaloTypography.bodyLarge, color = HaloGlassColors.TextPrimary)
                        Text(
                            text = if (uiState.activeModelId != null) uiState.activeModelId!! else "Default Model",
                            style = HaloTypography.labelMedium,
                            color = HaloGlassColors.TextSecondary
                        )
                    }
                    HaloGlassChip(
                        text = uiState.activeProviderStatus.state.name,
                        material = when(uiState.activeProviderStatus.state) {
                            ProviderStatus.State.READY -> com.halo.app.ui.glass.material.HaloGlassMaterials.Interactive.copy(
                                containerColor = HaloGlassColors.Success.copy(alpha = 0.2f),
                                contentColor = HaloGlassColors.Success
                            )
                            else -> com.halo.app.ui.glass.material.HaloGlassMaterials.Interactive.copy(
                                containerColor = HaloGlassColors.Warning.copy(alpha = 0.2f),
                                contentColor = HaloGlassColors.Warning
                            )
                        }
                    )
                }
            }
        }

        // 2. CAPABILITIES
        HaloGlassSection(title = "CAPABILITIES") {
            Column(Modifier.padding(8.dp)) {
                CapabilityRow("Text", uiState.capabilities.contains(AiCapability.TEXT))
                HaloGlassDivider()
                CapabilityRow("Vision", uiState.capabilities.contains(AiCapability.IMAGE))
                HaloGlassDivider()
                CapabilityRow("Audio", uiState.capabilities.contains(AiCapability.AUDIO))
                HaloGlassDivider()
                CapabilityRow("Tools", uiState.capabilities.contains(AiCapability.TOOLS))
            }
        }

        // 3. MODEL SELECTION
        HaloGlassSection(title = "MODEL") {
            Column(Modifier.padding(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        uiState.activeModelId ?: "Default",
                        style = HaloTypography.bodyMedium,
                        color = HaloGlassColors.TextPrimary
                    )
                    HaloGlassButton(
                        text = "Change",
                        onClick = { showModelPicker = true }
                    )
                }
            }
        }

        // 4. PROVIDER SELECTION
        HaloGlassSection(title = "PROVIDER") {
            Column(Modifier.padding(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            uiState.activeProviderName,
                            style = HaloTypography.bodyMedium,
                            color = HaloGlassColors.TextPrimary
                        )
                        Text(
                            text = when(uiState.activeProviderId) {
                                "android.system.assistant" -> "Zero configuration"
                                "openrouter" -> "Cloud reasoning"
                                else -> "Local inference"
                            },
                            style = HaloTypography.labelSmall,
                            color = HaloGlassColors.TextMuted
                        )
                    }
                    HaloGlassButton(
                        text = "Switch",
                        onClick = { showProviderPicker = true }
                    )
                }

                if (uiState.activeProviderId == "openrouter") {
                    Spacer(Modifier.height(16.dp))
                    Text("API KEY", style = HaloTypography.labelSmall, color = HaloGlassColors.TextMuted)
                    OutlinedTextField(
                        value = uiState.openRouterKey,
                        onValueChange = { viewModel.setOpenRouterKey(it) },
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        textStyle = HaloTypography.bodySmall,
                        placeholder = { Text("sk-or-...") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = HaloGlassColors.Accent,
                            unfocusedBorderColor = HaloGlassColors.Glass
                        )
                    )
                } else if (uiState.activeProviderId == "provider.ollama") {
                    Spacer(Modifier.height(16.dp))
                    Text("ENDPOINT", style = HaloTypography.labelSmall, color = HaloGlassColors.TextMuted)
                    OutlinedTextField(
                        value = uiState.ollamaEndpoint,
                        onValueChange = { viewModel.setOllamaEndpoint(it) },
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        textStyle = HaloTypography.bodySmall,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = HaloGlassColors.Accent,
                            unfocusedBorderColor = HaloGlassColors.Glass
                        )
                    )
                }
            }
        }

        // 5. ADVANCED
        HaloGlassCard {
            Column(Modifier.padding(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { advancedExpanded = !advancedExpanded },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Advanced Settings", style = HaloTypography.bodyMedium, color = HaloGlassColors.TextPrimary)
                    Text(if (advancedExpanded) "−" else "+", style = HaloTypography.bodyLarge, color = HaloGlassColors.TextMuted)
                }
                
                if (advancedExpanded) {
                    Spacer(Modifier.height(16.dp))
                    AdvancedToggle("Memory", "Long-term cognitive context", uiState.isMemoryEnabled) {
                        viewModel.setMemoryEnabled(it)
                    }
                    HaloGlassDivider()
                    AdvancedToggle("Tools", "Model-requested actions", uiState.isToolsEnabled) {
                        viewModel.setToolsEnabled(it)
                    }
                    HaloGlassDivider()
                    
                    Spacer(Modifier.height(16.dp))
                    Text("Temperature: ${String.format(java.util.Locale.ROOT, "%.1f", uiState.temperature)}", style = HaloTypography.bodySmall, color = HaloGlassColors.TextSecondary)
                    Slider(
                        value = uiState.temperature,
                        onValueChange = { viewModel.setTemperature(it) },
                        valueRange = 0f..1.2f
                    )

                    Spacer(Modifier.height(24.dp))
                    Text("CONTEXT POLICY", style = HaloTypography.labelSmall, color = HaloGlassColors.TextMuted)
                    Spacer(Modifier.height(8.dp))
                    
                    AdvancedToggle("Conversation", "Include chat history", uiState.policy.includeConversation) {
                        viewModel.updatePolicy(conversation = it)
                    }
                    HaloGlassDivider()
                    AdvancedToggle("Recent Events", "Timeline context", uiState.policy.includeRecentEvents) {
                        viewModel.updatePolicy(recentEvents = it)
                    }
                    HaloGlassDivider()
                    AdvancedToggle("Images", "Camera snapshot context", uiState.policy.includeImages) {
                        viewModel.updatePolicy(images = it)
                    }
                }
            }
        }
    }

    if (showProviderPicker) {
        ProviderPickerDialog(
            providers = uiState.providers,
            onSelect = { viewModel.selectProvider(it); showProviderPicker = false },
            onDismiss = { showProviderPicker = false }
        )
    }

    if (showModelPicker) {
        ModelPickerDialog(
            models = uiState.availableModels,
            onSelect = { viewModel.selectModel(it); showModelPicker = false },
            onRefresh = { viewModel.refreshModels() },
            isRefreshing = uiState.isRefreshingModels,
            onDismiss = { showModelPicker = false }
        )
    }
}

@Composable
private fun CapabilityRow(name: String, supported: Boolean) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(name, color = HaloGlassColors.TextPrimary, style = HaloTypography.bodyMedium)
        if (supported) {
            Text("✓", color = HaloGlassColors.Success, style = HaloTypography.bodyLarge)
        } else {
            Text("—", color = HaloGlassColors.TextMuted, style = HaloTypography.bodyLarge)
        }
    }
}

@Composable
private fun AdvancedToggle(title: String, description: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = HaloTypography.bodyMedium, color = HaloGlassColors.TextPrimary)
            Text(description, style = HaloTypography.labelSmall, color = HaloGlassColors.TextMuted)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = HaloGlassColors.Accent
            )
        )
    }
}

@Composable
private fun ProviderPickerDialog(
    providers: List<AiProviderUiState>,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    HaloGlassDialog(
        onDismissRequest = onDismiss,
        content = {
            Column(Modifier.padding(16.dp)) {
                Text("Select Provider", style = HaloTypography.titleLarge, color = HaloGlassColors.TextPrimary)
                Spacer(Modifier.height(16.dp))
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 400.dp)) {
                    items(providers) { provider: AiProviderUiState ->
                        Row(
                            Modifier.fillMaxWidth().clickable { onSelect(provider.id) }.padding(vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(provider.name, color = HaloGlassColors.TextPrimary)
                            if (provider.isSelected) Text("●", color = HaloGlassColors.Accent)
                        }
                    }
                }
            }
        }
    )
}

@Composable
private fun ModelPickerDialog(
    models: List<AiModel>,
    onSelect: (String) -> Unit,
    onRefresh: () -> Unit,
    isRefreshing: Boolean,
    onDismiss: () -> Unit
) {
    HaloGlassDialog(
        onDismissRequest = onDismiss,
        content = {
            Column(Modifier.padding(16.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Select Model", style = HaloTypography.titleLarge, color = HaloGlassColors.TextPrimary)
                    HaloGlassButton(text = if (isRefreshing) "..." else "Refresh", onClick = onRefresh)
                }
                Spacer(Modifier.height(16.dp))
                if (models.isEmpty()) {
                    Text("No models found. Try refreshing.", color = HaloGlassColors.TextMuted)
                } else {
                    LazyColumn(Modifier.fillMaxWidth().heightIn(max = 400.dp)) {
                        items(models) { model: AiModel ->
                            Row(
                                Modifier.fillMaxWidth().clickable { onSelect(model.id) }.padding(vertical = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text(model.displayName, color = HaloGlassColors.TextPrimary)
                                    Text(model.id, style = HaloTypography.labelSmall, color = HaloGlassColors.TextMuted)
                                }
                                if (model.isFree) HaloGlassChip(text = "Free", material = com.halo.app.ui.glass.material.HaloGlassMaterials.Interactive.copy(containerColor = HaloGlassColors.Success.copy(alpha = 0.1f), contentColor = HaloGlassColors.Success))
                            }
                        }
                    }
                }
            }
        }
    )
}
