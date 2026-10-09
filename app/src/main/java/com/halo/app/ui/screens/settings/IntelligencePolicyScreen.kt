package com.halo.app.ui.screens.settings

import com.halo.app.language.uiText

import androidx.lifecycle.compose.collectAsStateWithLifecycle

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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.halo.app.ui.workspace.HaloWorkspaceButton
import com.halo.app.ui.workspace.HaloWorkspaceCard
import com.halo.app.ui.workspace.HaloWorkspaceHeader
import com.halo.app.ui.workspace.HaloWorkspaceSection

@Composable
fun IntelligencePolicyScreen(viewModel: IntelligenceViewModel, onBack: () -> Unit, onBrowseModels: () -> Unit) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
        HaloWorkspaceHeader(title = uiText("Context and on-device policy"), subtitle = "Privacy, capability packs and advanced agent settings", onBack = onBack)
        var temperature by remember(uiState.temperature) { mutableStateOf(uiState.temperature) }
        HaloWorkspaceCard {
            Text("On-device temperature: ${"%.2f".format(temperature)}")
            androidx.compose.material3.Slider(value = temperature, onValueChange = { temperature = it },
                valueRange = 0f..2f, onValueChangeFinished = { viewModel.setTemperature(temperature) })
            Text("Used by the local model preferences. Remote providers retain their own sampling policy.")
        }
        var multimodal by remember { mutableStateOf<com.halo.app.capture.MultimodalStatus?>(null) }
        androidx.compose.runtime.LaunchedEffect(uiState.roleBindings, uiState.visionQualification, uiState.activeModelId) {
            multimodal = com.halo.app.capture.inspectMultimodal(com.halo.app.di.ManualDependencyGraph.aiProviderRouter)
        }
        HaloWorkspaceCard {
            Text(uiText("On-device capabilities"), style = MaterialTheme.typography.titleMedium)
            multimodal?.packs?.forEach { pack ->
                Text("${pack.descriptor.displayName} · ${pack.state.name.lowercase().replace('_', ' ')}")
                Text(pack.descriptor.description, style = MaterialTheme.typography.bodySmall)
            }
            Text("Captured context: ephemeral by default. Visual captures are deleted after the interaction; explicitly saved images belong to Notebook.", style = MaterialTheme.typography.bodySmall)
            var screenImageFallback by remember { mutableStateOf(com.halo.app.di.ManualDependencyGraph.storage.getBoolean("policy_screen_image_fallback", false)) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(uiText("Screen image fallback"), modifier = Modifier.weight(1f))
                Switch(checked = screenImageFallback, onCheckedChange = {
                    screenImageFallback = it
                    com.halo.app.di.ManualDependencyGraph.storage.putBoolean("policy_screen_image_fallback", it)
                })
            }
            Text("Only unreadable, permitted apps. Semantic screen context remains preferred.", style = MaterialTheme.typography.bodySmall)
            var dynamicWebExtraction by remember { mutableStateOf(com.halo.app.di.ManualDependencyGraph.storage.getBoolean("policy_dynamic_web_extraction", false)) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(uiText("Rendered web page extraction"), modifier = Modifier.weight(1f))
                Switch(checked = dynamicWebExtraction, onCheckedChange = {
                    dynamicWebExtraction = it
                    com.halo.app.di.ManualDependencyGraph.storage.putBoolean("policy_dynamic_web_extraction", it)
                })
            }
            Text("Allow bounded page scripts when static extraction has little readable text.", style = MaterialTheme.typography.bodySmall)
            HaloWorkspaceButton(text = uiText("Inspect capability models"), onClick = onBrowseModels)
        }
        Spacer(Modifier.height(12.dp))
        HaloWorkspaceCard {
            Text(uiText("Advanced agent workloads"), style = MaterialTheme.typography.titleMedium)
            var delegation by remember { mutableStateOf(com.halo.app.di.ManualDependencyGraph.storage.getBoolean("policy_long_task_delegation", true)) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(uiText("Allow bounded delegation"), modifier = Modifier.weight(1f))
                Switch(checked = delegation, onCheckedChange = {
                    delegation = it
                    com.halo.app.di.ManualDependencyGraph.storage.putBoolean("policy_long_task_delegation", it)
                })
            }
            Text("Read-only children. Research, compare and analyze use Reasoning; summarize and extract use Fast. Existing routing and cloud policy apply.", style = MaterialTheme.typography.bodySmall)
            var compaction by remember { mutableStateOf(com.halo.app.di.ManualDependencyGraph.storage.getBoolean("policy_context_compaction", true)) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(uiText("Compact completed history"), modifier = Modifier.weight(1f))
                Switch(checked = compaction, onCheckedChange = {
                    compaction = it
                    com.halo.app.di.ManualDependencyGraph.storage.putBoolean("policy_context_compaction", it)
                })
            }
            Text("Threshold: 70%. Originals stay in history; summaries are context optimizations. Local-only routing prevents remote summarization.", style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.height(12.dp))
        HaloWorkspaceSection(title = uiText("Runtime policy")) {
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

        HaloWorkspaceSection(title = uiText("Remote context sharing")) {
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
