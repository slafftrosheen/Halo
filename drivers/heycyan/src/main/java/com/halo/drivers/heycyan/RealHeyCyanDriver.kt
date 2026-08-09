package com.halo.drivers.heycyan

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.content.Context
import android.os.Build
import com.halo.drivers.heycyan.ble.HeyCyanBleScanner
import com.halo.drivers.heycyan.ble.HeyCyanGattClient
import com.halo.drivers.heycyan.codec.LoggingPacketTracer
import com.halo.drivers.heycyan.codec.CaptureSessionRecorder
import com.halo.drivers.heycyan.codec.CaptureMetadata
import com.halo.drivers.heycyan.codec.CompositePacketTracer
import com.halo.drivers.heycyan.codec.JsonCaptureSessionStore
import com.halo.drivers.heycyan.codec.HeyCyanCommandEncoder
import com.halo.drivers.heycyan.codec.HeyCyanEventDecoder
import com.halo.drivers.heycyan.protocol.ButtonType
import com.halo.drivers.heycyan.protocol.HeyCyanCommand
import com.halo.drivers.heycyan.protocol.HeyCyanEvent
import com.halo.libraries.logging.ConsoleLogger
import com.halo.sdk.driver.DeviceEvent
import com.halo.sdk.driver.Driver
import com.halo.sdk.driver.DriverState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.io.File

/**
 * Real HeyCyan transport driver.
 *
 * Physical discovery is deliberately raw-only. A caller must select an
 * advertisement address explicitly before connecting; this driver never picks
 * the first nearby BLE device. Protocol definitions remain test-only until
 * real capture evidence promotes them.
 */
