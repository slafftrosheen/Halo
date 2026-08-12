package com.halo.drivers.heycyan

import android.annotation.SuppressLint
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import com.halo.drivers.heycyan.ble.HeyCyanBleScanner
import com.halo.drivers.heycyan.ble.HeyCyanGattClient
import com.halo.drivers.heycyan.codec.*
import com.halo.drivers.heycyan.protocol.ButtonType
import com.halo.drivers.heycyan.protocol.HeyCyanEvent
import com.halo.libraries.logging.ConsoleLogger
import com.halo.sdk.driver.DeviceEvent
import com.halo.sdk.driver.Driver
import com.halo.sdk.driver.DriverState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import java.io.File
import com.halo.drivers.heycyan.HeyCyanConnectionMode
import com.halo.drivers.heycyan.rfcomm.HeyCyanRfcommClient
import com.halo.drivers.heycyan.capability.*
import com.halo.drivers.heycyan.ble.HeyCyanGattCommandTransport
import com.halo.drivers.heycyan.codec.HeyCyanCommandEncoder
import com.halo.drivers.heycyan.codec.HeyCyanPacketFramer
import com.halo.drivers.heycyan.capability.toOperationFailure

/**
 * Real HeyCyan transport driver.
 *
 * Physical discovery is deliberately raw-only. A caller must select an
 * advertisement address explicitly before connecting; this driver never picks
 * the first nearby BLE device. Protocol definitions remain test-only until
 * real capture evidence promotes them.
 */
