package com.halo.app.ui.screens.settings

import androidx.lifecycle.compose.collectAsStateWithLifecycle

import androidx.lifecycle.ViewModel
import com.halo.app.ui.design.HaloScreenHeader
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.Alignment
import com.halo.app.R
import com.halo.app.ui.icons.HaloGlyph
import com.halo.app.ui.icons.HaloIcon
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
fun ContextPrivacyScreen(viewModel: ContextPrivacyViewModel, onBack: () -> Unit,
    onAdvanced: () -> Unit = {}, onPermissions: () -> Unit = {}) {
    val policy by viewModel.policy.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) { viewModel.refresh() }
    ContextPrivacyContent(policy, viewModel::update, onBack, onAdvanced, onPermissions)
}

@Composable
internal fun ContextPrivacyContent(policy: AiContextPolicy,
    update: ((AiContextPolicy) -> AiContextPolicy) -> Unit,
    onBack: () -> Unit, onAdvanced: () -> Unit, onPermissions: () -> Unit = {}) {
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(com.halo.app.ui.design.HaloSpacing.screen), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { HaloScreenHeader(stringResource(R.string.brand_privacy_title),
            stringResource(R.string.brand_privacy_subtitle), navigation = onBack,
            glyph = HaloGlyph.SECURITY) }
        item {
            // This screen owns context policy only. There is no verified daily
            // local/cloud transfer ledger here, so NEVER show a fake zero count.
            Surface(shape = com.halo.app.ui.design.HaloShapes.card,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                border = androidx.compose.foundation.BorderStroke(
                    1.dp, MaterialTheme.colorScheme.outlineVariant)) {
                Row(Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    HaloIcon(HaloGlyph.SECURITY, null, Modifier.size(30.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(stringResource(R.string.brand_privacy_hero_title),
                            style = MaterialTheme.typography.titleMedium)
                        Text(stringResource(R.string.brand_privacy_hero_detail),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        item {
            HaloSettingsSection(stringResource(R.string.brand_privacy_where)) {
                PrivacyLocationRow(HaloGlyph.CONVERSATION,
                    stringResource(R.string.brand_privacy_conversations),
                    stringResource(R.string.brand_privacy_local_storage))
                PrivacyLocationRow(HaloGlyph.NOTE,
                    stringResource(R.string.brand_privacy_notebook),
                    stringResource(R.string.brand_privacy_local_storage))
                PrivacyLocationRow(HaloGlyph.CLOUD,
                    stringResource(R.string.brand_privacy_remote),
                    stringResource(R.string.brand_privacy_remote_explanation))
            }
        }
        item { Text(uiText("Sharing permissions never select a cloud provider. halo can answer without enabling tools."),
            style = MaterialTheme.typography.bodySmall) }
        item { HaloSettingsSection(uiText("Conversation context")) {
            ContextToggle("Conversation", policy.includeConversation) { update { p -> p.copy(includeConversation = it) } }
            ContextToggle("Personal memory", policy.includeMemories) { update { p -> p.copy(includeMemories = it) } }
            ContextToggle("Workspace", policy.includeWorkspace) { update { p -> p.copy(includeWorkspace = it) } }
            ContextToggle("Images", policy.includeImages) { update { p -> p.copy(includeImages = it) } }
            ContextToggle("Tools", policy.includeAvailableTools) { update { p -> p.copy(includeAvailableTools = it) } }
        } }
        item { HaloSettingsSection(uiText("Allow context with remote inference")) {
            Text(stringResource(R.string.brand_privacy_remote_scope),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            ContextToggle("Workspace references", policy.allowCloudWorkspace) { update { p -> p.copy(allowCloudWorkspace = it) } }
            ContextToggle("Current screen", policy.allowCloudScreenContext) { update { p -> p.copy(allowCloudScreenContext = it) } }
            ContextToggle("Images", policy.allowCloudImages) { update { p -> p.copy(allowCloudImages = it) } }
            ContextToggle("Personal memory", policy.allowCloudMemories) { update { p -> p.copy(allowCloudMemories = it) } }
        } }
        item { HaloSettingsSection(uiText("Preferences")) {
            HaloWorkspaceDestination(stringResource(R.string.brand_privacy_permissions),
                stringResource(R.string.brand_privacy_permissions_summary), onPermissions)
            HaloWorkspaceDestination(uiText("Advanced intelligence"),
                uiText("Generation and agent preferences"), onAdvanced)
        } }
    }
    }
}
@Composable
private fun ContextToggle(label: String, checked: Boolean, changed: (Boolean) -> Unit) {
    HaloWorkspaceToggleRow(uiText(label), checked, changed)
}

@Composable
private fun PrivacyLocationRow(glyph: HaloGlyph, title: String, detail: String) {
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        HaloIcon(glyph, contentDescription = null, modifier = Modifier.size(24.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(detail, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
