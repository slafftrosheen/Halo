package com.halo.app.ui.screens.devices

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.halo.app.ui.glass.components.HaloGlassBackHeader
import com.halo.app.ui.glass.components.HaloGlassButton
import com.halo.app.ui.glass.components.HaloGlassLazyPage
import com.halo.app.ui.glass.material.HaloGlassColors
import com.halo.app.ui.glass.material.HaloTypography
import com.halo.sdk.runtime.RuntimeSnapshotSource
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class GenericDeviceUiState(
    val id: String,
    val name: String,
    val type: String,
    val isConnected: Boolean,
    val battery: String,
    val firmware: String,
    val lastSeen: String
)

data class DevicesUiState(
    val devices: List<GenericDeviceUiState> = emptyList()
)

class DevicesViewModel(private val snapshotManager: RuntimeSnapshotSource) : ViewModel() {
    
    // Mock persisted devices that Halo knows about
    private val persistedDevices = listOf(
        GenericDeviceUiState(
            id = "heycyan_1",
            name = "HeyCyan BW-AG1 Pro",
            type = "HeyCyan",
            isConnected = false,
            battery = "Unknown",
            firmware = "v1.2.4",
            lastSeen = "2 hours ago"
        ),
        GenericDeviceUiState(
            id = "buddybot_1",
            name = "BuddyBot",
            type = "BuddyBot",
            isConnected = false,
            battery = "Unknown",
            firmware = "v0.9.1",
            lastSeen = "1 day ago"
        )
    )

    val uiState: StateFlow<DevicesUiState> = snapshotManager.snapshot
        .map { snapshot ->
            val activeContext = snapshot.deviceContext
            val isConnected = activeContext.connected
            val activeName = activeContext.name ?: "Unknown device"
            val activeBattery = activeContext.batteryPercent?.let { "$it%" } ?: "Unknown"
            
            // Map the active runtime state onto our persisted devices if they match.
            // (Assuming for now that if connected, it's HeyCyan unless we check capabilities, 
            // but we'll mock HeyCyan active if the name contains HeyCyan or just generically)
            val updatedDevices = persistedDevices.map { device ->
                if (isConnected && (activeName.contains("HeyCyan", ignoreCase = true) && device.id == "heycyan_1")) {
                    device.copy(isConnected = true, battery = activeBattery, lastSeen = "Now")
                } else if (isConnected && (activeName.contains("BuddyBot", ignoreCase = true) && device.id == "buddybot_1")) {
                    device.copy(isConnected = true, battery = activeBattery, lastSeen = "Now")
                } else if (isConnected && device.id == "heycyan_1" && activeName == "Unknown device") {
                    // Fallback mock logic: assume unknown connection is HeyCyan for the demo
                    device.copy(isConnected = true, battery = activeBattery, lastSeen = "Now")
                } else {
                    device
                }
            }
            
            DevicesUiState(devices = updatedDevices)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = DevicesUiState(devices = persistedDevices)
        )

    private val _navigationEvents = MutableSharedFlow<String>()
    val navigationEvents = _navigationEvents.asSharedFlow()

    fun navigateTo(route: String) {
        viewModelScope.launch {
            _navigationEvents.emit(route)
        }
    }

    fun dispatchEmbodimentIntent(intent: com.halo.sdk.runtime.embodiment.EmbodimentIntent) {
        // In a real implementation this would push to the EventBus or CommandBus
        // For now, it represents the boundary where UI sends semantic intents
    }
}

@Composable
fun DevicesScreen(viewModel: DevicesViewModel, onNavigate: (String) -> Unit = {}, onBack: () -> Unit = {}) {
    val uiState by viewModel.uiState.collectAsState()
    
    HaloGlassLazyPage(title = "") {
        item {
            HaloGlassBackHeader(
                title = "Devices",
                onBack = onBack
            )
        }
        
        item { Spacer(modifier = Modifier.height(16.dp)) }

        items(uiState.devices) { device ->
            DeviceRow(
                device = device,
                onClick = {
                    if (device.type == "HeyCyan") {
                        viewModel.navigateTo("device/heycyan")
                    } else if (device.type == "BuddyBot") {
                        viewModel.navigateTo("device/buddybot")
                    }
                }
            )
            Spacer(modifier = Modifier.height(12.dp))
        }

        item { Spacer(modifier = Modifier.height(24.dp)) }

        item {
            HaloGlassButton(
                text = "Add device",
                onClick = { viewModel.navigateTo("discovery") },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
fun DeviceRow(device: GenericDeviceUiState, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(HaloGlassColors.GlassElevated)
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = device.name,
                style = HaloTypography.titleMedium,
                color = HaloGlassColors.TextPrimary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(
                            if (device.isConnected) HaloGlassColors.Accent else HaloGlassColors.Danger,
                            CircleShape
                        )
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (device.isConnected) "Connected" else "Offline • Last seen ${device.lastSeen}",
                    style = HaloTypography.bodyMedium,
                    color = HaloGlassColors.TextSecondary
                )
            }
        }
        Text(
            text = ">",
            style = HaloTypography.titleLarge,
            color = HaloGlassColors.TextMuted
        )
    }
}
