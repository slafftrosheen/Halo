package com.halo.app.ui.screens.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.halo.app.ui.glass.components.*
import com.halo.app.ui.glass.material.HaloGlassColors
import com.halo.app.ui.glass.material.HaloGlassMaterials
import com.halo.app.ui.glass.material.HaloTypography
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import com.halo.sdk.provider.*
import kotlinx.coroutines.flow.collectLatest

@Composable
fun IntelligenceScreen(viewModel: IntelligenceViewModel, onBack: () -> Unit = {}) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var showProviderPicker by remember { mutableStateOf(false) }
    var showModelPicker by remember { mutableStateOf(false) }
    var advancedExpanded by remember { mutableStateOf(false) }
    var customUrl by remember { mutableStateOf("") }
    var customName by remember { mutableStateOf("") }

    LaunchedEffect(viewModel) {
        viewModel.testConnectionResult.collectLatest { success ->
            val message = if (success) "Connection test successful" else "Connection test failed"
            android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    val activeIsHandoff = uiState.providers.firstOrNull { it.id == uiState.activeProviderId }?.isHandoff == true
    val customModelReady = customUrl.trim().isNotBlank() && customName.trim().isNotBlank()

    HaloGlassPage(title = "") {
        HaloGlassBackHeader(title = "Intelligence", onBack = onBack)

        HaloGlassSection(title = "ACTIVE PROVIDER") {
            Column(Modifier.fillMaxWidth().padding(12.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(uiState.activeProviderName, style = HaloTypography.bodyLarge, color = HaloGlassColors.TextPrimary)
                        Spacer(Modifier.height(4.dp))
                        Text(if (activeIsHandoff) "System assistant handoff" else (uiState.activeModelId ?: "No model selected"), style = HaloTypography.labelMedium, color = HaloGlassColors.TextSecondary)
                    }
                    ProviderStatusChip(uiState.activeProviderStatus)
                }
                uiState.activeProviderStatus.message?.takeIf { it.isNotBlank() }?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, style = HaloTypography.labelSmall, color = statusColor(uiState.activeProviderStatus.state))
                }
            }
        }

        HaloGlassSection(title = "CAPABILITIES") {
            CapabilityRow("Text", uiState.capabilities.contains(AiCapability.TEXT))
            HaloGlassDivider()
            CapabilityRow("Vision", uiState.capabilities.contains(AiCapability.IMAGE))
            HaloGlassDivider()
            CapabilityRow("Audio", uiState.capabilities.contains(AiCapability.AUDIO))
            HaloGlassDivider()
            CapabilityRow("Tools", uiState.capabilities.contains(AiCapability.TOOLS))
        }

        HaloGlassSection(title = "MODEL") {
            Row(Modifier.fillMaxWidth().padding(8.dp).defaultMinSize(minHeight = 52.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(if (activeIsHandoff) "No model required" else (uiState.activeModelId ?: "No model selected"), style = HaloTypography.bodyMedium, color = HaloGlassColors.TextPrimary)
                    if (activeIsHandoff) Text("Halo hands this request to the Android system assistant.", style = HaloTypography.labelSmall, color = HaloGlassColors.TextMuted)
                    else if (uiState.isRefreshingModels) Text("Refreshing models…", style = HaloTypography.labelSmall, color = HaloGlassColors.TextMuted)
                    else if (uiState.availableModels.isEmpty()) Text("No models currently available", style = HaloTypography.labelSmall, color = HaloGlassColors.Warning)
                }
                if (!activeIsHandoff) HaloGlassButton(text = "Change", onClick = { showModelPicker = true })
            }
        }

        HaloGlassSection(title = "PROVIDER") {
            Column(Modifier.padding(8.dp)) {
                Row(Modifier.fillMaxWidth().defaultMinSize(minHeight = 52.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(uiState.activeProviderName, style = HaloTypography.bodyMedium, color = HaloGlassColors.TextPrimary)
                        Text(providerDescription(uiState.activeProviderId), style = HaloTypography.labelSmall, color = HaloGlassColors.TextMuted)
                    }
                    HaloGlassButton(text = "Switch", onClick = { showProviderPicker = true })
                }
                Spacer(Modifier.height(12.dp))
                if (activeIsHandoff) {
                    HaloGlassButton(text = "Open Assistant", onClick = { viewModel.handoffToAssistant() }, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(8.dp))
                    Text(uiState.assistantPackage?.let { "Detected assistant: $it" } ?: "No Android assistant detected", style = HaloTypography.labelSmall, color = if (uiState.assistantPackage != null) HaloGlassColors.TextSecondary else HaloGlassColors.Warning)
                    Spacer(Modifier.height(8.dp))
                    HaloGlassButton(text = "Test Availability", onClick = viewModel::testConnection, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(8.dp))
                    Text("Halo may supply a best-effort prompt extra, but Android does not guarantee prompt injection or a response channel for third-party assistants.", style = HaloTypography.labelSmall, color = HaloGlassColors.TextSecondary)
                } else {
                    HaloGlassButton(text = "Test Connection", onClick = viewModel::testConnection, modifier = Modifier.fillMaxWidth())
                }
                when (uiState.activeProviderId) {
                    "openrouter" -> {
                        Spacer(Modifier.height(12.dp))
                        Text("API KEY", style = HaloTypography.labelSmall, color = HaloGlassColors.TextMuted)
                        OutlinedTextField(value = uiState.openRouterKey, onValueChange = viewModel::setOpenRouterKey, modifier = Modifier.fillMaxWidth().padding(top = 4.dp), textStyle = HaloTypography.bodySmall, placeholder = { Text("sk-or-...") }, visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = HaloGlassColors.Accent, unfocusedBorderColor = HaloGlassColors.Glass))
                    }
                    "provider.ollama" -> {
                        Spacer(Modifier.height(12.dp))
                        Text("OLLAMA ENDPOINT", style = HaloTypography.labelSmall, color = HaloGlassColors.TextMuted)
                        OutlinedTextField(value = uiState.ollamaEndpoint, onValueChange = viewModel::setOllamaEndpoint, modifier = Modifier.fillMaxWidth().padding(top = 4.dp), textStyle = HaloTypography.bodySmall, placeholder = { Text("http://192.168.1.100:11434") }, singleLine = true, colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = HaloGlassColors.Accent, unfocusedBorderColor = HaloGlassColors.Glass))
                        Spacer(Modifier.height(6.dp))
                        Text("Physical phone: use the PC's LAN IP. The emulator may use 10.0.2.2.", style = HaloTypography.labelSmall, color = HaloGlassColors.TextMuted)
                    }
                }
            }
        }

        HaloGlassSection(title = "ON-DEVICE MODELS (GGUF)") {
            Column(Modifier.padding(8.dp)) {
                if (uiState.localModels.isEmpty()) Text("No GGUF models registered.", style = HaloTypography.bodySmall, color = HaloGlassColors.TextMuted)
                uiState.localModels.forEach { model ->
                    val dlState = uiState.downloadStates[model.id] ?: com.halo.providers.llamacpp.DownloadState.NotDownloaded
                    val isSelected = uiState.activeLocalModelId == model.id
                    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(model.name, style = HaloTypography.bodyMedium, color = HaloGlassColors.TextPrimary)
                                if (isSelected) { Spacer(Modifier.width(8.dp)); Text("ACTIVE", style = HaloTypography.labelSmall, color = HaloGlassColors.Success) }
                            }
                            Text(model.description, style = HaloTypography.labelSmall, color = HaloGlassColors.TextMuted)
                            if (model.sizeMb > 0) Text("${model.sizeMb} MB", style = HaloTypography.labelSmall, color = HaloGlassColors.Accent)
                        }
                        when (dlState) {
                            com.halo.providers.llamacpp.DownloadState.Downloaded -> if (isSelected) HaloGlassChip(text = "Active", material = HaloGlassMaterials.Interactive.copy(containerColor = HaloGlassColors.Success.copy(alpha = 0.2f), contentColor = HaloGlassColors.Success)) else HaloGlassButton(text = "Use", onClick = { viewModel.setActiveLocalModel(model.id) })
                            com.halo.providers.llamacpp.DownloadState.Downloading -> HaloGlassChip(text = "Downloading", material = HaloGlassMaterials.Interactive.copy(containerColor = HaloGlassColors.Accent.copy(alpha = 0.2f), contentColor = HaloGlassColors.Accent))
                            com.halo.providers.llamacpp.DownloadState.Failed -> HaloGlassChip(text = "Failed", material = HaloGlassMaterials.Interactive.copy(containerColor = HaloGlassColors.Warning.copy(alpha = 0.2f), contentColor = HaloGlassColors.Warning))
                            else -> HaloGlassButton(text = "Download", onClick = { viewModel.downloadModel(model.id) })
                        }
                    }
                    HaloGlassDivider()
                }
                Spacer(Modifier.height(12.dp))
                Text("FETCH GGUF", style = HaloTypography.labelSmall, color = HaloGlassColors.TextMuted)
                OutlinedTextField(value = customUrl, onValueChange = { customUrl = it }, modifier = Modifier.fillMaxWidth().padding(top = 4.dp), textStyle = HaloTypography.bodySmall, placeholder = { Text("https://huggingface.co/.../model.gguf") }, singleLine = true)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = customName, onValueChange = { customName = it }, modifier = Modifier.fillMaxWidth(), textStyle = HaloTypography.bodySmall, placeholder = { Text("Model display name") }, singleLine = true)
                Spacer(Modifier.height(6.dp))
                Text(if (customModelReady) "Ready to add this GGUF model to the local catalog." else "Enter both the GGUF URL and a display name to continue.", style = HaloTypography.labelSmall, color = if (customModelReady) HaloGlassColors.TextSecondary else HaloGlassColors.TextMuted)
                Spacer(Modifier.height(10.dp))
                HaloGlassButton(text = "Download GGUF", onClick = { viewModel.downloadCustomUrl(customUrl, customName); customUrl = ""; customName = "" }, enabled = customModelReady, modifier = Modifier.fillMaxWidth())
            }
        }

        HaloGlassCard {
            Column(Modifier.padding(8.dp)) {
                Row(Modifier.fillMaxWidth().defaultMinSize(minHeight = 56.dp).clickable { advancedExpanded = !advancedExpanded }, horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Advanced Settings", style = HaloTypography.bodyMedium, color = HaloGlassColors.TextPrimary)
                    Text(if (advancedExpanded) "−" else "+", style = HaloTypography.bodyLarge, color = HaloGlassColors.TextMuted)
                }
                AnimatedVisibility(advancedExpanded) {
                    Column {
                        Spacer(Modifier.height(12.dp))
                        AdvancedToggle("Memory", "Long-term cognitive context", uiState.isMemoryEnabled) { viewModel.setMemoryEnabled(it) }
                        HaloGlassDivider()
                        AdvancedToggle("Tools", "Model-requested actions", uiState.isToolsEnabled) { viewModel.setToolsEnabled(it) }
                        HaloGlassDivider()
                        Text("Temperature: ${String.format(java.util.Locale.ROOT, "%.1f", uiState.temperature)}", style = HaloTypography.bodySmall, color = HaloGlassColors.TextSecondary)
                        Slider(value = uiState.temperature, onValueChange = viewModel::setTemperature, valueRange = 0f..1.2f)
                        Spacer(Modifier.height(12.dp))
                        Text("CONTEXT POLICY", style = HaloTypography.labelSmall, color = HaloGlassColors.TextMuted)
                        AdvancedToggle("Conversation", "Include chat history", uiState.policy.includeConversation) { viewModel.updatePolicy(conversation = it) }
                        HaloGlassDivider()
                        AdvancedToggle("Recent Events", "Timeline context", uiState.policy.includeRecentEvents) { viewModel.updatePolicy(recentEvents = it) }
                        HaloGlassDivider()
                        AdvancedToggle("Images", "Camera snapshot context", uiState.policy.includeImages) { viewModel.updatePolicy(images = it) }
                    }
                }
            }
        }
    }

    if (showProviderPicker) ProviderPickerDialog(providers = uiState.providers, onSelect = { viewModel.selectProvider(it); showProviderPicker = false }, onDismiss = { showProviderPicker = false })
    if (showModelPicker) HaloGlassModelPickerDialog(models = uiState.availableModels, selectedModelId = uiState.activeModelId, onSelect = { viewModel.selectModel(it); showModelPicker = false }, onRefresh = viewModel::refreshModels, isRefreshing = uiState.isRefreshingModels, onDismiss = { showModelPicker = false })
}

