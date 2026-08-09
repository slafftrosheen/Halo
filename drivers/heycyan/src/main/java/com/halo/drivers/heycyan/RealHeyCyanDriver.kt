package com.halo.drivers.heycyan

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
import com.halo.drivers.heycyan.codec.HeyCyanProtocolDefinitions
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.io.File

/**
 * Real HeyCyan transport driver.
 *
 * PR-011 records complete BLE discovery and transport sessions.
 * PR-013/014 installs hypothesis decoder and encoder definitions backed by
 * synthetic fixture captures. Real hardware evidence will promote or replace
 * these definitions once imported captures are committed.
 *
 * @see HeyCyanProtocolDefinitions
 */
class RealHeyCyanDriver(
    private val context: Context,
    private val eventDecoder: HeyCyanEventDecoder = HeyCyanEventDecoder(
        HeyCyanProtocolDefinitions.eventDecoderDefinitions()
    ),
    private val commandEncoder: HeyCyanCommandEncoder = HeyCyanCommandEncoder(
        HeyCyanProtocolDefinitions.commandEncoderDefinitions()
    )
) : Driver {

    companion object {
        private const val SCAN_TIMEOUT_MILLIS = 30_000L
    }

    private val captureRecorder = CaptureSessionRecorder(
        JsonCaptureSessionStore(File(context.filesDir, "captures/heycyan"))
    )
    private val tracer = CompositePacketTracer(
        LoggingPacketTracer(ConsoleLogger()),
        captureRecorder
    )
    private val scanner = HeyCyanBleScanner(context)
    private val gattClient = HeyCyanGattClient(context, tracer)

    private val _events = MutableSharedFlow<DeviceEvent>(extraBufferCapacity = 64)
    override val events: Flow<DeviceEvent> = _events.asSharedFlow()
    override val state: StateFlow<DriverState> = gattClient.state
    private val eventScope = kotlinx.coroutines.CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var incomingEventJob: Job? = null

    override suspend fun initialize() = Unit

    override suspend fun connect() {
        startIncomingEventDecoder()
        captureRecorder.start(
            scenario = "runtime",
            metadata = captureMetadata()
        )
        gattClient.markScanning()

        try {
            val advertisement = withTimeout(SCAN_TIMEOUT_MILLIS) {
                scanner.scanForDevice().first()
            }
            captureRecorder.recordAdvertisement(
                com.halo.drivers.heycyan.codec.CaptureAdvertisement(
                    timestamp = System.currentTimeMillis(),
                    address = advertisement.address,
                    name = advertisement.name,
                    rssi = advertisement.rssi,
                    serviceUuids = advertisement.serviceUuids,
                    manufacturerData = advertisement.manufacturerData
                )
            )
            gattClient.connect(advertisement.address)
            captureRecorder.setDevice(advertisement.address, advertisement.name)
            gattClient.awaitReady()
            _events.emit(DeviceEvent.Ready(System.currentTimeMillis()))
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
        captureRecorder.stop()
    }

    override suspend fun shutdown() {
        disconnect()
        eventScope.coroutineContext[Job]?.cancel()
    }

    override suspend fun emit(event: DeviceEvent) {
        _events.emit(event)
    }

    /** Sends a command only when the supplied encoder has capture-backed evidence. */
    suspend fun sendCommand(command: HeyCyanCommand): Result<Unit> = runCatching {
        val encoded = commandEncoder.encodeWithTarget(command)
        check(gattClient.write(encoded.characteristic, encoded.copyPayload())) {
            "HeyCyan command write was rejected by GATT"
        }
    }

    private fun startIncomingEventDecoder() {
        if (incomingEventJob != null) return
        incomingEventJob = eventScope.launch(start = CoroutineStart.UNDISPATCHED) {
            gattClient.incomingBytes.collect { (characteristic, payload) ->
                val decoded = eventDecoder.decode(
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

    private fun captureMetadata(): CaptureMetadata {
        val packageInfo = runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0)
        }.getOrNull()
        val bluetoothAdapter = runCatching {
            context.getSystemService(BluetoothManager::class.java)?.adapter?.name
        }.getOrNull()

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
