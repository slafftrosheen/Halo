package com.halo.app.ui.screens.discovery

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.halo.app.ui.glass.components.*
import com.halo.app.ui.glass.material.HaloGlassColors
import com.halo.app.ui.glass.material.HaloTypography
import com.halo.sdk.host.ConnectionState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PairingScreen(viewModel: PairingViewModel) {
    val uiState by viewModel.uiState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(HaloGlassColors.Background)
            .padding(16.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { viewModel.navigateBack() }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = HaloGlassColors.TextPrimary)
            }
            Spacer(Modifier.width(8.dp))
            Text("Pair Glasses", style = HaloTypography.titleLarge, color = HaloGlassColors.TextPrimary)
        }

        Spacer(Modifier.height(32.dp))

        Text(
            "Select your HeyCyan glasses from the list below to begin pairing.",
            style = HaloTypography.bodyMedium,
            color = HaloGlassColors.TextSecondary
        )

        Spacer(Modifier.height(24.dp))

        // Scanning Status
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                if (uiState.isScanning) "Searching..." else "Discovery Idle",
                style = HaloTypography.labelLarge,
                color = if (uiState.isScanning) HaloGlassColors.Accent else HaloGlassColors.TextMuted
            )
            HaloGlassButton(
                text = if (uiState.isScanning) "Stop" else "Scan",
                onClick = { if (uiState.isScanning) viewModel.stopScan() else viewModel.startScan() }
            )
        }

        Spacer(Modifier.height(24.dp))

        val isActive = uiState.connectionState is ConnectionState.Connected || uiState.connectionState is ConnectionState.Ready

        // Device List
        Box(modifier = Modifier.weight(1f)) {
            if (uiState.devices.isEmpty() && !uiState.isScanning && !isActive) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = uiState.statusMessage ?: "No BLE devices found",
                        color = if (uiState.statusMessage?.contains("fail", ignoreCase = true) == true) HaloGlassColors.Danger else HaloGlassColors.TextMuted
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (isActive) {
                        item {
                            Text("Active Device", style = HaloTypography.labelMedium, color = HaloGlassColors.TextSecondary)
                            Spacer(Modifier.height(8.dp))
                        }
                    }

                    items(uiState.devices, key = { it.advertisement.address }) { device ->
                        val isSelected = uiState.selectedAddress == device.advertisement.address
                        HaloGlassCard(
                            onClick = { viewModel.selectDevice(device) },
                            material = if (isSelected) com.halo.app.ui.glass.material.HaloGlassMaterials.Interactive.copy(
                                containerColor = HaloGlassColors.Accent.copy(alpha = 0.2f)
                            ) else com.halo.app.ui.glass.material.HaloGlassMaterials.Standard
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(device.advertisement.name ?: "Unknown Glasses", color = HaloGlassColors.TextPrimary, style = HaloTypography.bodyLarge)
                                    Text(device.advertisement.address, color = HaloGlassColors.TextMuted, style = HaloTypography.labelSmall)
                                }
                                if (isSelected) {
                                    Text("SELECTED", color = HaloGlassColors.Accent, style = HaloTypography.labelLarge)
                                }
                            }
                        }
                    }
                }
            }
        }

        // Action Footer
        Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val showStatusInFooter = uiState.devices.isNotEmpty() || uiState.isScanning || isActive
            val currentStatusMsg = uiState.statusMessage
            if (showStatusInFooter && currentStatusMsg != null) {
                Text(currentStatusMsg, color = HaloGlassColors.Info, style = HaloTypography.bodySmall)
                Spacer(Modifier.height(12.dp))
            }

            HaloGlassButton(
                text = "Connect Glasses",
                onClick = { viewModel.connect() },
                enabled = uiState.selectedAddress != null && (uiState.connectionState is ConnectionState.Disconnected || uiState.connectionState is ConnectionState.Failed),
                modifier = Modifier.fillMaxWidth()
            )
            
            if (uiState.connectionState is ConnectionState.Ready) {
                Spacer(Modifier.height(8.dp))
                Text("Device is active", color = HaloGlassColors.Success, style = HaloTypography.labelLarge)
            } else if (uiState.connectionState is ConnectionState.Connected) {
                Spacer(Modifier.height(8.dp))
                Text("Connected (Initializing...)", color = HaloGlassColors.Info, style = HaloTypography.labelLarge)
            }
        }
    }
}
