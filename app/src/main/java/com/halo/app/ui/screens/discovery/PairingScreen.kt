package com.halo.app.ui.screens.discovery

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
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.halo.app.ui.theme.HaloPastelBlue
import com.halo.app.ui.theme.HaloPastelMint
import com.halo.app.ui.workspace.HaloWorkspaceButton
import com.halo.app.ui.workspace.HaloWorkspaceCard
import com.halo.app.ui.workspace.HaloWorkspaceHeader
import com.halo.sdk.host.ConnectionState

@Composable
fun PairingScreen(viewModel: PairingViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val isActive =
        uiState.connectionState is ConnectionState.Connected ||
            uiState.connectionState is ConnectionState.Ready

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

        HaloWorkspaceCard(accent = if (isActive) HaloPastelMint else HaloPastelBlue) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = when {
                            isActive -> "Glasses connected"
                            uiState.isScanning -> "Searching nearby"
                            else -> "Ready to scan"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = uiState.statusMessage ?: when {
                            isActive -> "The selected device is available to Halo."
                            uiState.isScanning -> "BLE discovery is active."
                            else -> "Start discovery when the glasses are powered on."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                HaloWorkspaceButton(
                    text = if (uiState.isScanning) "Stop" else "Scan",
                    onClick = {
                        if (uiState.isScanning) viewModel.stopScan() else viewModel.startScan()
                    },
                    primary = !uiState.isScanning
                )
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
                        text = uiState.statusMessage ?: "No BLE devices found",
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (
                            uiState.statusMessage?.contains("fail", ignoreCase = true) == true
                        ) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
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
                            shape = RoundedCornerShape(18.dp),
                            color = if (selected) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surface
                            },
                            border = BorderStroke(
                                1.dp,
                                if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
                                else MaterialTheme.colorScheme.outlineVariant
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
                                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
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
                                        text = "Selected",
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
                uiState.connectionState is ConnectionState.Ready -> "Device active"
                uiState.connectionState is ConnectionState.Connected -> "Initializing…"
                else -> "Connect glasses"
            },
            onClick = viewModel::connect,
            enabled = uiState.selectedAddress != null &&
                (
                    uiState.connectionState is ConnectionState.Disconnected ||
                        uiState.connectionState is ConnectionState.Failed
                ),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(16.dp))
    }
}
