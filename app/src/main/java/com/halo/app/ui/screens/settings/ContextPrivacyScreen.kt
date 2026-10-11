package com.halo.app.ui.screens.settings

import androidx.lifecycle.compose.collectAsStateWithLifecycle

import androidx.lifecycle.ViewModel
import com.halo.app.ui.design.HaloScreenHeader
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
import com.halo.app.language.uiText
import com.halo.app.ui.workspace.HaloWorkspaceSection
import com.halo.app.ui.workspace.HaloWorkspaceToggleRow
import com.halo.app.ui.workspace.HaloWorkspaceDestination
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
fun ContextPrivacyScreen(viewModel: ContextPrivacyViewModel, onBack: () -> Unit, onAdvanced: () -> Unit = {}) {
    val policy by viewModel.policy.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) { viewModel.refresh() }
    ContextPrivacyContent(policy, viewModel::update, onBack, onAdvanced)
}

@Composable
internal fun ContextPrivacyContent(policy: AiContextPolicy,
    update: ((AiContextPolicy) -> AiContextPolicy) -> Unit,
    onBack: () -> Unit, onAdvanced: () -> Unit) {
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { HaloScreenHeader(uiText("Context & privacy"), uiText("Choose what Halo can use"), navigation = onBack) }
        item { Text(uiText("Sharing permissions never select a cloud provider. Halo can answer without enabling tools.")) }
        item { HaloWorkspaceSection(uiText("Conversation context")) {
            ContextToggle("Conversation", policy.includeConversation) { update { p -> p.copy(includeConversation = it) } }
            ContextToggle("Personal memory", policy.includeMemories) { update { p -> p.copy(includeMemories = it) } }
            ContextToggle("Workspace", policy.includeWorkspace) { update { p -> p.copy(includeWorkspace = it) } }
            ContextToggle("Images", policy.includeImages) { update { p -> p.copy(includeImages = it) } }
            ContextToggle("Tools", policy.includeAvailableTools) { update { p -> p.copy(includeAvailableTools = it) } }
        } }
        item { HaloWorkspaceSection(uiText("Allow context with remote inference")) {
            ContextToggle("Workspace references", policy.allowCloudWorkspace) { update { p -> p.copy(allowCloudWorkspace = it) } }
            ContextToggle("Current screen", policy.allowCloudScreenContext) { update { p -> p.copy(allowCloudScreenContext = it) } }
            ContextToggle("Images", policy.allowCloudImages) { update { p -> p.copy(allowCloudImages = it) } }
            ContextToggle("Personal memory", policy.allowCloudMemories) { update { p -> p.copy(allowCloudMemories = it) } }
        } }
        item { HaloWorkspaceSection(uiText("Preferences")) {
            HaloWorkspaceDestination("Advanced intelligence", "Generation and agent preferences", onAdvanced)
        } }
    }
    }
}
@Composable
private fun ContextToggle(label: String, checked: Boolean, changed: (Boolean) -> Unit) {
    HaloWorkspaceToggleRow(uiText(label), checked, changed)
}
