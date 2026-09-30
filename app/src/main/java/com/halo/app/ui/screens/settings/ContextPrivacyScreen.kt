package com.halo.app.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.halo.app.ai.readRuntimeAiContextPolicy
import com.halo.app.ai.writeRuntimeAiContextPolicy
import com.halo.sdk.provider.AiContextPolicy
import com.halo.sdk.runtime.Storage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** A small policy projection; does not subscribe to model catalogues or telemetry. */
class ContextPrivacyViewModel(private val storage: Storage) : ViewModel() {
    private val current = MutableStateFlow(storage.readRuntimeAiContextPolicy())
    val policy = current.asStateFlow()
    fun refresh() { current.value = storage.readRuntimeAiContextPolicy() }
    fun update(change: (AiContextPolicy) -> AiContextPolicy) {
        val updated = change(storage.readRuntimeAiContextPolicy())
        storage.writeRuntimeAiContextPolicy(updated); current.value = updated
    }
}

@Composable
fun ContextPrivacyScreen(viewModel: ContextPrivacyViewModel, onBack: () -> Unit) {
    val policy by viewModel.policy.collectAsState()
    LaunchedEffect(viewModel) { viewModel.refresh() }
    LazyColumn(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { TextButton(onClick = onBack) { Text("Back") }; Text("Context and privacy", style = MaterialTheme.typography.headlineMedium) }
        item { Text("General conversational answering is a core Halo capability and does not depend on tool availability. Sharing permissions never select a cloud provider.") }
        item { ContextToggle("Conversation", policy.includeConversation) { viewModel.update { p -> p.copy(includeConversation = it) } } }
        item { ContextToggle("Personal memory", policy.includeMemories) { viewModel.update { p -> p.copy(includeMemories = it) } } }
        item { ContextToggle("Workspace", policy.includeWorkspace) { viewModel.update { p -> p.copy(includeWorkspace = it) } } }
        item { ContextToggle("Images", policy.includeImages) { viewModel.update { p -> p.copy(includeImages = it) } } }
        item { Text("Allow context with remote inference", style = MaterialTheme.typography.titleMedium) }
        item { ContextToggle("Workspace references", policy.allowCloudWorkspace) { viewModel.update { p -> p.copy(allowCloudWorkspace = it) } } }
        item { ContextToggle("Current screen", policy.allowCloudScreenContext) { viewModel.update { p -> p.copy(allowCloudScreenContext = it) } } }
        item { ContextToggle("Images", policy.allowCloudImages) { viewModel.update { p -> p.copy(allowCloudImages = it) } } }
        item { ContextToggle("Personal memory", policy.allowCloudMemories) { viewModel.update { p -> p.copy(allowCloudMemories = it) } } }
    }
}
@Composable
private fun ContextToggle(label: String, checked: Boolean, changed: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth()) { Text(label, Modifier.weight(1f)); Switch(checked, changed) }
}
