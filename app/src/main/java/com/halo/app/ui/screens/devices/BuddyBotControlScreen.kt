package com.halo.app.ui.screens.devices

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.clickable
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.halo.app.ui.glass.components.HaloGlassBackHeader
import com.halo.app.ui.glass.components.HaloGlassButton
import com.halo.app.ui.glass.components.HaloGlassDivider
import com.halo.app.ui.glass.components.HaloGlassLazyPage
import com.halo.app.ui.glass.components.HaloGlassSection
import com.halo.app.ui.glass.components.HaloGlassSettingRow
import com.halo.app.ui.glass.material.HaloGlassColors
import com.halo.app.ui.glass.material.HaloTypography
import java.util.UUID

@Composable
fun BuddyBotControlScreen(
    deviceId: String,
    viewModel: DevicesViewModel,
    onBack: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val device = uiState.devices.find { it.id == deviceId }
    val isConnected = device?.isConnected == true

    HaloGlassLazyPage(title = "") {
        item {
            HaloGlassBackHeader(
                title = device?.name ?: "BuddyBot",
                onBack = onBack
            )
        }

        item { Spacer(Modifier.height(8.dp)) }

        item {
            HaloGlassSection(title = "CONNECTION") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            if (isConnected) viewModel.disconnectDevice(deviceId)
                            else viewModel.connectDevice(deviceId)
                        }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            "Status",
                            style = HaloTypography.bodyLarge,
                            color = HaloGlassColors.TextPrimary
                        )
                        Spacer(Modifier.height(3.dp))
                        Text(
                            device?.connectionStateText ?: "Unknown",
                            style = HaloTypography.bodySmall,
                            color = if (isConnected) HaloGlassColors.Success else HaloGlassColors.TextSecondary
                        )
                    }
                    Text(
                        if (isConnected) "Disconnect" else "Connect",
                        style = HaloTypography.bodyMedium,
                        color = HaloGlassColors.Accent
                    )
                }
                HaloGlassDivider()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { viewModel.toggleAutoConnect(deviceId, !(device?.autoConnect ?: false)) }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Auto-connect", style = HaloTypography.bodyLarge, color = HaloGlassColors.TextPrimary)
                    Text(
                        if (device?.autoConnect == true) "ON" else "OFF",
                        style = HaloTypography.bodyMedium,
                        color = HaloGlassColors.TextSecondary
                    )
                }
                HaloGlassDivider()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { viewModel.forgetDevice(deviceId) }
                        .padding(horizontal = 16.dp, vertical = 14.dp)
                ) {
                    Text("Forget device", style = HaloTypography.bodyLarge, color = HaloGlassColors.Danger)
                }
            }
        }

        item { Spacer(Modifier.height(8.dp)) }

        item {
            HaloGlassSection(title = "MANUAL CONTROL") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    HaloGlassButton(
                        text = "Move Forward",
                        onClick = {
                            viewModel.dispatchEmbodimentIntent(
                                deviceId,
                                com.halo.sdk.runtime.embodiment.EmbodimentIntent(
                                    intentId = UUID.randomUUID().toString(),
                                    targetDeviceId = deviceId,
                                    kind = com.halo.sdk.runtime.embodiment.IntentKind.MOVE,
                                    driveMode = com.halo.sdk.runtime.embodiment.DriveMode.FORWARD
                                )
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = isConnected
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        HaloGlassButton(
                            text = "Stop",
                            onClick = {
                                viewModel.dispatchEmbodimentIntent(
                                    deviceId,
                                    com.halo.sdk.runtime.embodiment.EmbodimentIntent(
                                        intentId = UUID.randomUUID().toString(),
                                        targetDeviceId = deviceId,
                                        kind = com.halo.sdk.runtime.embodiment.IntentKind.STOP
                                    )
                                )
                            },
                            modifier = Modifier.weight(1f),
                            enabled = isConnected
                        )
                        HaloGlassButton(
                            text = "Reverse",
                            onClick = {
                                viewModel.dispatchEmbodimentIntent(
                                    deviceId,
                                    com.halo.sdk.runtime.embodiment.EmbodimentIntent(
                                        intentId = UUID.randomUUID().toString(),
                                        targetDeviceId = deviceId,
                                        kind = com.halo.sdk.runtime.embodiment.IntentKind.MOVE,
                                        driveMode = com.halo.sdk.runtime.embodiment.DriveMode.REVERSE
                                    )
                                )
                            },
                            modifier = Modifier.weight(1f),
                            enabled = isConnected
                        )
                    }
                }
            }
        }

        item { Spacer(Modifier.height(8.dp)) }

        item {
            HaloGlassSection(title = "DEVICE STATE") {
                HaloGlassSettingRow(
                    title = "Battery",
                    value = device?.battery ?: "Unknown"
                )
                HaloGlassDivider()
                HaloGlassSettingRow(
                    title = "Firmware",
                    value = device?.firmware ?: "Unknown"
                )
                HaloGlassDivider()
                HaloGlassSettingRow(
                    title = "Last seen",
                    value = device?.lastSeen ?: "Never"
                )
                HaloGlassDivider()
                HaloGlassSettingRow(
                    title = "Health",
                    value = device?.healthStateText ?: "Unknown"
                )
            }
        }

        item { Spacer(Modifier.height(24.dp)) }
    }
}