class RealHeyCyanDriver(
    private val context: Context
) : Driver {

    init {
        android.util.Log.i("RealHeyCyanDriver", "Initializing RealHeyCyanDriver")
        HeyCyanProtocolCapture.instance = HeyCyanProtocolCapture(ConsoleLogger())
    }

    private val captureRecorder = CaptureSessionRecorder(
        JsonCaptureSessionStore(File(context.filesDir, "captures/heycyan"))
    )
    private val tracer = CompositePacketTracer(
        LoggingPacketTracer(ConsoleLogger()),
        captureRecorder
    )
    val scanner = HeyCyanBleScanner(context)
    private val gattClient = HeyCyanGattClient(context, tracer)
    private val rfcommClient = HeyCyanRfcommClient(context, tracer)

    private val packetAnalyzer = HeyCyanConsolePacketAnalyzer(ConsoleLogger())
    private val eventRouter = HeyCyanEventRouter(HeyCyanSdkEventDecoder())

    private val stateStore = com.halo.drivers.heycyan.protocol.HeyCyanDeviceStateStore()
    val deviceState: StateFlow<com.halo.drivers.heycyan.protocol.HeyCyanDeviceState> = stateStore.state

    private val _events = MutableSharedFlow<DeviceEvent>(extraBufferCapacity = 64)
    override val events: Flow<DeviceEvent> = _events.asSharedFlow()
    
    private val _heyCyanEvents =
        MutableSharedFlow<HeyCyanEvent>(extraBufferCapacity = 64)
    val heyCyanEvents: Flow<HeyCyanEvent> = _heyCyanEvents.asSharedFlow()
    
    override val state: StateFlow<DriverState> = gattClient.state
    private val eventScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var readJob: kotlinx.coroutines.Job? = null

    val capabilityProvider: CapabilityProvider by lazy {
        HeyCyanCapabilityProvider(
            HeyCyanCapabilityController(
                executor = DefaultHeyCyanCommandExecutor(
                    transport = HeyCyanGattCommandTransport(
                        gatt = gattClient,
                        commandCharacteristic = null // Resolved at runtime in transport
                    ),
                    encoder = HeyCyanCommandEncoder(),
                    framer = HeyCyanPacketFramer()
                ),
                stateStore = stateStore
            )
        )
    }

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
        captureId: String? = null,
        advertisement: com.halo.drivers.heycyan.ble.BleAdvertisement? = null
    ) {
        require(selectedAddress.isNotBlank()) { "Selected BLE address must not be blank" }
        android.util.Log.i("RealHeyCyanDriver", "Connecting to $selectedAddress (mode: $mode)")
        captureRecorder.abort()
        captureRecorder.start(
            scenario = scenario,
            captureId = captureId,
            metadata = captureMetadata()
        )
        gattClient.markScanning()
        stateStore.updateConnection(com.halo.drivers.heycyan.protocol.HeyCyanConnectionState.SCANNING)

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
            stateStore.updateConnection(com.halo.drivers.heycyan.protocol.HeyCyanConnectionState.CONNECTING)
            
            android.util.Log.i("RealHeyCyanDriver", "Starting RFCOMM connection...")
            val rfcommChannels = rfcommClient.connect(selectedAddress).getOrElse { 
                android.util.Log.w("RealHeyCyanDriver", "RFCOMM connection failed: $it")
                emptyList() 
            }
            
            android.util.Log.i("RealHeyCyanDriver", "Starting GATT connection...")
            val gattReady = runCatching {
                gattClient.connect(selectedAddress)
                gattClient.awaitReady()
                true
            }.onFailure {
                android.util.Log.w("RealHeyCyanDriver", "GATT connection failed", it)
            }.getOrDefault(false)

            android.util.Log.i("RealHeyCyanDriver", "Transport status: RFCOMM=$rfcommChannels, GATT=$gattReady")

            if (gattReady) {
                stateStore.updateConnection(com.halo.drivers.heycyan.protocol.HeyCyanConnectionState.READY)
            }
            if (gattReady && mode == HeyCyanConnectionMode.RUNTIME) {
                startReading()
            }
            check(isUsableDiscoveryTransport(rfcommChannels, gattReady)) {
                "No usable HeyCyan transport connected for $selectedAddress"
            }
            if (mode == HeyCyanConnectionMode.RUNTIME) {
                check(gattReady) { "Runtime mode requires verified BLE GATT readiness" }
            }
            if (canDecodeEvents(mode)) {
                _events.emit(DeviceEvent.Ready(System.currentTimeMillis()))
            }
        } catch (error: CancellationException) {
            android.util.Log.i("RealHeyCyanDriver", "Connection cancelled")
            stateStore.updateConnection(com.halo.drivers.heycyan.protocol.HeyCyanConnectionState.DISCONNECTED)
            captureRecorder.abort()
            throw error
        } catch (error: Throwable) {
            android.util.Log.e("RealHeyCyanDriver", "Connection failed", error)
            stateStore.updateConnection(com.halo.drivers.heycyan.protocol.HeyCyanConnectionState.ERROR)
            gattClient.fail(error)
            captureRecorder.abort()
            throw error
        }
    }

    override suspend fun disconnect() {
        android.util.Log.i("RealHeyCyanDriver", "Disconnecting...")
        rfcommClient.disconnect()
        gattClient.disconnect()
        gattClient.awaitDisconnected()
        readJob?.cancel()
        readJob = null
        stateStore.updateConnection(com.halo.drivers.heycyan.protocol.HeyCyanConnectionState.DISCONNECTED)
        runCatching { captureRecorder.stop() }
    }

    override suspend fun shutdown() {
        android.util.Log.i("RealHeyCyanDriver", "Shutting down...")
        disconnect()
        rfcommClient.shutdown()
        eventScope.coroutineContext[kotlinx.coroutines.Job]?.cancel()
    }

    override suspend fun emit(event: DeviceEvent) {
        _events.emit(event)
    }

    /**
     * Emits decoded HeyCyan events to the protocol event stream.
     * Consumers can observe these events for application-level state management.
     */
    private suspend fun emitDeviceEvent(event: HeyCyanEvent) {
        _heyCyanEvents.emit(event)
    }

    private fun startReading() {
        readJob?.cancel()

        readJob = eventScope.launch {
            android.util.Log.i("RealHeyCyanDriver", "Starting event reader loop")
            
            gattClient.incomingBytes.collect { (characteristicUuid, payload) ->

                packetAnalyzer.onRx(
                    characteristicUuid = characteristicUuid,
                    payload = payload
                )

                val event = eventRouter.decode(
                    characteristicUuid = characteristicUuid,
                    payload = payload,
                    timestampMillis = System.currentTimeMillis()
                )

                when (event) {
                    is HeyCyanEvent.DecodedProprietaryFrame -> {
                        // Evidence preserved in event; can be used for state matching later
                        handleDecodedEvent(event)
                    }

                    is HeyCyanEvent.Unknown -> {
                        // Preserve evidence; don't discard it.
                        android.util.Log.d("RealHeyCyanDriver", "Unknown event on $characteristicUuid: ${payload.joinToString("") { "%02X".format(it) }}")
                    }

                    else -> {
                        handleDecodedEvent(event)
                    }
                }
            }
        }
    }

    private suspend fun handleDecodedEvent(
        event: HeyCyanEvent
    ) {
        val timestamp = System.currentTimeMillis()
        
        stateStore.apply(
            event = event,
            timestamp = timestamp,
        )

        when (event) {
            is HeyCyanEvent.BatteryUpdated -> {
                _events.emit(
                    DeviceEvent.BatteryLevel(
                        timestamp = timestamp,
                        level = event.level,
                    )
                )
                emitDeviceEvent(event)
            }

            is HeyCyanEvent.ButtonPressed -> {
                _events.emit(
                    DeviceEvent.ButtonClick(
                        timestamp = timestamp,
                        buttonId = event.type.toButtonId(),
                    )
                )
                emitDeviceEvent(event)
            }

            else -> {
                // Keep HeyCyan-specific events available through
                // the protocol event stream.
                emitDeviceEvent(event)
            }
        }
    }

    private fun ButtonType.toButtonId(): Int = when (this) {
        ButtonType.SINGLE_TAP -> 0
        ButtonType.DOUBLE_TAP -> 1
        ButtonType.LONG_PRESS -> 2
        ButtonType.VOICE_BUTTON -> 3
        ButtonType.CAMERA_BUTTON -> 4
        ButtonType.UNKNOWN -> -1
    }

    private fun isUsableDiscoveryTransport(rfcommChannels: List<String>, gattReady: Boolean): Boolean {
        return rfcommChannels.isNotEmpty() || gattReady
    }

    private fun shouldReadBluetoothAdapterName(sdkInt: Int, permissionChecker: (String) -> Int): Boolean {
        return if (sdkInt >= Build.VERSION_CODES.S) {
            permissionChecker(android.Manifest.permission.BLUETOOTH_CONNECT) == android.content.pm.PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
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