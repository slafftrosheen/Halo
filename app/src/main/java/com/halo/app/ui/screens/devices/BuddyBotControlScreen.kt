package com.halo.app.ui.screens.devices

import androidx.compose.foundation.layout.*
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
fun BuddyBotControlScreen(viewModel: DevicesViewModel, onBack: () -> Unit = {}) {
    val uiState by viewModel.uiState.collectAsState()
    
    val device = uiState.devices.find { it.id == "buddybot_1" }
    val isConnected = device?.isConnected == true

    HaloGlassLazyPage(title = "") {
        item {
            HaloGlassBackHeader(
                title = "BuddyBot",
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
                HaloGlassSettingRow(title = "Transport", value = "TCP/IP")
                HaloGlassDivider()
                HaloGlassSettingRow(title = "Forget", value = "")
                HaloGlassDivider()
                HaloGlassSettingRow(title = "Status", value = if (isConnected) "Connected" else "Offline")
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
                        viewModel.dispatchEmbodimentIntent(
                            com.halo.sdk.runtime.embodiment.EmbodimentIntent(
                                intentId = java.util.UUID.randomUUID().toString(),
                                targetDeviceId = "buddybot_1",
                                kind = com.halo.sdk.runtime.embodiment.IntentKind.MOVE,
                                driveMode = com.halo.sdk.runtime.embodiment.DriveMode.FORWARD
                            )
                        )
                    },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
                )
                
                com.halo.app.ui.glass.components.HaloGlassButton(
                    text = "STOP",
                    onClick = {
                        viewModel.dispatchEmbodimentIntent(
                            com.halo.sdk.runtime.embodiment.EmbodimentIntent(
                                intentId = java.util.UUID.randomUUID().toString(),
                                targetDeviceId = "buddybot_1",
                                kind = com.halo.sdk.runtime.embodiment.IntentKind.STOP
                            )
                        )
                    },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
        }

        item { Spacer(modifier = Modifier.height(16.dp)) }

        item {
            HaloGlassSection(
                title = "Configuration",
                modifier = Modifier.fillMaxWidth()
            ) {
                HaloGlassSettingRow(title = "Hardware profile", value = "Standard")
                HaloGlassDivider()
                HaloGlassSettingRow(title = "Sensors", value = "Calibrated")
                HaloGlassDivider()
                HaloGlassSettingRow(title = "Motion", value = "Smooth")
                HaloGlassDivider()
                HaloGlassSettingRow(title = "Accessories", value = "None")
            }
        }

        item { Spacer(modifier = Modifier.height(16.dp)) }

        item {
            HaloGlassSection(
                title = "Safety",
                modifier = Modifier.fillMaxWidth()
            ) {
                HaloGlassSettingRow(title = "Current safety state", value = if (isConnected) "ARMED" else "UNKNOWN")
                HaloGlassDivider()
                HaloGlassSettingRow(title = "Range sensor status", value = "CLEAR")
                HaloGlassDivider()
                HaloGlassSettingRow(title = "Motion limits", value = "ACTIVE")
                HaloGlassDivider()
                HaloGlassSettingRow(title = "Safety diagnostics", value = "OK")
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
                HaloGlassDivider()
                HaloGlassSettingRow(title = "Runtime state", value = "Nominal")
                HaloGlassDivider()
                HaloGlassSettingRow(title = "Sensors", value = "Active")
                HaloGlassDivider()
                HaloGlassSettingRow(title = "Logs", value = ">")
            }
        }
        
        item { Spacer(modifier = Modifier.height(32.dp)) }
    }
}
