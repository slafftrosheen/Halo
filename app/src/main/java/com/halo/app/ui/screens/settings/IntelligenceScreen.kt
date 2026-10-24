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
    onVisionLab: () -> Unit = {}, onAudioLab: () -> Unit = {}, onAdvanced: () -> Unit = {},
    onNotebookSearch: () -> Unit = onContext, onUsage: () -> Unit = onContext) {
    val providerId by control.router.selectedProviderId.collectAsStateWithLifecycle()
    val modelId by control.router.selectedModelId.collectAsStateWithLifecycle()
    val rows by control.rows.collectAsStateWithLifecycle()
    val checkGeneration = rememberProviderGenerationCheck(control)
    val active = rows.firstOrNull { it.descriptor.providerId == providerId }
    val location = currentIntelligenceLocation(active)
    val proven = active?.generation == com.halo.app.ai.GenerationProof.PROVEN &&
        !modelId.isNullOrBlank() && active.generationModelId == modelId
    val scheme = MaterialTheme.colorScheme
    val step = intelligenceNextStep(active?.configured == true, active?.connection, !modelId.isNullOrBlank(),
        if (proven) com.halo.app.ai.GenerationProof.PROVEN else active?.generation?.takeUnless { it == com.halo.app.ai.GenerationProof.PROVEN })
    LazyColumn(Modifier.fillMaxSize().background(scheme.background),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)) {
        item { HaloScreenHeader(uiText("How halo answers"), navigation = onBack) }
        item {
            Surface(shape = com.halo.app.ui.design.HaloShapes.card, color = scheme.surfaceContainerLow,
                border = BorderStroke(1.dp, scheme.outlineVariant)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        HaloIcon(when (location) {
                            AiExecutionLocation.ON_DEVICE -> HaloGlyph.LOCAL
                            AiExecutionLocation.LOCAL_NETWORK -> HaloGlyph.WIFI
                            AiExecutionLocation.REMOTE -> HaloGlyph.CLOUD
                            null -> HaloGlyph.MODEL
                        }, null, Modifier.size(32.dp), tint = scheme.primary)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(uiText(intelligenceLocationLabel(location)), style = MaterialTheme.typography.titleMedium)
                            Text(uiText(when {
                                active == null || !active.configured -> "Choose where answers come from"
                                proven -> "Everyday answers · you tested it"
                                active.generation == com.halo.app.ai.GenerationProof.CHECKING -> "Checking answers…"
                                active.generation == com.halo.app.ai.GenerationProof.FAILED -> "Answers need a look"
                                else -> "Everyday answers · not checked yet"
                            }), style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
                        }
                    }
                    Text(uiText(when (location) {
                        AiExecutionLocation.ON_DEVICE -> "This choice answers on your phone"
                        AiExecutionLocation.LOCAL_NETWORK -> "Questions go to your computer at home"
                        AiExecutionLocation.REMOTE -> "Questions go to your chosen cloud service"
                        null -> "You decide what leaves this phone"
                    }), style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                }
            }
        }
        if (!proven) item {
            IntelligenceGuidance(step, onProviderSetup, onBrowseModels,
                { active?.let { control.testConnection(it.descriptor.providerId) } },
                { active?.let { checkGeneration(it.descriptor.providerId) } })
        }
        item {
            AnswerSettingsGroup("Answers") {
                SettingsLink(HaloGlyph.INTEGRATIONS, "Where answers come from",
                    if (active?.configured == true) intelligenceLocationLabel(location) else "Choose a place to start", onProviderSetup, divider = true)
                SettingsLink(HaloGlyph.MODEL, "Choose a model", if (modelId.isNullOrBlank()) "Nothing chosen yet" else "Everyday answers", onBrowseModels, divider = true)
                SettingsLink(HaloGlyph.DOWNLOAD, "Downloads on this phone", "Manage what stays on your phone", onDevice, divider = true)
                SettingsLink(HaloGlyph.SEARCH, stringResource(com.halo.app.R.string.next_action_notebook_search), "Find saved notes on this phone", onNotebookSearch)
            }
        }
        item {
            AnswerSettingsGroup("Photos & privacy") {
                SettingsLink(HaloGlyph.SEE, "Photos and images", "Choose what halo can see and create", onContext, divider = true)
                SettingsLink(HaloGlyph.SECURITY, "What left this phone", "Review what was shared", onUsage)
            }
        }
    }
}

@Composable private fun AnswerSettingsGroup(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(uiText(title).uppercase(), style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 2.dp))
        Surface(shape = com.halo.app.ui.design.HaloShapes.card, color = MaterialTheme.colorScheme.surfaceContainerLow,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) { Column(content = content) }
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
