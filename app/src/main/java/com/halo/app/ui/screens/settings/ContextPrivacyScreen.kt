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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.halo.sdk.provider.PaidMediaPolicy
import com.halo.app.online.OnlineOperationRejected
import com.halo.app.online.OnlineProblem

/** A small policy projection; does not subscribe to model catalogues or telemetry. */
class ContextPrivacyViewModel(private val storage: Storage,
    private val canGrantRemoteMedia: () -> Boolean = { false }) : ViewModel() {
    private val current = MutableStateFlow(storage.readRuntimeAiContextPolicy())
    val policy = current.asStateFlow()
    private val mediaConsent = MutableStateFlow(storage.getBoolean(PaidMediaPolicy.CONSENT_KEY, false))
    val paidMediaAllowed = mediaConsent.asStateFlow()
    fun refresh() {
        current.value = storage.readRuntimeAiContextPolicy()
        mediaConsent.value = storage.getBoolean(PaidMediaPolicy.CONSENT_KEY, false)
    }
    fun update(change: (AiContextPolicy) -> AiContextPolicy) {
        val requested = change(storage.readRuntimeAiContextPolicy())
        val updated = requested.copy(allowCloudImages = requested.allowCloudImages &&
            storage.getBoolean(PaidMediaPolicy.CONSENT_KEY, false))
        storage.writeRuntimeAiContextPolicy(updated); current.value = updated
    }

    /** Explicit spending approval, not a provider selection or permission to send an image. */
    fun grantRemoteImageAccess() {
        if (!canGrantRemoteMedia()) throw OnlineOperationRejected(OnlineProblem.CHILD_RESTRICTED)
        storage.putBatch(booleans = mapOf(PaidMediaPolicy.CONSENT_KEY to true,
            com.halo.app.ai.KEY_CLOUD_IMAGES to true))
        refresh()
    }
}

@Composable
fun ContextPrivacyScreen(viewModel: ContextPrivacyViewModel, onBack: () -> Unit,
    onAdvanced: () -> Unit = {}, onPermissions: () -> Unit = {}) {
    val policy by viewModel.policy.collectAsStateWithLifecycle()
    var costs by remember { mutableStateOf(false) }
    LaunchedEffect(viewModel) { viewModel.refresh() }
    ContextPrivacyContent(policy, viewModel::update, onBack, onAdvanced, onPermissions,
        onCosts = { costs = true })
    if (costs) com.halo.app.ui.online.OnlineCostDialog {
        costs = false; viewModel.refresh()
    }
}

@Composable
internal fun ContextPrivacyContent(policy: AiContextPolicy,
    update: ((AiContextPolicy) -> AiContextPolicy) -> Unit,
    onBack: () -> Unit, onAdvanced: () -> Unit, onPermissions: () -> Unit = {},
    paidMediaAllowed: Boolean = false, onRequestRemoteImages: () -> Unit = {},
    onCosts: () -> Unit = {}, failure: OnlineProblem? = null) {
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
        item {
            // Policy disclosure, not a claim that this page audits every SDK/network request.
            Text(stringResource(R.string.brand_privacy_backend_disclosure),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item { HaloSettingsSection(uiText("Private / Cloud")) {
            Text(uiText(if (policy.allowCloudImages) "Cloud mode" else "Private mode"), style = MaterialTheme.typography.titleMedium)
            Text(uiText(if (policy.allowCloudImages)
                "Cloud models can use your conversation, images, memories, and workspace. Provider charges may apply."
                else "Everything stays on this phone. Cloud features are off."))
            Text(uiText("One switch at the top of every screen controls sharing. No separate image, memory, or workspace permissions are needed."))
            Text(uiText("Android permissions, Kids restrictions, spending limits, and action confirmations still apply."), style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = onCosts) { Text(stringResource(R.string.online_costs)) }
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
