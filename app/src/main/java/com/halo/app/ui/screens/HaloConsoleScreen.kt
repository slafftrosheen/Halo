package com.halo.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.halo.app.ui.components.ConsoleRow
import com.halo.app.ui.components.ConsoleSection
import com.halo.app.ui.components.HaloEngineCard
import com.halo.app.ui.components.HaloOrbAdvanced
import com.halo.app.ui.components.HaloPresenceState
import com.halo.app.ui.components.HaloPresenceUi
import com.halo.app.ui.components.HaloProviderCard
import com.halo.app.ui.components.HaloStatusChip
import com.halo.app.ui.components.HaloStatus
import com.halo.app.ui.components.HaloPixelSignal
import com.halo.app.ui.theme.HaloBackground
import com.halo.app.ui.theme.HaloSurface
import com.halo.app.ui.theme.HaloTextMuted
import com.halo.app.ui.theme.HaloTextPrimary
import com.halo.app.ui.theme.HaloTextSecondary
import com.halo.app.ui.theme.HaloDanger
import com.halo.app.ui.theme.HaloInformation
import com.halo.app.ui.theme.HaloPrimary
import com.halo.runtime.kernel.ProcessState

data class EngineStatusUi(
    val name: String,
    val state: ProcessState
)

data class ProviderStatusUi(
    val name: String,
    val status: String,
    val isOnline: Boolean
)

data class ConsoleEventUi(
    val time: String,
    val title: String,
    val description: String
)

@Composable
fun HaloConsoleScreen(
    modifier: Modifier = Modifier,
    engines: List<EngineStatusUi>,
    providers: List<ProviderStatusUi>,
    events: List<ConsoleEventUi>,
    initialPresenceState: HaloPresenceState = HaloPresenceState.Idle,
    onOrbTap: (HaloPresenceState) -> Unit = {},
    onEngineTap: (EngineStatusUi) -> Unit = {},
    onProviderTap: (ProviderStatusUi) -> Unit = {},
    onEventTap: (ConsoleEventUi) -> Unit = {}
) {
    var presenceState by remember { mutableStateOf(initialPresenceState) }

    val presenceUi = HaloPresenceUi(
        state = presenceState,
        primaryLabel = "Halo",
        secondaryLabel = when (presenceState) {
            HaloPresenceState.Idle -> "Standing by"
            HaloPresenceState.Listening -> "Listening"
            HaloPresenceState.Thinking -> "Reasoning"
            HaloPresenceState.Acting -> "Executing routines"
            HaloPresenceState.Error -> "Attention required"
        }
    )

    Surface(
        modifier = modifier.fillMaxSize(),
        color = HaloBackground
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Top band: title + status
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        HaloPixelSignal(modifier = Modifier.padding(end = 8.dp))
                        Text(
                            text = "HALO CONSOLE",
                            style = MaterialTheme.typography.titleMedium,
                            color = HaloTextSecondary
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = presenceUi.secondaryLabel ?: "Ready",
                        style = MaterialTheme.typography.bodySmall,
                        color = HaloTextMuted
                    )
                }

                HaloStatusChip(
                    text = presenceState.name,
                    color = when (presenceState) {
                        HaloPresenceState.Idle -> HaloTextSecondary
                        HaloPresenceState.Listening -> HaloInformation
                        HaloPresenceState.Thinking -> HaloInformation
                        HaloPresenceState.Acting -> HaloPrimary
                        HaloPresenceState.Error -> HaloDanger
                    }
                )
            }

            // Middle band: orb + metrics
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(260.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .weight(1.1f),
                    contentAlignment = Alignment.Center
                ) {
                    HaloOrbAdvanced(
                        presence = presenceUi,
                        onTap = {
                            presenceState = orbitPresenceCycle(presenceState)
                            onOrbTap(presenceState)
                        }
                    )
                }

                ConsoleSection(
                    title = "RUNTIME METRICS"
                ) {
                    ConsoleRow(label = "Engines", value = engines.size.toString())
                    ConsoleRow(
                        label = "Providers",
                        value = providers.count { it.isOnline }.toString()
                    )
                    ConsoleRow(
                        label = "Events (last 5 min)",
                        value = events.size.toString()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    HaloStatus(
                        label = "Overall health",
                        statusText = overallHealthText(engines),
                        isHealthy = engines.none { it.state == ProcessState.Crashed }
                    )
                }
            }

            // Bottom band: engines + providers + timeline
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                ConsoleSection(title = "ENGINES") {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(engines, key = { it.name }) { engine ->
                            // Tap uses your existing HaloEngineCard semantics
                            Box(modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onEngineTap(engine) }
                            ) {
                                HaloEngineCard(name = engine.name, state = engine.state)
                            }
                        }
                    }
                }

                ConsoleSection(title = "PROVIDERS") {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(providers, key = { it.name }) { provider ->
                            Box(modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onProviderTap(provider) }
                            ) {
                                HaloProviderCard(
                                    name = provider.name,
                                    status = provider.status,
                                    isOnline = provider.isOnline
                                )
                            }
                        }
                    }
                }
            }

            ConsoleSection(title = "EVENTS TIMELINE") {
                if (events.isEmpty()) {
                    Text(
                        text = "No recent events.",
                        style = MaterialTheme.typography.bodySmall,
                        color = HaloTextMuted
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(events, key = { it.time + it.title }) { event ->
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onEventTap(event) }
                            ) {
                                TimelineRow(event)
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun orbitPresenceCycle(current: HaloPresenceState): HaloPresenceState {
    return when (current) {
        HaloPresenceState.Idle -> HaloPresenceState.Listening
        HaloPresenceState.Listening -> HaloPresenceState.Thinking
        HaloPresenceState.Thinking -> HaloPresenceState.Acting
        HaloPresenceState.Acting -> HaloPresenceState.Error
        HaloPresenceState.Error -> HaloPresenceState.Idle
    }
}

private fun overallHealthText(engines: List<EngineStatusUi>): String {
    return if (engines.none { it.state == ProcessState.Crashed }) "Nominal" else "Degraded"
}

@Composable
private fun TimelineRow(event: ConsoleEventUi) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = event.title,
                style = MaterialTheme.typography.bodyMedium,
                color = HaloTextPrimary
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = event.description,
                style = MaterialTheme.typography.bodySmall,
                color = HaloTextSecondary
            )
        }
        Text(
            text = event.time,
            style = MaterialTheme.typography.bodySmall,
            color = HaloTextMuted
        )
    }
}