@Composable
private fun ProviderStatusChip(status: ProviderStatus) {
    val text = when (status.state) {
        ProviderStatus.State.READY -> "READY"
        ProviderStatus.State.STANDBY -> "STANDBY"
        ProviderStatus.State.LOADING -> "LOADING"
        ProviderStatus.State.UNCONFIGURED -> "UNCONFIGURED"
        ProviderStatus.State.OFFLINE -> "OFFLINE"
        ProviderStatus.State.MODEL_MISSING -> "MODEL MISSING"
        ProviderStatus.State.UNAVAILABLE -> "UNAVAILABLE"
        ProviderStatus.State.ERROR -> "ERROR"
    }
    val tone = statusColor(status.state)
    HaloGlassChip(text = text, material = HaloGlassMaterials.Interactive.copy(containerColor = tone.copy(alpha = 0.16f), contentColor = tone))
}

private fun statusColor(state: ProviderStatus.State) = when (state) {
    ProviderStatus.State.READY -> HaloGlassColors.Success
    ProviderStatus.State.STANDBY -> HaloGlassColors.TextSecondary
    ProviderStatus.State.LOADING -> HaloGlassColors.Accent
    ProviderStatus.State.UNCONFIGURED,
    ProviderStatus.State.OFFLINE,
    ProviderStatus.State.MODEL_MISSING,
    ProviderStatus.State.UNAVAILABLE,
    ProviderStatus.State.ERROR -> HaloGlassColors.Warning
}

