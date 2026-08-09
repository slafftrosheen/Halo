package com.halo.app.ui.screens.discovery

import android.content.Context
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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
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
import com.halo.drivers.heycyan.HeyCyanDiscoverySession
import com.halo.drivers.heycyan.ble.DiscoveredBleDevice
import com.halo.sdk.driver.DriverState
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@Composable
fun HeyCyanDiscoveryScreen(context: Context) {
    val session = remember(context) { HeyCyanDiscoverySession(context) }
    val scope = rememberCoroutineScope()
    val devices = remember { mutableStateListOf<DiscoveredBleDevice>() }
    val selectedAddress by session.selectedAddress.collectAsState()
    val state by session.state.collectAsState()
    var scanJob by remember { mutableStateOf<Job?>(null) }
    var message by remember { mutableStateOf<String?>(null) }

    DisposableEffect(session) {
        onDispose {
            scanJob?.cancel()
            scope.launch { session.shutdown() }
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("BLE PROTOCOL DISCOVERY", style = MaterialTheme.typography.headlineSmall)
        Text("Raw BLE evidence collection. The selected device is not assumed to be HeyCyan. No protocol commands are sent.")

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                devices.clear()
                message = "Scanning..."
                scanJob?.cancel()
                scanJob = scope.launch {
                    runCatching {
                        session.scan().collect { device ->
                            if (devices.none { it.advertisement.address == device.advertisement.address }) {
                                devices += device
                            }
                        }
                    }.onFailure { message = it.message }
                }
            }) { Text("Scan") }
            Button(
                enabled = selectedAddress != null && state is DriverState.Disconnected,
                onClick = {
                    scope.launch {
                        runCatching { session.connectSelected() }
                            .onSuccess { message = "Connected; GATT discovery and subscriptions complete." }
                            .onFailure { message = it.message }
                    }
                }
            ) { Text("Connect") }
            Button(
                enabled = state != DriverState.Disconnected,
                onClick = {
                    scope.launch {
                        session.disconnect()
                        message = "Capture saved under captures/heycyan."
                    }
                }
            ) { Text("Save Capture") }
        }

        Text("State: ${state.label()}")
        message?.let { Text(it, color = MaterialTheme.colorScheme.secondary) }
        Spacer(Modifier.height(4.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(devices, key = { it.advertisement.address }) { device ->
                val advertisement = device.advertisement
                Card(
                    onClick = { session.select(device) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text(
                            advertisement.name ?: "Unknown BLE device",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(advertisement.address)
                        Text("RSSI ${advertisement.rssi} dBm")
                        if (selectedAddress == advertisement.address) {
                            Text("Selected", color = MaterialTheme.colorScheme.primary)
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
