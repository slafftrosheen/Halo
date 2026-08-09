package com.halo.drivers.heycyan

import com.halo.drivers.heycyan.capability.HeyCyanCapabilities
import com.halo.drivers.heycyan.capability.HeyCyanDiagnostics
import com.halo.drivers.heycyan.operations.HeyCyanAudioRecordingController
import com.halo.drivers.heycyan.operations.HeyCyanCameraController
import com.halo.drivers.heycyan.operations.HeyCyanModeController
import com.halo.drivers.heycyan.operations.HeyCyanVideoController
import com.halo.sdk.driver.DeviceCapabilities
import com.halo.sdk.driver.DeviceEvent
import com.halo.sdk.driver.Driver
import com.halo.sdk.host.AudioOutput
import com.halo.sdk.host.AudioSource
import com.halo.sdk.host.ButtonEvent
import com.halo.sdk.host.ConnectionState
import com.halo.sdk.host.DeviceContext
import com.halo.sdk.host.WearableHost
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

/**
 * Complete HeyCyan host adapter.
 *
 * Exposes the HeyCyan glasses as a complete WearableHost with full capability
 * integration. The host delegates to the device controller and sub-controllers
 * for all operations while implementing the WearableHost contract.
 *
 * Audio: HeyCyanAudioSource emits no frames because HeyCyan has no live BLE
 * microphone (UNSUPPORTED_BY_DEVICE). The cognitive loop should use
 * PhoneMicrophoneAudioSource for live audio input.
 *
 * See: HEYCYAN_FUNCTIONALITY_MATRIX.md for the complete capability matrix.
 */
class HeyCyanHost(
    private val driver: Driver,
    override val audioOutput: AudioOutput,
    override val id: String = "host.heycyan.glasses"
) : WearableHost {

    /**
     * Audio source — deliberately empty.
     *
     * HeyCyan has no live BLE microphone. This is UNSUPPORTED_BY_DEVICE.
     * For the cognitive loop, use PhoneMicrophoneAudioSource (phone hardware mic)
     * as the audio source. HeyCyan provides only recorded audio (record on device
     * → transfer via WiFi → decode locally).
     */
    override val audioSource: AudioSource = HeyCyanAudioSource(driver)

    private val _buttons = MutableSharedFlow<ButtonEvent>()
    override val buttons: Flow<ButtonEvent> = _buttons.asSharedFlow()

    private val _deviceContext = MutableStateFlow(DeviceContext())
    override val deviceContext: StateFlow<DeviceContext> = _deviceContext

    private val _connectivity = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    override val connectivity: StateFlow<ConnectionState> = _connectivity

    private val scope = CoroutineScope(Dispatchers.IO)
    private var hostJob: Job? = null
    private var deviceStateJob: Job? = null

    // ── Capabilities (available after connect) ───────────────────────

    /** HeyCyan device capabilities declaration. */
    val capabilities: DeviceCapabilities = DeviceCapabilities.HEYCYAN_GLASSES

    /**
     * Typed capability accessors — available after connection.
     * Initialized lazily when the device controller becomes available.
     */
    private var _heycyanCapabilities: HeyCyanCapabilities? = null
    val heycyanCapabilities: HeyCyanCapabilities?
        get() = _heycyanCapabilities

    private var _diagnostics: HeyCyanDiagnostics? = null
    val diagnostics: HeyCyanDiagnostics?
        get() = _diagnostics

    // ── WearableHost Lifecycle ───────────────────────────────────────

    override suspend fun connect() {
        _connectivity.value = ConnectionState.Connecting
        driver.initialize()
        runCatching { driver.connect() }
            .onSuccess {
                _deviceContext.value = _deviceContext.value.copy(connected = true)
                _connectivity.value = ConnectionState.Connected

                // Start event forwarding
                hostJob = scope.launch {
                    driver.events.collect { event ->
                        forwardEvent(event)
                    }
                }
            }
            .onFailure { error ->
                _connectivity.value = ConnectionState.Failed(
                    error.message ?: "HeyCyan connection failed"
                )
            }
    }

    override suspend fun disconnect() {
        hostJob?.cancel()
        hostJob = null
        deviceStateJob?.cancel()
        deviceStateJob = null
        driver.disconnect()
        audioSource.stop()
        _heycyanCapabilities = null
        _diagnostics = null
        _deviceContext.value = _deviceContext.value.copy(connected = false)
        _connectivity.value = ConnectionState.Disconnected
    }

    // ── Internal Event Forwarding ────────────────────────────────────

    private suspend fun forwardEvent(event: DeviceEvent) {
        when (event) {
            is DeviceEvent.ButtonClick -> {
                _buttons.emit(ButtonEvent.Pressed)
                delay(100)
                _buttons.emit(ButtonEvent.Released)
            }
            is DeviceEvent.BatteryLevel -> {
                _deviceContext.value = _deviceContext.value.copy(
                    batteryPercent = event.level
                )
            }
            is DeviceEvent.ChargingState -> {
                _deviceContext.value = _deviceContext.value.copy(
                    charging = event.isCharging
                )
            }
            is DeviceEvent.WearState -> {
                _deviceContext.value = _deviceContext.value.copy(
                    wearing = event.isWorn
                )
            }
            is DeviceEvent.Ready -> {
                // Device is ready — connection fully established
            }
            else -> {
                // ProtocolUnknown, Input, Output — pass through without context update
            }
        }
    }

    /**
     * Wires up the capability system with a connected device controller.
     * Called by the driver after device initialization is complete.
     */
    fun initializeCapabilities(
        deviceController: HeyCyanDeviceController,
        dispatcher: HeyCyanCommandDispatcher
    ) {
        val modeController = HeyCyanModeController()
        val cameraController = HeyCyanCameraController(dispatcher, modeController)
        val videoController = HeyCyanVideoController(dispatcher, modeController)
        val audioRecordingController = HeyCyanAudioRecordingController(dispatcher, modeController)

        _heycyanCapabilities = HeyCyanCapabilities(
            deviceController = deviceController,
            cameraController = cameraController,
            videoController = videoController,
            audioRecordingController = audioRecordingController,
            modeController = modeController
        )

        _diagnostics = HeyCyanDiagnostics(
            deviceController = deviceController,
            cameraController = cameraController,
            videoController = videoController,
            audioRecordingController = audioRecordingController,
            modeController = modeController,
            capabilities = _heycyanCapabilities!!
        )

        // Forward device state to DeviceContext
        deviceStateJob = scope.launch {
            deviceController.batteryUpdates.collect { battery ->
                if (battery != null) {
                    _deviceContext.value = _deviceContext.value.copy(
                        batteryPercent = battery.level,
                        charging = battery.isCharging
                    )
                }
            }
        }
    }
}