private fun providerDescription(id: String) = when (id) {
    "android.system.assistant" -> "Phone assistant handoff"
    "openrouter" -> "Remote model gateway"
    "provider.ollama" -> "Local Ollama server"
    "provider.llamacpp.native" -> "On-device GGUF / llama.cpp"
    else -> "Inference provider"
}

@Composable
private fun CapabilityRow(name: String, supported: Boolean) {
    Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(name, color = HaloGlassColors.TextPrimary, style = HaloTypography.bodyMedium)
        Text(if (supported) "✓" else "—", color = if (supported) HaloGlassColors.Success else HaloGlassColors.TextMuted, style = HaloTypography.bodyLarge)
    }
}

@Composable
private fun AdvancedToggle(title: String, description: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = HaloTypography.bodyMedium, color = HaloGlassColors.TextPrimary)
            Text(description, style = HaloTypography.labelSmall, color = HaloGlassColors.TextMuted)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun ProviderPickerDialog(providers: List<AiProviderUiState>, onSelect: (String) -> Unit, onDismiss: () -> Unit) {
    HaloGlassDialog(title = "Select Provider", onDismissRequest = onDismiss) {
        Column(Modifier.padding(16.dp)) {
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 400.dp)) {
                items(providers) { provider ->
                    Row(Modifier.fillMaxWidth().clickable { onSelect(provider.id) }.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(provider.name, color = HaloGlassColors.TextPrimary)
                                if (provider.isHandoff) {
                                    Spacer(Modifier.width(8.dp))
                                    HaloGlassChip(text = "ASSISTANT", material = HaloGlassMaterials.Interactive.copy(containerColor = HaloGlassColors.Accent.copy(alpha = 0.16f), contentColor = HaloGlassColors.Accent))
                                }
                            }
                            val isReady = provider.status.state == ProviderStatus.State.READY || provider.status.state == ProviderStatus.State.STANDBY
                            val statusText = when {
                                provider.isSelected && provider.isHandoff && isReady -> "Selected / Handoff ready"
                                provider.isSelected && isReady -> "Selected / Ready"
                                provider.isSelected -> "Selected / Unavailable"
                                provider.isHandoff && isReady -> "Available / Handoff"
                                isReady -> "Available / Not selected"
                                provider.status.state == ProviderStatus.State.LOADING -> "Loading / Please wait"
                                else -> "Unavailable"
                            }
                            Text(statusText, style = HaloTypography.labelSmall, color = if (isReady) HaloGlassColors.Success else if (provider.status.state == ProviderStatus.State.LOADING) HaloGlassColors.Accent else HaloGlassColors.Warning)
                            provider.status.message?.let { Text(it, style = HaloTypography.labelSmall, color = HaloGlassColors.TextMuted) }
                        }
                        if (provider.isSelected) Text("●", color = HaloGlassColors.Accent)
                    }
                    HaloGlassDivider()
                }
            }
        }
    }
}
