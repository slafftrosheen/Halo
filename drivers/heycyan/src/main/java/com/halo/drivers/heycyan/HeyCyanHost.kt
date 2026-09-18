package com.halo.drivers.heycyan

import android.util.Log
import com.halo.drivers.heycyan.protocol.HeyCyanEvent
import com.halo.sdk.host.AudioOutput
import com.halo.sdk.host.AudioSource
import com.halo.sdk.host.ButtonEvent
import com.halo.sdk.host.ConnectionState
import com.halo.sdk.host.DeviceConnectionState
import com.halo.sdk.host.DeviceContext
import com.halo.sdk.host.Freshness
import com.halo.sdk.host.WearableHost
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class HeyCyanHost(
    private val driver: RealHeyCyanDriver,
    override val audioOutput: AudioOutput,
    private val storage: com.halo.sdk.runtime.Storage,
    private val sessionManager: com.halo.sdk.runtime.embodiment.DeviceSessionManager? = null,
    private val eventBus: com.halo.sdk.runtime.EventBus? = null
) : WearableHost {
    override val id: String = "heycyan-glasses"
    override val audioSource: AudioSource = HeyCyanAudioSource(driver)

    // Registry/session work is persistence and transport orchestration, not UI.
    // Keep it off the main thread so first composition cannot block rendering.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var selectedAddress: String? = storage.getString("last_heycyan_address")

    override val buttons: Flow<ButtonEvent> = driver.heyCyanEvents
        .filterIsInstance<HeyCyanEvent.ButtonPressed>()
        .map { ButtonEvent.Pressed }

    private val _deviceContext = MutableStateFlow(DeviceContext())
    override val deviceContext: StateFlow<DeviceContext> = _deviceContext.asStateFlow()
    private val _connectivity = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    override val connectivity: StateFlow<ConnectionState> = _connectivity.asStateFlow()

    init {
        scope.launch {
            retryAutoConnect()
        }

        driver.deviceState
            .onEach { state ->
                val heyCyanConn = state.connection
                val isConnected = heyCyanConn == HeyCyanConnectionState.CONNECTED ||
                    heyCyanConn == HeyCyanConnectionState.READY
                val deviceConnState = when (heyCyanConn) {
                    HeyCyanConnectionState.DISCONNECTED -> DeviceConnectionState.DISCONNECTED
                    HeyCyanConnectionState.SCANNING -> DeviceConnectionState.DISCOVERING
                    HeyCyanConnectionState.CONNECTING -> DeviceConnectionState.CONNECTING
                    HeyCyanConnectionState.CONNECTED -> DeviceConnectionState.CONNECTED
                    HeyCyanConnectionState.READY -> DeviceConnectionState.READY
                    HeyCyanConnectionState.DISCONNECTING -> DeviceConnectionState.DISCONNECTING
                    HeyCyanConnectionState.ERROR -> DeviceConnectionState.ERROR
                    HeyCyanConnectionState.UNBOUND -> DeviceConnectionState.ERROR
                    HeyCyanConnectionState.RECONNECTING -> DeviceConnectionState.CONNECTING
                }
                val currentLastSeen = _deviceContext.value.lastSeenMs
                _deviceContext.value = DeviceContext(
                    deviceId = selectedAddress,
                    name = "HeyCyan",
                    deviceType = "HEYCYAN_GLASSES",
                    batteryPercent = state.battery?.level,
                    connected = isConnected,
                    charging = state.battery?.charging ?: false,
                    capabilities = com.halo.drivers.heycyan.capability.HeyCyanCapabilities.all.map { it.id },
                    transport = "BLE",
                    lastSeenMs = if (isConnected) System.currentTimeMillis() else currentLastSeen,
                    freshness = if (isConnected) Freshness.FRESH else if (currentLastSeen != null) Freshness.STALE else Freshness.UNKNOWN,
                    state = deviceConnState
                )
            }
            .launchIn(scope)

        driver.state
            .onEach { state ->
                _connectivity.value = when (state) {
                    is com.halo.sdk.driver.DriverState.Ready -> {
                        val address = selectedAddress
                        if (address != null) {
                            ensureSessionRegistered(address)
                            val manager = sessionManager
                            if (manager != null) {
                                scope.launch {
                                    manager.registry.getDevice(address)?.let { record ->
                                        manager.registry.saveDevice(record.copy(lastSeenMs = System.currentTimeMillis()))
                                    }
                                }
                            }
                        }
                        ConnectionState.Ready
                    }
                    is com.halo.sdk.driver.DriverState.Connecting -> ConnectionState.Connecting
                    is com.halo.sdk.driver.DriverState.Error -> ConnectionState.Failed(state.cause.message ?: "Unknown error")
                    else -> ConnectionState.Disconnected
                }
            }
            .launchIn(scope)
    }

    /**
     * Re-register saved glasses sessions and retry the preferred auto-connect target.
     * Safe to call after Bluetooth returns; DeviceSessionManager serializes duplicate
     * connect attempts and treats READY as success.
     */
    suspend fun retryAutoConnect(): Boolean {
        val sm = sessionManager ?: return false
        selectedAddress?.let { migrateLegacySavedDevice(sm, it) }

        val saved = sm.registry.getSavedDevices()
            .filter { it.identity.deviceType == "HEYCYAN_GLASSES" }
        saved.forEach { ensureSessionRegistered(it.identity.deviceId) }

        val target = selectedAddress
            ?.let { id -> saved.firstOrNull { it.identity.deviceId == id && it.autoConnect } }
            ?: saved.firstOrNull { it.autoConnect }
            ?: return false

        selectedAddress = target.identity.deviceId
        return runCatching {
            sm.autoConnectDevice(target.identity.deviceId)
        }.onFailure {
            Log.w("HeyCyanHost", "Saved device auto-connect failed", it)
        }.getOrDefault(false)
    }

    private suspend fun migrateLegacySavedDevice(
        manager: com.halo.sdk.runtime.embodiment.DeviceSessionManager,
        address: String
    ) {
        val legacyId = "heycyan"
        if (address.equals(legacyId, ignoreCase = true)) return
        val legacy = manager.registry.getDevice(legacyId) ?: return
        if (manager.registry.getDevice(address) != null) return

        manager.registry.saveDevice(
            legacy.copy(identity = legacy.identity.copy(deviceId = address))
        )
        manager.registry.removeDevice(legacyId)
        Log.i("HeyCyanHost", "Migrated legacy HeyCyan saved identity to $address")
    }

    private fun ensureSessionRegistered(address: String) {
        val sm = sessionManager ?: return
        val bus = eventBus ?: return
        if (sm.getSession(address) == null) {
            val identity = com.halo.sdk.runtime.DeviceIdentity(address, "HEYCYAN_GLASSES", "HeyCyan Glasses")
            val transport = com.halo.drivers.heycyan.transport.HeyCyanEmbodimentTransport(driver, address)
            val controllerAdapter = com.halo.drivers.heycyan.capability.HeyCyanCapabilityControllerAdapter(driver.capabilityController)
            sm.registerSession(com.halo.sdk.runtime.embodiment.HeyCyanSession(
                identity = identity,
                controller = controllerAdapter,
                transport = transport,
                eventBus = bus
            ))
        }
    }

    fun setTargetDevice(address: String) {
        selectedAddress = address
        storage.putString("last_heycyan_address", address)
        sessionManager?.let { manager ->
            scope.launch { migrateLegacySavedDevice(manager, address) }
        }
    }

    override suspend fun connect() {
        val address = selectedAddress ?: return
        try {
            val session = sessionManager?.getSession(address)
            if (session != null) {
                session.connect()
            } else {
                driver.connect(address, HeyCyanConnectionMode.RUNTIME)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e("HeyCyanHost", "Failed to connect to $address", e)
            _connectivity.value = ConnectionState.Failed(e.message ?: "Unknown error")
        }
    }

    override suspend fun disconnect() { driver.disconnect() }
}
