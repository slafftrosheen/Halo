package com.halo.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.halo.app.ui.developer.console.ConsoleUiState
import com.halo.app.ui.developer.console.ConsoleViewModel
import com.halo.app.ui.glass.components.HaloGlassBackHeader
import com.halo.app.ui.glass.components.HaloGlassCard
import com.halo.app.ui.glass.components.HaloGlassChip
import com.halo.app.ui.glass.components.HaloGlassDivider
import com.halo.app.ui.glass.components.HaloGlassLazyPage
import com.halo.app.ui.glass.components.HaloGlassMetric
import com.halo.app.ui.glass.components.HaloGlassSection
import com.halo.app.ui.glass.components.HaloGlassSettingRow
import com.halo.app.ui.glass.components.HaloStateOrb
import com.halo.app.ui.glass.material.HaloGlassColors
import com.halo.app.ui.glass.material.HaloGlassMaterials
import com.halo.app.ui.glass.material.HaloTypography
import com.halo.app.ui.state.label
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Runtime observability surface.
 *
 * Every value here is projected from [ConsoleUiState], which is mapped from the
 * live RuntimeSnapshot. Tapping the orb dispatches a real listening command to
 * the runtime; the presence that comes back is whatever the pipeline reports.
 */
@Composable
fun HaloConsoleScreen(
    viewModel: ConsoleViewModel,
    onBack: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()

    HaloGlassLazyPage(title = "") {
        item { HaloGlassBackHeader(title = "Halo Console", onBack = onBack) }
        item { Spacer(Modifier.height(16.dp)) }

        item { PresenceCard(uiState = uiState, onOrbClick = viewModel::toggleListening) }
        item { Spacer(Modifier.height(20.dp)) }

        item {
            HaloGlassSection(title = "RUNTIME") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        HaloGlassMetric(
                            label = "State",
                            value = uiState.runtimeState.ifBlank { "Unknown" },
                            modifier = Modifier.weight(1f),
                            color = if (uiState.healthy) HaloGlassColors.Success else HaloGlassColors.Warning
                        )
                        HaloGlassMetric(
                            label = "Uptime",
                            value = uiState.uptime,
                            modifier = Modifier.weight(1f),
                            color = HaloGlassColors.TextPrimary
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        HaloGlassMetric(
                            label = "Memory",
                            value = uiState.memoryUsage,
                            modifier = Modifier.weight(1f),
                            color = HaloGlassColors.TextPrimary
                        )
                        HaloGlassMetric(
                            label = "Threads",
                            value = uiState.threadCount.toString(),
                            modifier = Modifier.weight(1f),
                            color = HaloGlassColors.TextPrimary
                        )
                    }
                    if (uiState.degraded) {
                        Text(
                            text = "Runtime reports a degraded condition.",
                            style = HaloTypography.bodySmall,
                            color = HaloGlassColors.Warning
                        )
                    }
                }
            }
        }
        item { Spacer(Modifier.height(20.dp)) }

        item {
            HaloGlassSection(
                title = "ENGINES",
                trailing = {
                    Text(
                        text = "${uiState.engines.count { it.isHealthy }}/${uiState.engines.size} healthy",
                        style = HaloTypography.labelSmall,
                        color = HaloGlassColors.TextMuted
                    )
                }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                ) {
                    if (uiState.engines.isEmpty()) {
                        EmptyNote("No engines registered with the kernel.")
                    } else {
                        uiState.engines.forEach { engine ->
                            HaloGlassSettingRow(
                                title = engine.name,
                                value = engine.latencyMs?.let { "${it}ms · queue ${engine.queueSize}" }
                                    ?: "queue ${engine.queueSize}",
                                trailingText = if (engine.isHealthy) "Healthy" else "Unhealthy"
                            )
                        }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(20.dp)) }

        item {
            HaloGlassSection(
                title = "PROVIDERS",
                trailing = {
                    Text(
                        text = "${uiState.providers.size}",
                        style = HaloTypography.labelSmall,
                        color = HaloGlassColors.TextMuted
                    )
                }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                ) {
                    if (uiState.providers.isEmpty()) {
                        EmptyNote("No providers published in the snapshot.")
                    } else {
                        uiState.providers.forEach { provider ->
                            HaloGlassSettingRow(
                                title = provider.name,
                                value = provider.activeModel ?: provider.id,
                                trailingText = provider.status
                            )
                        }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(20.dp)) }

        item {
            HaloGlassSection(title = "NATIVE RESOURCES") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                ) {
                    if (uiState.nativeResources.isEmpty()) {
                        EmptyNote("No heavyweight native model is registered.")
                    } else {
                        uiState.nativeResources.forEach { resource ->
                            HaloGlassSettingRow(
                                title = resource.kind.label(),
                                value = resource.reason ?: resource.ownerId,
                                trailingText = resource.state.label()
                            )
                        }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(20.dp)) }

        item {
            HaloGlassSection(
                title = "EVENTS",
                trailing = {
                    Text(
                        text = "${uiState.events.size}",
                        style = HaloTypography.labelSmall,
                        color = HaloGlassColors.TextMuted
                    )
                }
            ) {
                if (uiState.events.isEmpty()) {
                    EmptyNote("No timeline events recorded yet.")
                }
            }
        }

        if (uiState.events.isNotEmpty()) {
            items(uiState.events, key = { it.id }) { event ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = event.title,
                            style = HaloTypography.bodyMedium,
                            color = HaloGlassColors.TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = formatClock(event.timestampMs),
                            style = HaloTypography.labelSmall,
                            color = HaloGlassColors.TextMuted
                        )
                    }
                    if (event.description.isNotBlank()) {
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = event.description,
                            style = HaloTypography.bodySmall,
                            color = HaloGlassColors.TextSecondary
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    HaloGlassChip(
                        text = event.status,
                        material = HaloGlassMaterials.Interactive.copy(
                            containerColor = statusColor(event.status).copy(alpha = 0.14f),
                            contentColor = statusColor(event.status)
                        )
                    )
                    Spacer(Modifier.height(10.dp))
                    HaloGlassDivider()
                }
            }
        }

        item { Spacer(Modifier.height(20.dp)) }

        item {
            HaloGlassSection(
                title = "LOGS",
                trailing = {
                    Text(
                        text = "${uiState.logs.size}",
                        style = HaloTypography.labelSmall,
                        color = HaloGlassColors.TextMuted
                    )
                }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                ) {
                    if (uiState.logs.isEmpty()) {
                        EmptyNote("No events recorded on the event bus.")
                    } else {
                        uiState.logs.takeLast(50).reversed().forEach { log ->
                            Text(
                                text = log,
                                style = HaloTypography.monoSmall,
                                color = HaloGlassColors.TextSecondary,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(20.dp)) }

        item {
            HaloGlassSection(
                title = "BLACKBOARD",
                trailing = {
                    Text(
                        text = "${uiState.blackboardFacts.size}",
                        style = HaloTypography.labelSmall,
                        color = HaloGlassColors.TextMuted
                    )
                }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                ) {
                    if (uiState.blackboardFacts.isEmpty()) {
                        EmptyNote("Blackboard is empty.")
                    } else {
                        uiState.blackboardFacts.reversed().forEach { fact ->
                            Text(
                                text = fact,
                                style = HaloTypography.monoSmall,
                                color = HaloGlassColors.TextPrimary,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }
        }

        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun PresenceCard(uiState: ConsoleUiState, onOrbClick: () -> Unit) {
    HaloGlassCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            HaloStateOrb(
                state = uiState.presence,
                size = 96.dp,
                modifier = Modifier.clickable(onClick = onOrbClick)
            )
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = uiState.presence.name,
                    style = HaloTypography.titleMedium,
                    color = HaloGlassColors.TextPrimary
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Projected from the runtime speech pipeline.",
                    style = HaloTypography.bodySmall,
                    color = HaloGlassColors.TextMuted
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    HaloGlassChip(
                        text = uiState.runtimeState.ifBlank { "Unknown" },
                        material = HaloGlassMaterials.Interactive.copy(
                            containerColor = HaloGlassColors.Glass,
                            contentColor = HaloGlassColors.TextSecondary
                        )
                    )
                    HaloGlassChip(
                        text = when {
                            !uiState.healthy -> "Unhealthy"
                            uiState.degraded -> "Degraded"
                            else -> "Healthy"
                        },
                        material = HaloGlassMaterials.Interactive.copy(
                            containerColor = healthColor(uiState).copy(alpha = 0.14f),
                            contentColor = healthColor(uiState)
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyNote(text: String) {
    Text(
        text = text,
        style = HaloTypography.bodySmall,
        color = HaloGlassColors.TextMuted,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    )
}

private fun healthColor(uiState: ConsoleUiState): Color = when {
    !uiState.healthy -> HaloGlassColors.Danger
    uiState.degraded -> HaloGlassColors.Warning
    else -> HaloGlassColors.Success
}

private fun statusColor(status: String): Color = when {
    status.equals("Success", ignoreCase = true) -> HaloGlassColors.Success
    status.equals("Failed", ignoreCase = true) ||
        status.equals("Error", ignoreCase = true) -> HaloGlassColors.Danger
    else -> HaloGlassColors.TextSecondary
}

private fun formatClock(timestampMs: Long): String =
    SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(timestampMs))
