package com.halo.app.ui.screens.protocol

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.halo.app.di.ManualDependencyGraph
import com.halo.app.ui.glass.components.HaloGlassSection
import com.halo.app.ui.glass.material.HaloGlassColors
import com.halo.app.ui.glass.material.HaloTypography
import com.halo.drivers.heycyan.RealHeyCyanDriver
import com.halo.sdk.host.ConnectionState
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

@Composable
fun ProtocolLabScreen(viewModel: ProtocolLabViewModel) {
    val state by viewModel.uiState.collectAsState()
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(HaloGlassColors.Background)
    ) {
        // Header
        Row(
            Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Protocol Lab", color = HaloGlassColors.TextPrimary, style = HaloTypography.displayMedium)
            Button(
                onClick = { viewModel.toggleDiscovery() },
                enabled = !state.isConnecting
            ) { 
                Text(if (state.isDiscovering) "Stop" else "Discover") 
            }
        }
        
        // Single root container for all scrollable content
        Box(modifier = Modifier.weight(1f)) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 32.dp)
            ) {
                // Status Section
                item {
                    HaloGlassSection(
                        title = "Device Status", 
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                    ) {
                        Column(Modifier.padding(8.dp)) {
                            Text("State: ${if (state.isDiscovering) "Scanning" else "Idle"}", color = HaloGlassColors.TextPrimary)
                            Text("Found: ${state.discoveredDevices.size}", color = HaloGlassColors.TextSecondary)
                            Text("Active: ${state.activeDeviceName ?: "None"}", color = HaloGlassColors.TextSecondary)
                            Text("Connection: ${state.connectionStatus}", color = HaloGlassColors.Accent, style = HaloTypography.labelSmall)
                        }
                    }
                }

                item {
                    Spacer(Modifier.height(16.dp))
                    HaloGlassSection(
                        title = "Forensic Tools",
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                    ) {
                        Column(Modifier.padding(8.dp)) {
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Capture Mode", color = HaloGlassColors.TextPrimary)
                                Switch(
                                    checked = state.isForensicCaptureEnabled,
                                    onCheckedChange = { viewModel.setForensicCaptureEnabled(it) }
                                )
                            }
                            
                            if (state.isForensicCaptureEnabled) {
                                Spacer(Modifier.height(12.dp))
                                Text("Trigger Commands:", style = HaloTypography.labelLarge, color = HaloGlassColors.Accent)
                                Row(
                                    Modifier.fillMaxWidth().padding(vertical = 8.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Button(
                                        onClick = { viewModel.triggerForensicOperation("PHOTO") },
                                        modifier = Modifier.weight(1f),
                                        contentPadding = PaddingValues(4.dp)
                                    ) { Text("Photo", fontSize = 10.sp) }
                                    
                                    Button(
                                        onClick = { viewModel.triggerForensicOperation("RECORD_START") },
                                        modifier = Modifier.weight(1f),
                                        contentPadding = PaddingValues(4.dp)
                                    ) { Text("Rec Start", fontSize = 10.sp) }
                                    
                                    Button(
                                        onClick = { viewModel.triggerForensicOperation("RECORD_STOP") },
                                        modifier = Modifier.weight(1f),
                                        contentPadding = PaddingValues(4.dp)
                                    ) { Text("Rec Stop", fontSize = 10.sp) }
                                }
                            }
                        }
                    }
                }
                
                item { Spacer(Modifier.height(24.dp)) }
                
                // Devices Header
                item {
                    Text(
                        text = "DISCOVERED DEVICES",
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        style = HaloTypography.labelLarge,
                        color = HaloGlassColors.TextSecondary
                    )
                }

                // Devices List
                if (state.discoveredDevices.isEmpty()) {
                    item {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(200.dp), 
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                if (state.isDiscovering) "Searching..." else "No devices discovered", 
                                color = HaloGlassColors.TextSecondary
                            )
                        }
                    }
                } else {
                    items(state.discoveredDevices, key = { it.id }) { device ->
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = HaloGlassColors.BackgroundSecondary.copy(alpha = 0.5f)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 4.dp)
                        ) {
                            Row(
                                Modifier
                                    .padding(12.dp)
                                    .fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(device.name, color = HaloGlassColors.TextPrimary, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                                    Text(device.id, color = HaloGlassColors.TextSecondary, style = HaloTypography.labelSmall)
                                }
                                Button(
                                    onClick = { viewModel.connect(device) },
                                    enabled = !state.isDiscovering && !state.isConnecting,
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        if (state.isConnecting && state.activeDeviceName == device.name) "..." else "Connect", 
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

data class DiscoveryState(
    val discoveredDevices: List<ProtocolDevice> = emptyList(), 
    val activeDeviceName: String? = null,
    val connectionStatus: String = "Disconnected",
    val isDiscovering: Boolean = false,
    val isConnecting: Boolean = false,
    val isForensicCaptureEnabled: Boolean = false
)
data class ProtocolDevice(val id: String, val name: String, val protocol: String)

class ProtocolLabViewModel(
    private val driver: RealHeyCyanDriver = ManualDependencyGraph.heyCyanDriver
) : ViewModel() {
    private val _state = MutableStateFlow(DiscoveryState())
    val uiState: StateFlow<DiscoveryState> = _state.asStateFlow()
    
    private var scanJob: kotlinx.coroutines.Job? = null

    init {
        // ... (existing observations)
        
        // Observe capture state
        com.halo.drivers.heycyan.codec.HeyCyanProtocolCapture.instance.isCaptureEnabled
            .onEach { enabled ->
                _state.update { it.copy(isForensicCaptureEnabled = enabled) }
            }
            .launchIn(viewModelScope)
    }

    fun setForensicCaptureEnabled(enabled: Boolean) {
        com.halo.drivers.heycyan.codec.HeyCyanProtocolCapture.instance.setCaptureEnabled(enabled)
    }

    fun triggerForensicOperation(opName: String) {
        viewModelScope.launch {
            val driver = ManualDependencyGraph.heyCyanDriver
            val hooks = com.halo.drivers.heycyan.debug.HeyCyanCaptureHooks(driver, this)
            
            when (opName) {
                "PHOTO" -> hooks.triggerPhoto()
                "VIDEO_START" -> hooks.triggerVideoStart()
                "VIDEO_STOP" -> hooks.triggerVideoStop()
                "RECORD_START" -> hooks.triggerRecordStart()
                "RECORD_STOP" -> hooks.triggerRecordStop()
            }
        }
    }

    fun toggleDiscovery() {
        if (_state.value.isDiscovering) {
            stopDiscovery()
        } else {
            startDiscovery()
        }
    }

    fun startDiscovery() {
        scanJob?.cancel()
        _state.update { it.copy(isDiscovering = true, discoveredDevices = emptyList()) }
        scanJob = viewModelScope.launch {
            driver.scanner.scan().collect { advertisement ->
                _state.update { state ->
                    val newDevice = ProtocolDevice(
                        id = advertisement.address,
                        name = advertisement.name ?: "Unknown",
                        protocol = "BLE"
                    )
                    if (state.discoveredDevices.any { it.id == newDevice.id }) {
                        state
                    } else {
                        state.copy(discoveredDevices = state.discoveredDevices + newDevice)
                    }
                }
            }
        }
    }

    fun stopDiscovery() {
        scanJob?.cancel()
        _state.update { it.copy(isDiscovering = false) }
    }

    fun connect(device: ProtocolDevice) {
        if (_state.value.isConnecting) return
        
        stopDiscovery()
        _state.update { it.copy(isConnecting = true, activeDeviceName = device.name) }
        
        viewModelScope.launch {
            try {
                val host = ManualDependencyGraph.wearableHost
                if (host is com.halo.drivers.heycyan.HeyCyanHost) {
                    host.setTargetDevice(device.id)
                    host.connect()
                }
            } finally {
                _state.update { it.copy(isConnecting = false) }
            }
        }
    }
}
