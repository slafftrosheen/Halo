package com.halo.app.ui.screens.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.halo.app.R
import com.halo.app.ui.design.HaloScreenHeader
import com.halo.app.ui.icons.HaloGlyph
import com.halo.app.ui.presence.HaloSignalFace
import com.halo.app.ui.presence.HaloSignalPresentation
import com.halo.app.ui.presence.HaloSignalState
import com.halo.app.ui.state.RuntimeUiProjection
import com.halo.app.ui.workspace.HaloWorkspaceDestination

/**
 * BrandBook v1.6 p.45: Advanced stays away from everyday choices.
 * Displays canonical runtime projections; it never fabricates a green OK state.
 * Reports, exports and checks are handed to existing owners.
 */
@Composable
fun AdvancedSettingsScreen(
    projection: RuntimeUiProjection,
    onBack: () -> Unit,
    onRuntime: () -> Unit,
    onSystemCheck: () -> Unit,
    onStorage: () -> Unit
) {
    SettingsChildZone("Advanced", onBack, listOf(settingsChildLink("onRuntime", "Runtime status", onRuntime), settingsChildLink("onSystemCheck", "System check", onSystemCheck), settingsChildLink("onStorage", "Storage & backup", onStorage)))
    val tasks by projection.taskSummary.collectAsStateWithLifecycle()
    val capabilities by projection.capabilitySummary.collectAsStateWithLifecycle()
    val state = when {
        tasks.waitingCount > 0 -> HaloSignalState.WAITING
        tasks.activeCount > 0 -> HaloSignalState.ACTING
        else -> HaloSignalState.IDLE
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(
        horizontal = 20.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        item(key = "header") {
            if (!com.halo.app.ui.presence.LocalShellHaloOrb.current) HaloScreenHeader(stringResource(R.string.brand_advanced_title),
                stringResource(R.string.brand_advanced_subtitle),
                navigation = onBack, glyph = HaloGlyph.DIAGNOSTICS)
        }
        item(key = "summary") {
            Surface(shape = com.halo.app.ui.design.HaloShapes.card,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
                Row(Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    HaloSignalFace(
                        HaloSignalPresentation(state = state,
                            label = tasks.statusText ?: "Runtime"),
                        size = 88.dp, contentDescription = stringResource(R.string.brand_advanced_title))
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.brand_advanced_right_now),
                            style = MaterialTheme.typography.titleMedium)
                        Text(stringResource(R.string.brand_advanced_task_counts,
                            tasks.activeCount, tasks.waitingCount),
                            style = MaterialTheme.typography.bodyMedium)
                        Text(stringResource(R.string.brand_advanced_capability_counts,
                            capabilities.availableCount, capabilities.needSetupCount),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        tasks.statusText?.let { Text(it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                }
            }
        }
        item(key = "tools") {
            HaloSettingsSection(stringResource(R.string.brand_advanced_tools)) {
                HaloWorkspaceDestination(
                    stringResource(R.string.brand_advanced_runtime),
                    stringResource(R.string.brand_advanced_runtime_summary), onRuntime)
                HaloWorkspaceDestination(
                    stringResource(R.string.brand_advanced_check),
                    stringResource(R.string.brand_advanced_check_summary), onSystemCheck)
                HaloWorkspaceDestination(
                    stringResource(R.string.brand_advanced_storage),
                    stringResource(R.string.brand_advanced_storage_summary), onStorage)
            }
        }
        item(key = "limitations") {
            Text(stringResource(R.string.brand_advanced_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
