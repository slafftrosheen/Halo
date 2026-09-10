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
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import kotlinx.coroutines.flow.collectLatest
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext

@Composable
fun IntelligenceScreen(viewModel: IntelligenceViewModel, onBack: () -> Unit = {}) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var showProviderPicker by remember { mutableStateOf(false) }
    var showModelPicker by remember { mutableStateOf(false) }
    var advancedExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(viewModel) {
        viewModel.testConnectionResult.collectLatest { success ->
            val message = if (success) "Connection test successful!" else "Connection test failed."
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }

    HaloGlassPage(title = "") {
        HaloGlassBackHeader(
            title = "Intelligence",
            onBack = onBack
        )

        // 1. ACTIVE PROVIDER
        HaloGlassSection(title = "ACTIVE PROVIDER") {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(uiState.activeProviderName.ifEmpty { "None" }, style = HaloTypography.bodyLarge, color = HaloGlassColors.TextPrimary)
                    uiState.activeProviderStatus.message?.let {
                        Text(it, style = HaloTypography.labelSmall, color = HaloGlassColors.Warning)
                    }
                    Text(
                        text = if (uiState.activeModelId != null) uiState.activeModelId!! else "Default Model",
                        style = HaloTypography.labelMedium,
                        color = HaloGlassColors.TextSecondary
                    )
                }
                
                val statusText = when(uiState.activeProviderStatus.state) {
                    ProviderStatus.State.READY -> "READY"
                    ProviderStatus.State.STANDBY -> "STANDBY"
                    ProviderStatus.State.LOADING -> "LOADING"
                    ProviderStatus.State.UNCONFIGURED -> "UNCONFIGURED"
                    ProviderStatus.State.OFFLINE -> "OFFLINE"
                    ProviderStatus.State.MODEL_MISSING -> "MODEL MISSING"
                    ProviderStatus.State.UNAVAILABLE -> "UNAVAILABLE"
                    ProviderStatus.State.ERROR -> "ERROR"
                }

                HaloGlassChip(
                    text = statusText,
                    material = when(uiState.activeProviderStatus.state) {
                        ProviderStatus.State.READY -> com.halo.app.ui.glass.material.HaloGlassMaterials.Interactive.copy(
                            containerColor = HaloGlassColors.Success.copy(alpha = 0.2f),
                            contentColor = HaloGlassColors.Success
                        )
                        ProviderStatus.State.STANDBY -> com.halo.app.ui.glass.material.HaloGlassMaterials.Interactive.copy(
                            containerColor = HaloGlassColors.Success.copy(alpha = 0.1f),
                            contentColor = HaloGlassColors.TextSecondary
                        )
                        ProviderStatus.State.LOADING -> com.halo.app.ui.glass.material.HaloGlassMaterials.Interactive.copy(
                            containerColor = HaloGlassColors.Accent.copy(alpha = 0.2f),
                            contentColor = HaloGlassColors.Accent
                        )
                        else -> com.halo.app.ui.glass.material.HaloGlassMaterials.Interactive.copy(
                            containerColor = HaloGlassColors.Warning.copy(alpha = 0.2f),
                            contentColor = HaloGlassColors.Warning
                        )
                    }
                )
            }
        }

        // 2. CAPABILITIES
        HaloGlassSection(title = "CAPABILITIES") {
            CapabilityRow("Text", uiState.capabilities.contains(AiCapability.TEXT))
            HaloGlassDivider()
            CapabilityRow("Vision", uiState.capabilities.contains(AiCapability.IMAGE))
            HaloGlassDivider()
            CapabilityRow("Audio", uiState.capabilities.contains(AiCapability.AUDIO))
            HaloGlassDivider()
            CapabilityRow("Tools", uiState.capabilities.contains(AiCapability.TOOLS))
        }

        // 3. MODEL SELECTION
        HaloGlassSection(title = "MODEL") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 52.dp)
                    .clickable { showModelPicker = true }
                    .padding(horizontal = 8.dp),
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

        // 4. PROVIDER SELECTION
        HaloGlassSection(title = "PROVIDER") {
            Column(Modifier.padding(8.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 52.dp)
                        .clickable { showProviderPicker = true }
                        .padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            uiState.activeProviderName.ifEmpty { "None" },
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
                
                Spacer(Modifier.height(16.dp))
                HaloGlassButton(
                    text = "Test Connection",
                    onClick = { viewModel.testConnection() },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
                )

                if (uiState.activeProviderId == "openrouter") {
                    Spacer(Modifier.height(16.dp))
                    Text("API KEY", style = HaloTypography.labelSmall, color = HaloGlassColors.TextMuted)
                    OutlinedTextField(
                        value = uiState.openRouterKey,
                        onValueChange = { viewModel.setOpenRouterKey(it) },
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        textStyle = HaloTypography.bodySmall,
                        placeholder = { Text("sk-or-...") },
                        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
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

        // ON-DEVICE GGUF MODELS (Hugging Face / llama.cpp)
        var customUrl by remember { mutableStateOf("") }
        var customName by remember { mutableStateOf("") }

        HaloGlassSection(title = "ON-DEVICE MODELS (GGUF)") {
            Column(Modifier.padding(8.dp)) {
                uiState.localModels.forEach { model ->
                    val dlState = uiState.downloadStates[model.id] ?: com.halo.providers.llamacpp.DownloadState.NotDownloaded
                    val isSelected = uiState.activeLocalModelId == model.id

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(model.name, style = HaloTypography.bodyMedium, color = HaloGlassColors.TextPrimary)
                                if (isSelected) {
                                    Spacer(Modifier.width(8.dp))
                                    Text("(ACTIVE)", style = HaloTypography.labelSmall, color = HaloGlassColors.Success)
                                }
                            }
                            Text(model.description, style = HaloTypography.labelSmall, color = HaloGlassColors.TextMuted)
                            if (model.sizeMb > 0) {
                                Text("${model.sizeMb} MB", style = HaloTypography.labelSmall, color = HaloGlassColors.Accent)
                            }
                        }

                        when (dlState) {
                            com.halo.providers.llamacpp.DownloadState.Downloaded -> {
                                if (isSelected) {
                                    HaloGlassChip(text = "Active", material = com.halo.app.ui.glass.material.HaloGlassMaterials.Interactive.copy(containerColor = HaloGlassColors.Success.copy(alpha = 0.2f), contentColor = HaloGlassColors.Success))
                                } else {
                                    HaloGlassButton(text = "Use Model", onClick = { viewModel.setActiveLocalModel(model.id) })
                                }
                            }
                            com.halo.providers.llamacpp.DownloadState.Downloading -> {
                                HaloGlassChip(text = "Downloading...", material = com.halo.app.ui.glass.material.HaloGlassMaterials.Interactive.copy(containerColor = HaloGlassColors.Accent.copy(alpha = 0.2f), contentColor = HaloGlassColors.Accent))
                            }
                            else -> {
                                HaloGlassButton(text = "Download", onClick = { viewModel.downloadModel(model.id) })
                            }
                        }
                    }
                    HaloGlassDivider()
                }

                Spacer(Modifier.height(12.dp))
                Text("FETCH FROM HUGGING FACE", style = HaloTypography.labelSmall, color = HaloGlassColors.TextMuted)
                OutlinedTextField(
                    value = customUrl,
                    onValueChange = { customUrl = it },
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    textStyle = HaloTypography.bodySmall,
                    placeholder = { Text("https://huggingface.co/org/repo/resolve/main/model.gguf") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = HaloGlassColors.Accent,
                        unfocusedBorderColor = HaloGlassColors.Glass
                    )
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = customName,
                    onValueChange = { customName = it },
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = HaloTypography.bodySmall,
                    placeholder = { Text("Model Display Name (e.g. Llama-3-GGUF)") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = HaloGlassColors.Accent,
                        unfocusedBorderColor = HaloGlassColors.Glass
                    )
                )
                Spacer(Modifier.height(12.dp))
                HaloGlassButton(
                    text = "Download GGUF from Hugging Face",
                    onClick = {
                        viewModel.downloadCustomUrl(customUrl, customName)
                        customUrl = ""
                        customName = ""
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        // 5. ADVANCED
        HaloGlassCard {
            Column(Modifier.padding(8.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 56.dp)
                        .clickable { advancedExpanded = !advancedExpanded }
                        .padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Advanced Settings", style = HaloTypography.bodyMedium, color = HaloGlassColors.TextPrimary)
                    Text(if (advancedExpanded) "−" else "+", style = HaloTypography.bodyLarge, color = HaloGlassColors.TextMuted)
                }
                
                AnimatedVisibility(visible = advancedExpanded) {
                    Column {
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
    }

    if (showProviderPicker) {
        ProviderPickerDialog(
            providers = uiState.providers,
            onSelect = { viewModel.selectProvider(it); showProviderPicker = false },
            onDismiss = { showProviderPicker = false }
        )
    }

    if (showModelPicker) {
        HaloGlassModelPickerDialog(
            models = uiState.availableModels,
            selectedModelId = uiState.activeModelId,
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
                            Column {
                                Text(provider.name, color = HaloGlassColors.TextPrimary)
                                
                                val isReady = provider.status.state == ProviderStatus.State.READY || provider.status.state == ProviderStatus.State.STANDBY || provider.status.state == ProviderStatus.State.LOADING
                                val statusText = when {
                                    provider.isSelected && isReady -> "Selected / Ready"
                                    provider.isSelected && !isReady -> "Selected / Unavailable"
                                    !provider.isSelected && isReady -> "Available / Not selected"
                                    else -> "Failed"
                                }
                                
                                Text(statusText, style = HaloTypography.labelSmall, color = if (isReady) HaloGlassColors.Success else HaloGlassColors.Warning)

                                provider.status.message?.let {
                                    Text(it, style = HaloTypography.labelSmall, color = HaloGlassColors.Warning)
                                }
                            }
                            if (provider.isSelected) Text("●", color = HaloGlassColors.Accent)
                        }
                    }
                }
            }
        }
    )
}
