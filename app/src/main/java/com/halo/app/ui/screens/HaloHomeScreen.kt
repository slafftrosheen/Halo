package com.halo.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.halo.app.ui.components.HaloOrb
import com.halo.app.ui.components.HaloPresenceState
import com.halo.app.ui.components.HaloPresenceUi
import com.halo.app.ui.theme.HaloBackground
import com.halo.app.ui.theme.HaloCard
import com.halo.app.ui.theme.HaloGlassHighlight
import com.halo.app.ui.theme.HaloTextMuted
import com.halo.app.ui.theme.HaloTextPrimary
import com.halo.app.ui.theme.HaloTextSecondary

data class DeviceTile(
    val id: String,
    val name: String,
    val location: String,
    val isOnline: Boolean,
    val isActive: Boolean
)

@Composable
fun HaloHomeScreen(
    modifier: Modifier = Modifier,
    devices: List<DeviceTile>,
    initialPresenceState: HaloPresenceState = HaloPresenceState.Idle,
    onDeviceTap: (DeviceTile) -> Unit = {},
    onOrbTap: (HaloPresenceState) -> Unit = {}
) {
    var presenceState by remember { mutableStateOf(initialPresenceState) }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = HaloBackground
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            HaloGlassHighlight,
                            Color.Transparent
                        ),
                        center = Offset.Zero,
                        radius = 600f
                    )
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Top app bar / context
                HaloHomeHeader()

                Spacer(modifier = Modifier.height(12.dp))

                // Orb + headline copy
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1.3f, fill = true),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 12.dp),
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "Halo is ready.",
                            style = MaterialTheme.typography.headlineMedium,
                            color = HaloTextPrimary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Your home devices, scenes and automations are listening.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = HaloTextSecondary
                        )
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .size(260.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        HaloOrb(
                            presence = HaloPresenceUi(
                                state = presenceState,
                                isInteractive = true,
                                primaryLabel = "Halo presence"
                            ),
                            onTap = {
                                // Simple demo cycle through states.
                                presenceState = nextPresenceState(presenceState)
                                onOrbTap(presenceState)
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Device strip
                Text(
                    text = "Devices",
                    style = MaterialTheme.typography.titleMedium,
                    color = HaloTextSecondary
                )

                Spacer(modifier = Modifier.height(8.dp))

                DeviceStrip(
                    devices = devices,
                    modifier = Modifier.weight(1f, fill = false),
                    onDeviceTap = onDeviceTap
                )
            }
        }
    }
}

private fun nextPresenceState(current: HaloPresenceState): HaloPresenceState {
    return when (current) {
        HaloPresenceState.Idle -> HaloPresenceState.Listening
        HaloPresenceState.Listening -> HaloPresenceState.Thinking
        HaloPresenceState.Thinking -> HaloPresenceState.Acting // Mapped speaking to acting
        HaloPresenceState.Acting -> HaloPresenceState.Error
        HaloPresenceState.Error -> HaloPresenceState.Idle
    }
}

@Composable
private fun HaloHomeHeader() {
    Row(
        modifier = Modifier
            .fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = "Halo",
                style = MaterialTheme.typography.titleLarge,
                color = HaloTextPrimary
            )
            Text(
                text = "Local brain for your smart gadgets",
                style = MaterialTheme.typography.bodySmall,
                color = HaloTextMuted
            )
        }

        // Right side secondary info (clock, profile, diagnostics badge, etc.)
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = "All systems nominal",
                style = MaterialTheme.typography.bodySmall,
                color = HaloTextSecondary
            )
        }
    }
}

@Composable
private fun DeviceStrip(
    devices: List<DeviceTile>,
    modifier: Modifier = Modifier,
    onDeviceTap: (DeviceTile) -> Unit
) {
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        items(devices, key = { it.id }) { device ->
            DeviceCard(device = device, onTap = { onDeviceTap(device) })
        }
    }
}

@Composable
private fun DeviceCard(
    device: DeviceTile,
    onTap: () -> Unit
) {
    Surface(
        modifier = Modifier
            .size(width = 180.dp, height = 120.dp)
            .padding(vertical = 4.dp),
        color = HaloCard,
        contentColor = HaloTextPrimary,
        tonalElevation = 4.dp,
        onClick = { onTap() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = device.name,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = device.location,
                style = MaterialTheme.typography.bodySmall,
                color = HaloTextSecondary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = when {
                    !device.isOnline -> "Offline"
                    device.isActive -> "Active"
                    else -> "Ready"
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (!device.isOnline) HaloTextMuted else HaloTextSecondary
            )
        }
    }
}
