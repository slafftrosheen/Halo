package com.halo.app.ui.screens.discovery

import com.halo.app.language.uiText

import androidx.lifecycle.compose.collectAsStateWithLifecycle

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.halo.app.ui.theme.HaloPastelBlue
import com.halo.app.ui.theme.HaloPastelMint
import com.halo.app.ui.theme.HaloWarning
import com.halo.app.ui.workspace.HaloWorkspaceButton
import com.halo.app.ui.workspace.HaloWorkspaceCard
import com.halo.app.ui.workspace.HaloWorkspaceHeader
import com.halo.sdk.host.ConnectionState

@Composable
fun PairingScreen(
    viewModel: PairingViewModel,
    onRequestBluetoothPermissions: () -> Unit,
    onRequestBluetoothEnable: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    val bluetoothReady = uiState.bluetoothPrerequisite == BluetoothPrerequisite.READY
    val isActive =
        bluetoothReady &&
            (
                uiState.connectionState is ConnectionState.Connected ||
                    uiState.connectionState is ConnectionState.Ready
                )

    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshPrerequisites()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 20.dp, vertical = 14.dp)
    ) {
        HaloWorkspaceHeader(
            title = "Pair glasses",
            subtitle = "Discover and connect supported HeyCyan hardware.",
            onBack = viewModel::navigateBack
        )

        Spacer(Modifier.height(20.dp))

        HaloWorkspaceCard(
            accent = when {
                !bluetoothReady -> HaloWarning
                isActive -> HaloPastelMint
                else -> HaloPastelBlue
            }
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = when (uiState.bluetoothPrerequisite) {
                            BluetoothPrerequisite.PERMISSION_REQUIRED -> "Bluetooth access needed"
                            BluetoothPrerequisite.BLUETOOTH_OFF -> "Bluetooth is off"
                            BluetoothPrerequisite.UNSUPPORTED -> "Bluetooth LE unavailable"
                            BluetoothPrerequisite.READY -> when {
                                isActive -> "Glasses connected"
                                uiState.isScanning -> "Searching nearby"
                                else -> "Ready to scan"
                            }
                        },
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = uiState.statusMessage ?: when (uiState.bluetoothPrerequisite) {
                            BluetoothPrerequisite.PERMISSION_REQUIRED ->
                                "Grant nearby-device access before scanning."
                            BluetoothPrerequisite.BLUETOOTH_OFF ->
                                "Turn on Bluetooth to discover and connect glasses."
                            BluetoothPrerequisite.UNSUPPORTED ->
                                "This Android device does not expose Bluetooth Low Energy."
                            BluetoothPrerequisite.READY -> when {
                                isActive -> "The selected device is available to Halo."
                                uiState.isScanning -> "BLE discovery is active."
                                else -> "Start discovery when the glasses are powered on."
                            }
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                when (uiState.bluetoothPrerequisite) {
                    BluetoothPrerequisite.PERMISSION_REQUIRED -> {
                        HaloWorkspaceButton(
                            text = "Grant access",
                            onClick = onRequestBluetoothPermissions
                        )
                    }
                    BluetoothPrerequisite.BLUETOOTH_OFF -> {
                        HaloWorkspaceButton(
                            text = "Turn on",
                            onClick = onRequestBluetoothEnable
                        )
                    }
                    BluetoothPrerequisite.UNSUPPORTED -> {
                        Text(
                            text = uiText("Unavailable"),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    BluetoothPrerequisite.READY -> {
                        HaloWorkspaceButton(
                            text = if (uiState.isScanning) "Stop" else "Scan",
                            onClick = {
                                if (uiState.isScanning) {
                                    viewModel.stopScan()
                                } else {
                                    viewModel.startScan()
                                }
                            },
                            primary = !uiState.isScanning
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(18.dp))

        Text(
            text = if (isActive) "Available / active" else "Discovered devices",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))

        Box(modifier = Modifier.weight(1f)) {
            if (uiState.devices.isEmpty() && !uiState.isScanning && !isActive) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = uiState.statusMessage ?: when {
                            !bluetoothReady -> uiState.bluetoothPrerequisite.userMessage()
                            else -> "No BLE devices found"
                        },
                        style = MaterialTheme.typography.bodyLarge,
                        color = when {
                            uiState.bluetoothPrerequisite == BluetoothPrerequisite.UNSUPPORTED ->
                                MaterialTheme.colorScheme.error
                            uiState.statusMessage?.contains("fail", ignoreCase = true) == true ->
                                MaterialTheme.colorScheme.error
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(uiState.devices, key = { it.advertisement.address }) { device ->
                        val selected = uiState.selectedAddress == device.advertisement.address
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = { viewModel.selectDevice(device) },
                            enabled = bluetoothReady,
                            shape = RoundedCornerShape(18.dp),
                            color = if (selected) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surface
                            },
                            border = BorderStroke(
                                1.dp,
                                if (selected) {
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
                                } else {
                                    MaterialTheme.colorScheme.outlineVariant
                                }
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = device.advertisement.name ?: "Unknown glasses",
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = if (selected) {
                                            FontWeight.SemiBold
                                        } else {
                                            FontWeight.Normal
                                        },
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = device.advertisement.address,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (selected) {
                                    Text(
                                        text = uiText("Selected"),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.secondary
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        HaloWorkspaceButton(
            text = when {
                !bluetoothReady -> "Resolve Bluetooth first"
                uiState.connectionState is ConnectionState.Ready -> "Device active"
                uiState.connectionState is ConnectionState.Discovering ||
                    uiState.connectionState is ConnectionState.Connecting ||
                    uiState.connectionState is ConnectionState.Connected -> "Initializing…"
                else -> "Connect glasses"
            },
            onClick = viewModel::connect,
            enabled =
                bluetoothReady &&
                    uiState.selectedAddress != null &&
                    (
                        uiState.connectionState is ConnectionState.Disconnected ||
                            uiState.connectionState is ConnectionState.Failed
                        ),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(16.dp))
    }
}
