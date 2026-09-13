package com.halo.app.ui.screens.discovery

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.halo.app.ui.glass.components.HaloGlassButton
import com.halo.app.ui.glass.components.HaloGlassCard
import com.halo.app.ui.glass.material.HaloGlassColors
import com.halo.app.ui.glass.material.HaloGlassMaterials
import com.halo.app.ui.glass.material.HaloTypography
import com.halo.drivers.heycyan.HeyCyanDiscoverySession
import com.halo.drivers.heycyan.CaptureScenario
import com.halo.drivers.heycyan.RealHeyCyanDriver
import com.halo.drivers.heycyan.ble.DiscoveredBleDevice
import com.halo.sdk.driver.DriverState
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@Composable
fun HeyCyanDiscoveryScreen(driver: RealHeyCyanDriver) {
    val session = remember(driver) { HeyCyanDiscoverySession(driver) }
    val scope = rememberCoroutineScope()
    val devices = remember { mutableStateListOf<DiscoveredBleDevice>() }
    val selectedAddress by session.selectedAddress.collectAsState()
    val state by session.state.collectAsState()
    var scanJob by remember { mutableStateOf<Job?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var selectedScenario by remember { mutableStateOf(CaptureScenario.BOOT) }

    DisposableEffect(session) {
        onDispose {
            scanJob?.cancel()
            scope.launch { session.clearSelection() }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(HaloGlassColors.Background)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("BLE PROTOCOL DISCOVERY", style = HaloTypography.headlineMedium, color = HaloGlassColors.TextPrimary)
        Text("Raw BLE evidence collection. The selected device is not assumed to be HeyCyan. No protocol commands are sent.", color = HaloGlassColors.TextSecondary, style = HaloTypography.bodyMedium)

        Text("Scenario: ${selectedScenario.label}", style = HaloTypography.titleMedium, color = HaloGlassColors.TextPrimary)
        Text(selectedScenario.instructions, style = HaloTypography.bodySmall, color = HaloGlassColors.TextSecondary)
        LazyColumn(
            modifier = Modifier.height(110.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(CaptureScenario.entries, key = { it.id }) { scenario ->
                HaloGlassButton(
                    text = scenario.label,
                    enabled = state == DriverState.Disconnected,
                    onClick = { selectedScenario = scenario },
                    material = if (selectedScenario == scenario) HaloGlassMaterials.Interactive.copy(containerColor = HaloGlassColors.Accent.copy(alpha = 0.2f)) else HaloGlassMaterials.Interactive
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            HaloGlassButton(
                text = "Scan",
                onClick = {
                    devices.clear()
                    session.clearSelection()
                    message = "Scanning..."
                    scanJob?.cancel()
                    scanJob = scope.launch {
                        try {
                            session.scan().collect { device ->
                                if (devices.none { it.advertisement.address == device.advertisement.address }) {
                                    devices += device
                                }
                            }
                        } catch (e: kotlinx.coroutines.CancellationException) {
                            throw e
                        } catch (e: Throwable) {
                            message = e.message
                        }
                    }
                }
            )
            HaloGlassButton(
                text = "Connect",
                enabled = selectedAddress != null && state is DriverState.Disconnected,
                onClick = {
                    scope.launch {
                        try {
                            session.connectSelected(
                                scenario = selectedScenario.id,
                                captureId = selectedScenario.id
                            )
                            message = "Connected; GATT discovery and subscriptions complete."
                        } catch (e: kotlinx.coroutines.CancellationException) {
                            throw e
                        } catch (e: Throwable) {
                            message = e.message
                        }
                    }
                }
            )
            HaloGlassButton(
                text = "Save Capture",
                enabled = state != DriverState.Disconnected || selectedAddress != null,
                onClick = {
                    scope.launch {
                        runCatching { session.disconnect() }
                            .onSuccess {
                                message = "Capture saved under captures/heycyan, or discarded if connection did not complete."
                            }
                            .onFailure { message = "Capture could not be saved: ${it.message ?: "unknown error"}" }
                    }
                }
            )
        }

        Text("State: ${state.label()}", color = HaloGlassColors.TextPrimary, style = HaloTypography.bodyMedium)
        message?.let { Text(it, color = HaloGlassColors.Info, style = HaloTypography.bodySmall) }
        Spacer(Modifier.height(4.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(devices, key = { it.advertisement.address }) { device ->
                val advertisement = device.advertisement
                val isSelected = selectedAddress == advertisement.address

                HaloGlassCard(
                    onClick = { session.select(device) },
                    modifier = Modifier.fillMaxWidth(),
                    material = if (isSelected) HaloGlassMaterials.Interactive.copy(
                        containerColor = HaloGlassColors.Accent.copy(alpha = 0.2f)
                    ) else HaloGlassMaterials.Standard
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text(
                            advertisement.name ?: "Unknown BLE device",
                            style = HaloTypography.titleMedium,
                            color = HaloGlassColors.TextPrimary
                        )
                        Text(advertisement.address, color = HaloGlassColors.TextMuted, style = HaloTypography.labelSmall)
                        Text("RSSI ${advertisement.rssi} dBm", color = HaloGlassColors.TextSecondary, style = HaloTypography.bodySmall)
                        if (isSelected) {
                            Text("Selected", color = HaloGlassColors.Accent, style = HaloTypography.labelLarge)
                        }
                    }
                }
            }
        }
    }
}

private fun DriverState.label(): String = when (this) {
    DriverState.Disconnected -> "Disconnected"
    DriverState.Scanning -> "Scanning"
    DriverState.Connecting -> "Connecting"
    DriverState.Connected -> "Connected"
    DriverState.Ready -> "Ready"
    DriverState.Disconnecting -> "Disconnecting"
    is DriverState.Error -> "Error: ${cause.message ?: "unknown"}"
}
