package com.halo.app.ui.screens.devices

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.halo.app.ui.glass.components.HaloGlassBackHeader
import com.halo.app.ui.glass.components.HaloGlassLazyPage
import com.halo.app.ui.glass.components.HaloGlassSection
import com.halo.app.ui.glass.components.HaloGlassSettingRow
import com.halo.app.ui.glass.components.HaloGlassDivider
import com.halo.app.ui.glass.material.HaloGlassColors
import com.halo.app.ui.glass.material.HaloTypography

@Composable
fun BuddyBotControlScreen(deviceId: String, viewModel: DevicesViewModel, onBack: () -> Unit = {}) {
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

        item { Spacer(modifier = Modifier.height(16.dp)) }

        item {
            HaloGlassSection(
                title = "Connection",
                modifier = Modifier.fillMaxWidth()
            ) {
                HaloGlassSettingRow(title = "Auto-connect", value = "ON")
                HaloGlassDivider()
                HaloGlassSettingRow(title = "Forget", value = "")
                HaloGlassDivider()
                // Provide clickable row to connect/disconnect
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            if (isConnected) {
                                viewModel.disconnectDevice(deviceId)
                            } else {
                                viewModel.connectDevice(deviceId)
                            }
                        }
                        .padding(vertical = 12.dp, horizontal = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Status", style = HaloTypography.bodyLarge, color = HaloGlassColors.TextPrimary)
                    Text(
                        if (isConnected) "Connected (Tap to disconnect)" else "Offline (Tap to connect)", 
                        style = HaloTypography.bodyMedium, 
                        color = if (isConnected) HaloGlassColors.Success else HaloGlassColors.TextSecondary
                    )
                }
            }
        }

        item { Spacer(modifier = Modifier.height(16.dp)) }

        item {
            HaloGlassSection(
                title = "Manual Control",
                modifier = Modifier.fillMaxWidth()
            ) {
                com.halo.app.ui.glass.components.HaloGlassButton(
                    text = "Move Forward",
                    onClick = {
                        if (isConnected) {
                            viewModel.dispatchEmbodimentIntent(
                                deviceId,
                                com.halo.sdk.runtime.embodiment.EmbodimentIntent(
                                    intentId = java.util.UUID.randomUUID().toString(),
                                    targetDeviceId = deviceId,
                                    kind = com.halo.sdk.runtime.embodiment.IntentKind.MOVE,
                                    driveMode = com.halo.sdk.runtime.embodiment.DriveMode.FORWARD
                                )
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
                )
                
                com.halo.app.ui.glass.components.HaloGlassButton(
                    text = "STOP",
                    onClick = {
                        if (isConnected) {
                            viewModel.dispatchEmbodimentIntent(
                                deviceId,
                                com.halo.sdk.runtime.embodiment.EmbodimentIntent(
                                    intentId = java.util.UUID.randomUUID().toString(),
                                    targetDeviceId = deviceId,
                                    kind = com.halo.sdk.runtime.embodiment.IntentKind.STOP
                                )
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
        }

        item { Spacer(modifier = Modifier.height(16.dp)) }

        item {
            HaloGlassSection(
                title = "Diagnostics",
                modifier = Modifier.fillMaxWidth()
            ) {
                HaloGlassSettingRow(title = "Battery", value = device?.battery ?: "Unknown")
                HaloGlassDivider()
                HaloGlassSettingRow(title = "Firmware", value = device?.firmware ?: "Unknown")
            }
        }
        
        item { Spacer(modifier = Modifier.height(32.dp)) }
    }
}
