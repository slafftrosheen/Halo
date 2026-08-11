package com.halo.app.ui.screens.protocol

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import com.halo.app.ui.glass.components.HaloGlassSection
import com.halo.app.ui.glass.material.HaloGlassColors
import com.halo.app.ui.glass.material.HaloTypography
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@Composable
fun ProtocolLabScreen(viewModel: ProtocolLabViewModel) {
    val state by viewModel.uiState.collectAsState()
    
    Column(Modifier.fillMaxSize().background(HaloGlassColors.Background)) {
        // Header
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Protocol Lab", color = HaloGlassColors.TextPrimary, style = HaloTypography.displayMedium)
            Button(onClick = { viewModel.startDiscovery() }) { Text("Discover") }
        }
        
        Spacer(Modifier.height(8.dp))
        
        // Status
        HaloGlassSection(title = "Device Status", modifier = Modifier.fillMaxWidth()) {
            Text("Found: ${state.discoveredDevices.size}", color = HaloGlassColors.TextSecondary)
            Text("Active: ${state.activeDevice?.name ?: "None"}", color = HaloGlassColors.TextSecondary)
        }
        
        Spacer(Modifier.height(16.dp))
        
        // Devices
        HaloGlassSection(title = "Discovered Devices", modifier = Modifier.weight(1f)) {
            if (state.discoveredDevices.isEmpty()) {
                Text("No devices discovered", color = HaloGlassColors.TextSecondary)
            } else {
                // Device list
            }
        }
    }
}

data class DiscoveryState(
    val discoveredDevices: List<ProtocolDevice> = emptyList(), 
    val activeDevice: ProtocolDevice? = null
)
data class ProtocolDevice(val id: String, val name: String, val protocol: String)

class ProtocolLabViewModel : ViewModel() {
    private val _state = MutableStateFlow(DiscoveryState())
    val uiState: StateFlow<DiscoveryState> = _state.asStateFlow()
    
    fun startDiscovery() {
        // TODO: Implement BLE discovery
    }
}
