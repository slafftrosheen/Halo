package com.halo.app.ui.screens.devices

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import com.halo.app.ui.glass.components.HaloGlassCard
import com.halo.app.ui.glass.components.HaloGlassSection
import com.halo.app.ui.glass.material.HaloGlassColors
import com.halo.app.ui.glass.material.HaloTypography
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import com.halo.sdk.runtime.RuntimeSnapshotSource
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import com.halo.sdk.driver.DeviceCapabilities as SdkDeviceCapabilities

@Composable
fun DevicesScreen(viewModel: DevicesViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = "GLASSES",
                style = HaloTypography.labelLarge,
                color = HaloGlassColors.TextSecondary,
                letterSpacing = 2.sp
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .background(
                            if (uiState.isConnected) HaloGlassColors.Accent else HaloGlassColors.Danger,
                            androidx.compose.foundation.shape.CircleShape
                        )
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = uiState.deviceName,
                    style = HaloTypography.titleLarge,
                    color = HaloGlassColors.TextPrimary
                )
            }
        }
        
        item { Spacer(modifier = Modifier.height(32.dp)) }

        if (!uiState.isConnected) {
            item {
                Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                    Text(
                        text = "No active glasses",
                        color = HaloGlassColors.TextMuted,
                        style = HaloTypography.bodyLarge
                    )
                }
            }
            item {
                com.halo.app.ui.glass.components.HaloGlassButton(
                    text = "Find glasses",
                    onClick = { viewModel.navigateTo("discovery") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        } else {
            item {
                HaloGlassSection(
                    title = "Device Status",
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Battery", style = HaloTypography.bodyMedium, color = HaloGlassColors.TextSecondary)
                        Text(uiState.battery, style = HaloTypography.bodyMedium, color = HaloGlassColors.TextPrimary)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Connection", style = HaloTypography.bodyMedium, color = HaloGlassColors.TextSecondary)
                        Text(uiState.connectionType, style = HaloTypography.bodyMedium, color = HaloGlassColors.Accent)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Audio", style = HaloTypography.bodyMedium, color = HaloGlassColors.TextSecondary)
                        Text(uiState.audioStatus, style = HaloTypography.bodyMedium, color = HaloGlassColors.TextPrimary)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Camera", style = HaloTypography.bodyMedium, color = HaloGlassColors.TextSecondary)
                        Text(uiState.cameraStatus, style = HaloTypography.bodyMedium, color = HaloGlassColors.TextPrimary)
                    }
                }
            }
            // Removed dead Device Details button
        }
    }
}

data class DevicesUiState(
    val isConnected: Boolean = false,
    val deviceName: String = "No glasses connected",
    val battery: String = "Not reported",
    val connectionType: String = "Not reported",
    val audioStatus: String = "Not reported",
    val cameraStatus: String = "Not reported"
)

class DevicesViewModel(private val snapshotManager: RuntimeSnapshotSource) : ViewModel() {
    val uiState: StateFlow<DevicesUiState> = snapshotManager.snapshot
        .map { snapshot ->
            val isConnected = snapshot.deviceContext.connected
            val caps = snapshot.deviceContext.capabilities
            DevicesUiState(
                isConnected = isConnected,
                deviceName = snapshot.deviceContext.name ?: if (isConnected) "Unknown device" else "No glasses connected",
                battery = snapshot.deviceContext.batteryPercent?.let { "$it%" } ?: "Unknown",
                connectionType = if (isConnected) "BLE" else "Disconnected",
                audioStatus = if (caps.contains("Audio")) "Supported" else "Not reported",
                cameraStatus = if (caps.contains("Camera")) "Supported" else "Not reported"
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = DevicesUiState()
        )

    private val _navigationEvents = kotlinx.coroutines.flow.MutableSharedFlow<String>()
    val navigationEvents = _navigationEvents.asSharedFlow()

    fun navigateTo(route: String) {
        viewModelScope.launch {
            _navigationEvents.emit(route)
        }
    }
}
