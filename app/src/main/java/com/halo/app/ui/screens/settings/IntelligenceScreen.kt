package com.halo.app.ui.screens.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.halo.app.R
import com.halo.app.ai.ProviderControlRepository
import com.halo.app.ai.ProviderControlRow
import com.halo.app.ai.label
import com.halo.app.language.uiText
import com.halo.app.ui.design.HaloScreenHeader
import com.halo.app.ui.icons.HaloGlyph
import com.halo.app.ui.icons.HaloIcon
import com.halo.app.ui.workspace.HaloWorkspaceDestination
import com.halo.sdk.provider.AiExecutionLocation

/** Maps the selected provider, not the brand recommendation, to the current location. */
internal fun currentIntelligenceLocation(active: ProviderControlRow?): AiExecutionLocation? =
    active?.descriptor?.location

/** Display-only navigation. Opening a card must NEVER switch inference providers. */
@Composable
internal fun IntelligenceLocationCard(
    title: String, subtitle: String, glyph: HaloGlyph, current: Boolean,
    recommended: Boolean = false, detail: String? = null, onOpen: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        onClick = onOpen,
        modifier = Modifier.fillMaxWidth().heightIn(min = 84.dp),
        shape = com.halo.app.ui.design.HaloShapes.card,
        color = if (current) scheme.primaryContainer else scheme.surfaceContainerLow,
        border = BorderStroke(if (current) 2.dp else 1.dp,
            if (current) scheme.primary else scheme.outlineVariant)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            HaloIcon(glyph, null, modifier = Modifier.size(28.dp),
                tint = if (current) scheme.onPrimaryContainer else scheme.onSurface)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(subtitle, style = MaterialTheme.typography.bodySmall,
                    color = if (current) scheme.onPrimaryContainer else scheme.onSurfaceVariant)
                if (current) Text(stringResource(R.string.brand_intelligence_current),
                    style = MaterialTheme.typography.labelMedium, color = scheme.primary)
                else if (recommended) Text(stringResource(R.string.brand_intelligence_recommended),
                    style = MaterialTheme.typography.labelMedium, color = scheme.primary)
                if (!detail.isNullOrBlank()) Text(detail,
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.onSurfaceVariant, maxLines = 2)
            }
            Text(stringResource(R.string.brand_intelligence_configure),
                style = MaterialTheme.typography.labelMedium,
                color = scheme.primary)
        }
    }
}

