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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
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

class DevicesViewModel(
    val deviceRegistry: com.halo.sdk.runtime.embodiment.DeviceRegistry,
    val sessionManager: com.halo.sdk.runtime.embodiment.DeviceSessionManager,
    private val eventBus: com.halo.sdk.runtime.EventBus,
    private val snapshotSource: com.halo.sdk.runtime.RuntimeSnapshotSource
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(DevicesUiState())
    val uiState: StateFlow<DevicesUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            // Combine saved devices and active sessions
            sessionManager.activeSessions.collect { activeSessions ->
                val savedDevices = deviceRegistry.getSavedDevices()
                
                val updatedDevices = savedDevices.map { record ->
                    val session = activeSessions.find { it.identity.deviceId == record.identity.deviceId }
                    val isConnected = session?.state?.value == com.halo.sdk.runtime.embodiment.SessionState.READY
                    
                    GenericDeviceUiState(
                        id = record.identity.deviceId,
                        name = record.identity.displayName,
                        type = record.identity.deviceType,
                        isConnected = isConnected,
                        battery = "Unknown",
                        firmware = record.firmwareVersion ?: "Unknown",
                        lastSeen = if (isConnected) "Now" else "Last seen ${record.lastSeenMs}ms ago"
                    )
                }
                
                _uiState.value = DevicesUiState(devices = updatedDevices)
            }
        }
    }

    private val _navigationEvents = MutableSharedFlow<String>()
    val navigationEvents = _navigationEvents.asSharedFlow()

    fun navigateTo(route: String) {
        viewModelScope.launch {
            _navigationEvents.emit(route)
        }
    }

    fun dispatchEmbodimentIntent(deviceId: String, intent: com.halo.sdk.runtime.embodiment.EmbodimentIntent) {
        viewModelScope.launch {
            // Publish IntentCreated to let the cognitive timeline track this manual action
            eventBus.publish(com.halo.sdk.runtime.IntentCreated(
                intentId = intent.intentId,
                intentKind = intent.kind.name
            ))
            
            val session = sessionManager.activeSessions.value.find { it.identity.deviceId == deviceId }
            if (session != null && session.state.value == com.halo.sdk.runtime.embodiment.SessionState.READY) {
                session.dispatch(intent)
            } else {
                eventBus.publish(com.halo.sdk.runtime.EmbodimentExecutionEvent(
                    device = com.halo.sdk.runtime.DeviceIdentity(deviceId, "Unknown", "Unknown"),
                    result = com.halo.sdk.runtime.embodiment.ExecutionResult(
                        intentId = intent.intentId,
                        status = com.halo.sdk.runtime.embodiment.ExecutionStatus.FAILED,
                        reason = "No active session",
                        timestamp = System.currentTimeMillis(),
                        embodimentId = deviceId
                    )
                ))
            }
        }
    }

    fun connectDevice(deviceId: String) {
        viewModelScope.launch {
            sessionManager.getSession(deviceId)?.connect()
        }
    }

    fun disconnectDevice(deviceId: String) {
        viewModelScope.launch {
            sessionManager.getSession(deviceId)?.disconnect()
        }
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
                    viewModel.navigateTo("device/${device.id}")
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
