package com.halo.app.ui.developer.protocol

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
import com.halo.drivers.heycyan.debug.HeyCyanLogger
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

@Composable
fun ProtocolLabScreen(viewModel: ProtocolLabViewModel, onBack: () -> Unit = {}) {
    val state by viewModel.uiState.collectAsState()
    
    com.halo.app.ui.glass.components.HaloGlassLazyPage(title = "") {
        item {
            com.halo.app.ui.glass.components.HaloGlassBackHeader(
                title = "Protocol Lab",
                onBack = onBack
            )
        }
        
        // Connection Section
                item {
                    HaloGlassSection(
                        title = "Connection", 
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                    ) {
                        Column(Modifier.padding(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(if (state.isDiscovering) "Scanning..." else "Idle", color = HaloGlassColors.TextPrimary)
                                com.halo.app.ui.glass.components.HaloGlassButton(
                                    text = if (state.isDiscovering) "Stop" else "Discover",
                                    onClick = { viewModel.toggleDiscovery() },
                                    enabled = !state.isConnecting
                                )
                            }
                            
                            if (state.discoveredDevices.isNotEmpty()) {
                                Spacer(Modifier.height(8.dp))
                                state.discoveredDevices.forEach { device ->
                                    Row(
                                        Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            Text(device.name, color = HaloGlassColors.TextPrimary)
                                            Text(device.id, color = HaloGlassColors.TextSecondary, style = HaloTypography.labelSmall)
                                        }
                                        com.halo.app.ui.glass.components.HaloGlassButton(
                                            text = if (state.isConnecting && (state.activeDeviceName == device.name)) "..." else "Connect",
                                            onClick = { viewModel.connect(device) },
                                            enabled = !state.isDiscovering && !state.isConnecting
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                item { Spacer(Modifier.height(16.dp)) }

                // DEVICE Section
                item {
                    HaloGlassSection(
                        title = "DEVICE", 
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                    ) {
                        Column(Modifier.padding(8.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Active Name", color = HaloGlassColors.TextSecondary)
                                Text(state.activeDeviceName ?: "None", color = HaloGlassColors.TextPrimary)
                            }
                        }
                    }
                }

                item { Spacer(Modifier.height(16.dp)) }

                // TRANSPORT Section
                item {
                    HaloGlassSection(
                        title = "TRANSPORT (BLE GATT)", 
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                    ) {
                        Column(Modifier.padding(8.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Status", color = HaloGlassColors.TextSecondary)
                                Text(state.connectionStatus, color = HaloGlassColors.Accent)
                            }
                        }
                    }
                }

                item { Spacer(Modifier.height(16.dp)) }

                // FORENSIC CAPTURE Section
                item {
                    HaloGlassSection(
                        title = "FORENSIC CAPTURE",
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                    ) {
                        Row(
                            Modifier.fillMaxWidth().padding(8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Record Session", color = HaloGlassColors.TextPrimary)
                            Switch(
                                checked = state.isForensicCaptureEnabled,
                                onCheckedChange = { viewModel.setForensicCaptureEnabled(it) }
                            )
                        }
                    }
                }
                
                item { Spacer(Modifier.height(16.dp)) }

                // QUICK ACTIONS Section
                item {
                    HaloGlassSection(
                        title = "QUICK ACTIONS",
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                    ) {
                        Column(Modifier.padding(8.dp)) {
                            Row(
                                Modifier.fillMaxWidth().padding(vertical = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                com.halo.app.ui.glass.components.HaloGlassButton(
                                    text = "Photo",
                                    onClick = { viewModel.triggerForensicOperation("PHOTO") },
                                    modifier = Modifier.weight(1f)
                                )
                                
                                com.halo.app.ui.glass.components.HaloGlassButton(
                                    text = "Vid Toggle",
                                    onClick = { viewModel.triggerForensicOperation("VID_TOGGLE") },
                                    modifier = Modifier.weight(1f)
                                )
                                
                                com.halo.app.ui.glass.components.HaloGlassButton(
                                    text = "Aud Toggle",
                                    onClick = { viewModel.triggerForensicOperation("AUD_TOGGLE") },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            Row(
                                Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                com.halo.app.ui.glass.components.HaloGlassButton(
                                    text = "Media Cnt",
                                    onClick = { viewModel.triggerForensicOperation("MEDIA_COUNT") },
                                    modifier = Modifier.weight(1f)
                                )
                                Spacer(Modifier.weight(2f))
                            }
                        }
                    }
                }

                item { Spacer(Modifier.height(16.dp)) }

                // TRANSACTION LOG Section
                item {
                    HaloGlassSection(
                        title = "TRANSACTION LOG",
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                    ) {
                        Column(Modifier.padding(8.dp)) {
                            Text(
                                text = "View raw transport evidence and detailed logs in the Developer Console.",
                                color = HaloGlassColors.TextSecondary,
                                style = HaloTypography.bodySmall
                            )
                            Spacer(Modifier.height(8.dp))
                            com.halo.app.ui.glass.components.HaloGlassButton(
                                text = "Open Console",
                                onClick = { viewModel.navigateTo("console") },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }

                item { Spacer(Modifier.height(16.dp)) }

                // AUDIO STREAM Section
                item {
                    HaloGlassSection(
                        title = "AUDIO STREAM (BC 59)",
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                    ) {
                        Column(Modifier.padding(8.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Status", color = HaloGlassColors.TextSecondary)
                                Text(state.audioStreamStatus, color = HaloGlassColors.Accent)
                            }
                            Spacer(Modifier.height(8.dp))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Session Packets", color = HaloGlassColors.TextSecondary)
                                Text(state.sessionAudioPacketsReceived.toString(), color = HaloGlassColors.TextPrimary)
                            }
                            Spacer(Modifier.height(8.dp))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Last Payload", color = HaloGlassColors.TextSecondary)
                                Text("${state.lastPayloadSize} bytes", color = HaloGlassColors.TextPrimary)
                            }
                            Spacer(Modifier.height(8.dp))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Interval", color = HaloGlassColors.TextSecondary)
                                Text("${state.lastIntervalMs} ms", color = HaloGlassColors.TextPrimary)
                            }
                            Spacer(Modifier.height(8.dp))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Decoder", color = HaloGlassColors.TextSecondary)
                                Text(state.decoderStatus, color = if (state.decoderStatus == "Healthy") HaloGlassColors.Success else HaloGlassColors.Warning)
                            }
                            Spacer(Modifier.height(8.dp))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Lifetime Packets", color = HaloGlassColors.TextSecondary)
                                Text(state.audioPacketsReceived.toString(), color = HaloGlassColors.TextMuted)
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
    val isForensicCaptureEnabled: Boolean = false,
    val audioStreamStatus: String = "Inactive",
    val audioPacketsReceived: Int = 0,
    val sessionAudioPacketsReceived: Int = 0,
    val lastPayloadSize: Int = 0,
    val lastIntervalMs: Long = 0,
    val decoderStatus: String = "Unknown"
)
data class ProtocolDevice(val id: String, val name: String, val protocol: String)

class ProtocolLabViewModel(
    private val driver: RealHeyCyanDriver = ManualDependencyGraph.heyCyanDriver
) : ViewModel() {
    private val _state = MutableStateFlow(DiscoveryState())
    val uiState: StateFlow<DiscoveryState> = _state.asStateFlow()
    
    private var scanJob: kotlinx.coroutines.Job? = null

    init {
        // Observe capture state
        com.halo.drivers.heycyan.codec.HeyCyanProtocolCapture.instance.isCaptureEnabled
            .onEach { enabled ->
                _state.update { it.copy(isForensicCaptureEnabled = enabled) }
            }
            .launchIn(viewModelScope)
            
        // Observe connection state
        driver.state.onEach { ds ->
            _state.update { it.copy(connectionStatus = ds.toString()) }
        }.launchIn(viewModelScope)
        
        // Observe audio stream stats
        com.halo.drivers.heycyan.debug.HeyCyanAudioStreamProbe.totalPacketCountFlow
            .onEach { count ->
                _state.update { it.copy(audioPacketsReceived = count) }
            }
            .launchIn(viewModelScope)
            
        com.halo.drivers.heycyan.debug.HeyCyanAudioStreamProbe.sessionPacketCountFlow
            .onEach { count ->
                _state.update { it.copy(sessionAudioPacketsReceived = count) }
            }
            .launchIn(viewModelScope)
            
        com.halo.drivers.heycyan.debug.HeyCyanAudioStreamProbe.statusFlow
            .onEach { status ->
                _state.update { it.copy(
                    audioStreamStatus = status,
                    lastPayloadSize = com.halo.drivers.heycyan.debug.HeyCyanAudioStreamProbe.getLastPayloadSize(),
                    lastIntervalMs = com.halo.drivers.heycyan.debug.HeyCyanAudioStreamProbe.getLastIntervalMs(),
                    decoderStatus = com.halo.drivers.heycyan.debug.HeyCyanAudioStreamProbe.getDecoderStatus()
                ) }
            }
            .launchIn(viewModelScope)
    }

    private val _navigationEvents = MutableSharedFlow<String>()
    val navigationEvents: SharedFlow<String> = _navigationEvents.asSharedFlow()

    fun navigateTo(route: String) {
        viewModelScope.launch {
            _navigationEvents.emit(route)
        }
    }

    fun setForensicCaptureEnabled(enabled: Boolean) {
        com.halo.drivers.heycyan.codec.HeyCyanProtocolCapture.instance.setCaptureEnabled(enabled)
    }

    fun triggerForensicOperation(opName: String) {
        HeyCyanLogger.i("HALO-TX-TRACE", "UI: Trigger forensic operation: $opName")
        viewModelScope.launch {
            val driver = ManualDependencyGraph.heyCyanDriver
            val hooks = com.halo.drivers.heycyan.debug.HeyCyanCaptureHooks(driver, this)
            
            when (opName) {
                "PHOTO" -> hooks.triggerPhoto()
                "VID_TOGGLE" -> hooks.triggerVideoToggle()
                "AUD_TOGGLE" -> hooks.triggerAudioToggle()
                "MEDIA_COUNT" -> hooks.triggerMediaCount()
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
            } catch (e: Exception) {
                println("[ERROR] [${java.time.Instant.now()}] [HALO-ProtocolLab] Connection failed: ${e.message}")
            } finally {
                _state.update { it.copy(isConnecting = false) }
            }
        }
    }
}