class RealHeyCyanDriver(
    private val context: Context,
    private val eventDecoder: HeyCyanEventDecoder? = null,
    private val commandEncoder: HeyCyanCommandEncoder? = null
) : Driver {

    private val captureRecorder = CaptureSessionRecorder(
        JsonCaptureSessionStore(File(context.filesDir, "captures/heycyan"))
    )
    private val tracer = CompositePacketTracer(
        LoggingPacketTracer(ConsoleLogger()),
        captureRecorder
    )
    val scanner = HeyCyanBleScanner(context)
    private val gattClient = HeyCyanGattClient(context, tracer)

    private val _events = MutableSharedFlow<DeviceEvent>(extraBufferCapacity = 64)
    override val events: Flow<DeviceEvent> = _events.asSharedFlow()
    override val state: StateFlow<DriverState> = gattClient.state
    private val eventScope = kotlinx.coroutines.CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var incomingEventJob: Job? = null
    private var connectionMode: HeyCyanConnectionMode? = null

    override suspend fun initialize() = Unit

    override suspend fun connect() {
        throw IllegalStateException(
            "HeyCyan requires explicit device selection; use connect(selectedAddress, mode, selectedName)"
        )
    }

    suspend fun connect(
        selectedAddress: String,
        mode: HeyCyanConnectionMode,
        selectedName: String? = null,
        scenario: String = "discovery",
        advertisement: com.halo.drivers.heycyan.ble.BleAdvertisement? = null
    ) {
        require(selectedAddress.isNotBlank()) { "Selected BLE address must not be blank" }
        connectionMode = mode
        if (canDecodeEvents(mode)) {
            startIncomingEventDecoder()
        }
        captureRecorder.start(scenario = scenario, metadata = captureMetadata())
        gattClient.markScanning()

        try {
            captureRecorder.setDevice(selectedAddress, selectedName)
            advertisement?.let {
                captureRecorder.recordAdvertisement(
                    com.halo.drivers.heycyan.codec.CaptureAdvertisement(
                        timestamp = System.currentTimeMillis(),
                        address = it.address,
                        name = it.name,
                        rssi = it.rssi,
                        serviceUuids = it.serviceUuids,
                        manufacturerData = it.manufacturerData
                    )
                )
            }
            gattClient.connect(selectedAddress)
            gattClient.awaitReady()
            if (canDecodeEvents(mode)) {
                _events.emit(DeviceEvent.Ready(System.currentTimeMillis()))
            }
        } catch (error: CancellationException) {
            captureRecorder.stop()
            throw error
        } catch (error: Throwable) {
            gattClient.fail(error)
            captureRecorder.stop()
            throw error
        }
    }

    override suspend fun disconnect() {
        gattClient.disconnect()
        gattClient.awaitDisconnected()
        incomingEventJob?.cancel()
        incomingEventJob = null
        connectionMode = null
        captureRecorder.stop()
    }

    override suspend fun shutdown() {
        disconnect()
        eventScope.coroutineContext[Job]?.cancel()
    }

    override suspend fun emit(event: DeviceEvent) {
        _events.emit(event)
    }

    /** Sends only promoted, capture-backed commands. Discovery has none. */
    suspend fun sendCommand(command: HeyCyanCommand): Result<Unit> = runCatching {
        check(canSendCommands(connectionMode ?: HeyCyanConnectionMode.DISCOVERY)) {
            "HeyCyan commands are disabled during BLE protocol discovery"
        }
        val encoder = commandEncoder
            ?: error("No runtime HeyCyan command encoder is configured")
        val encoded = encoder.encodeWithTarget(command)
        check(gattClient.write(encoded.characteristic, encoded.copyPayload())) {
            "HeyCyan command write was rejected by GATT"
        }
    }

    private fun startIncomingEventDecoder() {
        if (incomingEventJob != null) return
        val decoder = eventDecoder
            ?: error("No runtime HeyCyan event decoder is configured")
        incomingEventJob = eventScope.launch(start = CoroutineStart.UNDISPATCHED) {
            gattClient.incomingBytes.collect { (characteristic, payload) ->
                val decoded = decoder.decode(
                    payload = payload,
                    characteristic = characteristic,
                    timestamp = System.currentTimeMillis()
                )
                emitDeviceEvent(decoded, characteristic, payload)
            }
        }
    }

    private suspend fun emitDeviceEvent(
        event: HeyCyanEvent,
        characteristic: java.util.UUID,
        payload: ByteArray
    ) {
        val timestamp = System.currentTimeMillis()
        when (event) {
            is HeyCyanEvent.BatteryUpdated -> {
                _events.emit(DeviceEvent.BatteryLevel(timestamp, event.level))
                _events.emit(DeviceEvent.ChargingState(timestamp, event.isCharging))
            }
            is HeyCyanEvent.ButtonPressed -> {
                _events.emit(DeviceEvent.ButtonClick(timestamp, event.type.toButtonId()))
            }
            HeyCyanEvent.Ready -> _events.emit(DeviceEvent.Ready(timestamp))
            is HeyCyanEvent.UnknownEvent -> _events.emit(
                DeviceEvent.ProtocolUnknown(
                    timestamp = timestamp,
                    characteristic = characteristic,
                    payload = payload,
                    reason = event.reason
                )
            )
            else -> Unit
        }
    }

    private fun ButtonType.toButtonId(): Int = when (this) {
        ButtonType.SINGLE_TAP -> 1
        ButtonType.DOUBLE_TAP -> 2
        ButtonType.LONG_PRESS -> 3
        ButtonType.VOICE_BUTTON -> 4
        ButtonType.CAMERA_BUTTON -> 5
    }

    @SuppressLint("MissingPermission")
    private fun readBluetoothAdapterName(): String? {
        if (!shouldReadBluetoothAdapterName(
                sdkInt = Build.VERSION.SDK_INT,
                permissionChecker = context::checkSelfPermission
            )
        ) {
            return null
        }

        return runCatching {
            context.getSystemService(BluetoothManager::class.java)?.adapter?.name
        }.getOrNull()
    }

    private fun captureMetadata(): CaptureMetadata {
        val packageInfo = runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0)
        }.getOrNull()
        val bluetoothAdapter = readBluetoothAdapterName()

        return CaptureMetadata(
            haloVersion = packageInfo?.versionName,
            androidVersion = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
            phoneModel = listOf(Build.MANUFACTURER, Build.MODEL)
                .filter { it.isNotBlank() }
                .joinToString(" ")
                .ifBlank { null },
            bluetoothAdapter = bluetoothAdapter
        )
    }
}