@Composable
fun IntelligenceScreen(control: ProviderControlRepository, onBack: () -> Unit,
    onBrowseModels: () -> Unit, onProviderSetup: () -> Unit, onRoles: () -> Unit,
    onDevice: () -> Unit, onContext: () -> Unit, onDiagnostics: () -> Unit,
    onVisionLab: () -> Unit = {}, onAudioLab: () -> Unit = {}) {
    val providerId by control.router.selectedProviderId.collectAsStateWithLifecycle()
    val modelId by control.router.selectedModelId.collectAsStateWithLifecycle()
    val rows by control.rows.collectAsStateWithLifecycle()
    val checkGeneration = rememberProviderGenerationCheck(control)
    val active = rows.firstOrNull { it.descriptor.providerId == providerId }
    val currentLocation = currentIntelligenceLocation(active)
    val local = rows.firstOrNull { it.descriptor.location == AiExecutionLocation.ON_DEVICE }
    val network = rows.filter { it.descriptor.location == AiExecutionLocation.LOCAL_NETWORK }
    val cloud = rows.filter { it.descriptor.location == AiExecutionLocation.REMOTE }
    val step = intelligenceNextStep(active?.configured == true, active?.connection,
        !modelId.isNullOrBlank(), active?.generation)
    var advanced by rememberSaveable { mutableStateOf(false) }
    var locationsExpanded by rememberSaveable { mutableStateOf(false) }
    var modelDetailsExpanded by rememberSaveable { mutableStateOf(false) }

    LazyColumn(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item(key = "header") {
            HaloScreenHeader(uiText("Intelligence"),
                stringResource(R.string.brand_intelligence_where),
                navigation = onBack, glyph = HaloGlyph.MODEL)
        }
        // The real provider and model are one conversation configuration.
        // Round 2 p.38: one status-first card before the single next step.
        item(key = "current_conversation") {
            HaloSettingsSection(uiText("Your conversation")) {
                val scheme = MaterialTheme.colorScheme
                val locationLabel = intelligenceLocationLabel(currentLocation)
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Surface(
                        color = scheme.primaryContainer.copy(alpha = 0.30f),
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
                        modifier = Modifier.size(48.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            HaloIcon(when (currentLocation) {
                                AiExecutionLocation.ON_DEVICE -> HaloGlyph.LOCAL
                                AiExecutionLocation.LOCAL_NETWORK -> HaloGlyph.WIFI
                                AiExecutionLocation.REMOTE -> HaloGlyph.CLOUD
                                null -> HaloGlyph.MODEL
                            }, contentDescription = null, modifier = Modifier.size(24.dp),
                                tint = scheme.primary)
                        }
                    }
                    Column(Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(uiText("Where halo thinks"),
                            style = MaterialTheme.typography.labelMedium,
                            color = scheme.onSurfaceVariant)
                        Text(uiText(locationLabel), style = MaterialTheme.typography.titleSmall)
                        Text(active?.descriptor?.displayName ?: uiText("Setup required"),
                            style = MaterialTheme.typography.bodySmall,
                            color = scheme.onSurfaceVariant,
                            maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
                HorizontalDivider(color = scheme.outlineVariant)
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(uiText("Conversation model"),
                            style = MaterialTheme.typography.labelMedium,
                            color = scheme.onSurfaceVariant)
                        Text(uiText(intelligenceModelSummary(modelId)),
                            style = MaterialTheme.typography.titleSmall)
                    }
                    Text(uiText(active?.connection?.label ?: "Setup required"),
                        style = MaterialTheme.typography.labelSmall,
                        color = scheme.onSurfaceVariant)
                }
                if (!modelId.isNullOrBlank()) {
                    TextButton(onClick = { modelDetailsExpanded = !modelDetailsExpanded },
                        modifier = Modifier.heightIn(min = 48.dp)) {
                        Text(uiText(if (modelDetailsExpanded) "Hide technical details" else "Details"))
                    }
                    if (modelDetailsExpanded) {
                        Text(uiText("Model identifier") + ": " + modelId,
                            style = MaterialTheme.typography.bodySmall,
                            color = scheme.onSurfaceVariant,
                            maxLines = 4, overflow = TextOverflow.Ellipsis)
                    }
                }
                OutlinedButton(
                    onClick = if (active?.configured == true) onBrowseModels else onProviderSetup,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                ) { Text(uiText(if (active?.configured == true) "Change model" else "Set up provider")) }
                TextButton(onClick = { locationsExpanded = !locationsExpanded },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Text(uiText(if (locationsExpanded) "Hide choices" else "Change location"))
                }
                if (locationsExpanded) {
                    Text(stringResource(R.string.brand_intelligence_select_help),
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant)
                    IntelligenceLocationCard(
                        title = stringResource(R.string.brand_intelligence_phone),
                        subtitle = stringResource(R.string.brand_intelligence_phone_summary),
                        glyph = HaloGlyph.LOCAL,
                        current = currentLocation == AiExecutionLocation.ON_DEVICE,
                        recommended = true,
                        detail = local?.let { uiText(it.connection.label) },
                        onOpen = onDevice,
                    )
                    IntelligenceLocationCard(
                        title = stringResource(R.string.brand_intelligence_network),
                        subtitle = stringResource(R.string.brand_intelligence_network_summary),
                        glyph = HaloGlyph.WIFI,
                        current = currentLocation == AiExecutionLocation.LOCAL_NETWORK,
                        detail = network.firstOrNull { it.configured }?.descriptor?.displayName
                            ?: stringResource(R.string.brand_intelligence_not_configured),
                        onOpen = onProviderSetup,
                    )
                    IntelligenceLocationCard(
                        title = stringResource(R.string.brand_intelligence_cloud),
                        subtitle = stringResource(R.string.brand_intelligence_cloud_summary),
                        glyph = HaloGlyph.CLOUD,
                        current = currentLocation == AiExecutionLocation.REMOTE,
                        detail = cloud.firstOrNull { it.selected }?.descriptor?.displayName
                            ?: stringResource(R.string.brand_intelligence_not_configured),
                        onOpen = onProviderSetup,
                    )
                    Text(stringResource(R.string.brand_intelligence_no_auto_switch),
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant)
                }
            }
        }
        item(key = "next") {
            IntelligenceGuidance(step, onProviderSetup, onBrowseModels,
                { active?.let { control.testConnection(it.descriptor.providerId) } },
                { active?.let { checkGeneration(it.descriptor.providerId) } })
        }
        // Round 2: these are daily destinations, never buried behind an Advanced switch.
        item(key = "models_and_connections") {
            HaloSettingsSection(uiText("Models and connections")) {
                HaloWorkspaceDestination(uiText("Providers"),
                    uiText("%1\$d configured providers", rows.count { it.configured }), onProviderSetup)
                HaloWorkspaceDestination(uiText("Model library"),
                    uiText("Browse available models"), onBrowseModels)
            }
        }
        item(key = "context") {
            HaloSettingsSection(uiText("Preferences")) {
                HaloWorkspaceDestination(uiText("Context & privacy"),
                    uiText("Choose what Halo can use"), onContext)
                HaloWorkspaceDestination(uiText("Model roles"),
                    uiText("Route different kinds of work"), onRoles)
            }
        }
        item(key = "advanced_toggle") {
            OutlinedButton(onClick = { advanced = !advanced },
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text(stringResource(if (advanced) R.string.brand_intelligence_hide_tools
                    else R.string.brand_intelligence_advanced_tools))
            }
        }
        if (advanced) item(key = "workload_diagnostics") { WorkloadDiagnosticsPanel(control.router) }
        if (advanced) item(key = "advanced_tools") {
            HaloSettingsSection(uiText("Model diagnostics")) {
                HaloWorkspaceDestination(uiText("Vision Lab"),
                    uiText("Verify and compare image models"), onVisionLab)
                HaloWorkspaceDestination(uiText("Local audio test"),
                    uiText("Try an audio file with an installed model"), onAudioLab)
                HaloWorkspaceDestination(uiText("System check"),
                    uiText("Check what works and share a safe report"), onDiagnostics)
            }
        }
    }
}

/** Derived ONLY from the selected provider's actual execution location.
 * An unconfigured selection never inherits the suggested on-device location.
 */
internal fun intelligenceLocationLabel(location: AiExecutionLocation?): String = when (location) {
    AiExecutionLocation.ON_DEVICE -> "On this phone"
    AiExecutionLocation.LOCAL_NETWORK -> "On your network"
    AiExecutionLocation.REMOTE -> "In the cloud"
    null -> "No location selected"
}

/** The raw provider model id is a technical detail, not an everyday user-facing label. */
internal fun intelligenceModelSummary(modelId: String?): String =
    if (modelId.isNullOrBlank()) "No model selected" else "Model selected"
