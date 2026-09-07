package com.halo.drivers.heycyan.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import com.halo.drivers.heycyan.codec.HeyCyanProtocolCapture
import com.halo.drivers.heycyan.codec.NoOpPacketTracer
import com.halo.drivers.heycyan.codec.PacketDirection
import com.halo.drivers.heycyan.codec.PacketTracer
import com.halo.sdk.driver.DriverState
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import java.util.ArrayDeque
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

/**
 * Low-level HeyCyan GATT transport.
 *
 * Each physical connection attempt gets a monotonically increasing attempt id.
 * Old BluetoothGatt instances are invalidated before a new attempt is created,
 * and late callbacks from stale instances are ignored. This keeps the transport
 * deterministic enough for diagnosing vendor-stack failures such as GATT 147.
 */
@SuppressLint("MissingPermission")
open class HeyCyanGattClient(
    private val context: Context? = null,
    private val tracer: PacketTracer = NoOpPacketTracer
) {

    companion object {
        private val CCCD_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
        private const val REQUESTED_MTU = 517
    }

    private data class PendingDescriptorWrite(
        val characteristic: BluetoothGattCharacteristic,
        val descriptor: BluetoothGattDescriptor
    )

    private val bluetoothManager by lazy {
        context?.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    }
    private val adapter by lazy { bluetoothManager?.adapter }
    private val attemptCounter = AtomicInteger(0)

    @Volatile
    private var activeAttemptId: Int = 0

    private var gatt: BluetoothGatt? = null
    private var serviceDiscoveryStarted = false
    private var descriptorWriteInFlight: PendingDescriptorWrite? = null
    private val pendingDescriptorWrites = ArrayDeque<PendingDescriptorWrite>()
    private val characteristics = mutableMapOf<UUID, BluetoothGattCharacteristic>()

    private val _state = MutableStateFlow<DriverState>(DriverState.Disconnected)
    val state: StateFlow<DriverState> = _state.asStateFlow()

    private val _incomingBytes = MutableSharedFlow<Pair<UUID, ByteArray>>(extraBufferCapacity = 64)
    val incomingBytes: SharedFlow<Pair<UUID, ByteArray>> = _incomingBytes.asSharedFlow()

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(
            callbackGatt: BluetoothGatt,
            status: Int,
            newState: Int
        ) {
            val attemptId = activeAttemptId
            android.util.Log.i(
                "HeyCyanGattClient",
                "onConnectionStateChange attempt=$attemptId " +
                    "address=${callbackGatt.device.address} status=$status newState=$newState"
            )
            tracer.onConnectionStateChanged(status, newState)

            if (callbackGatt !== gatt) {
                android.util.Log.w(
                    "HeyCyanGattClient",
                    "Ignoring stale GATT callback attempt=$attemptId " +
                        "address=${callbackGatt.device.address}"
                )
                runCatching { callbackGatt.close() }
                return
            }

            if (status != BluetoothGatt.GATT_SUCCESS) {
                failGatt(
                    callbackGatt,
                    RuntimeException(
                        "GATT connection error: status=$status " +
                            "attempt=$attemptId address=${callbackGatt.device.address} newState=$newState"
                    )
                )
                return
            }

            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    _state.value = DriverState.Connected
                    serviceDiscoveryStarted = false
                    pendingDescriptorWrites.clear()
                    descriptorWriteInFlight = null

                    tracer.onMtuRequested(REQUESTED_MTU)
                    val mtuRequested = runCatching {
                        callbackGatt.requestMtu(REQUESTED_MTU)
                    }.getOrElse {
                        android.util.Log.w(
                            "HeyCyanGattClient",
                            "requestMtu threw attempt=$attemptId",
                            it
                        )
                        false
                    }

                    if (!mtuRequested) {
                        android.util.Log.i(
                            "HeyCyanGattClient",
                            "MTU request rejected/not supported; continuing to service discovery " +
                                "attempt=$attemptId"
                        )
                        discoverServices(callbackGatt)
                    }
                }

                BluetoothProfile.STATE_DISCONNECTED -> {
                    clearGatt(callbackGatt)
                    _state.value = DriverState.Disconnected
                }
            }
        }

        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
            android.util.Log.i(
                "HeyCyanGattClient",
                "onMtuChanged attempt=$activeAttemptId mtu=$mtu status=$status"
            )
            if (status == BluetoothGatt.GATT_SUCCESS) {
                tracer.onMtuChanged(mtu)
            }
            discoverServices(gatt)
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            android.util.Log.i(
                "HeyCyanGattClient",
                "onServicesDiscovered attempt=$activeAttemptId status=$status " +
                    "services=${gatt.services.size}"
            )
            if (gatt !== this@HeyCyanGattClient.gatt) return

            if (status != BluetoothGatt.GATT_SUCCESS) {
                failGatt(
                    gatt,
                    RuntimeException(
                        "Service discovery failed: status=$status attempt=$activeAttemptId"
                    )
                )
                return
            }

            serviceDiscoveryStarted = true
            pendingDescriptorWrites.clear()
            descriptorWriteInFlight = null

            for (service in gatt.services) {
                tracer.onDiscoveredService(service.uuid, service.type)

                for (characteristic in service.characteristics) {
                    tracer.onDiscoveredCharacteristic(
                        service.uuid,
                        characteristic.uuid,
                        characteristic.properties,
                        characteristic.permissions
                    )
                    characteristics[characteristic.uuid] = characteristic

                    for (descriptor in characteristic.descriptors) {
                        tracer.onDiscoveredDescriptor(
                            service.uuid,
                            characteristic.uuid,
                            descriptor.uuid,
                            descriptor.permissions
                        )
                    }

                    val supportsNotifications = characteristic.properties and
                        BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0
                    val supportsIndications = characteristic.properties and
                        BluetoothGattCharacteristic.PROPERTY_INDICATE != 0
                    val cccd = characteristic.getDescriptor(CCCD_UUID)

                    val charUuid = characteristic.uuid.toString().lowercase()
                    val isRelevantNotifyChar = charUuid.contains("de5bf729") ||
                        charUuid.startsWith("0000fee3") ||
                        charUuid.startsWith("6e400003") ||
                        charUuid.startsWith("0000ae02") ||
                        charUuid.startsWith("0000ae04")

                    if ((supportsNotifications || supportsIndications) &&
                        cccd != null &&
                        isRelevantNotifyChar
                    ) {
                        val localEnabled = runCatching {
                            gatt.setCharacteristicNotification(characteristic, true)
                        }.getOrElse {
                            android.util.Log.w(
                                "HeyCyanGattClient",
                                "setCharacteristicNotification threw attempt=$activeAttemptId " +
                                    "uuid=${characteristic.uuid}",
                                it
                            )
                            false
                        }

                        if (localEnabled) {
                            pendingDescriptorWrites.add(
                                PendingDescriptorWrite(characteristic, cccd)
                            )
                        } else {
                            tracer.onDescriptorWrite(cccd.uuid, BluetoothGatt.GATT_FAILURE)
                        }
                    }
                }
            }

            writeNextDescriptor(gatt)
        }

        override fun onDescriptorWrite(
            callbackGatt: BluetoothGatt?,
            descriptor: BluetoothGattDescriptor?,
            status: Int
        ) {
            if (descriptor == null) return

            tracer.onDescriptorWrite(descriptor.uuid, status)
            android.util.Log.i(
                "HeyCyanGattClient",
                "onDescriptorWrite attempt=$activeAttemptId uuid=${descriptor.uuid} status=$status"
            )

            if (callbackGatt !== this@HeyCyanGattClient.gatt) return
            if (descriptorWriteInFlight?.descriptor?.uuid != descriptor.uuid) return

            descriptorWriteInFlight = null
            if (status != BluetoothGatt.GATT_SUCCESS) {
                android.util.Log.w(
                    "HeyCyanGattClient",
                    "CCCD write failed; continuing queue " +
                        "attempt=$activeAttemptId uuid=${descriptor.uuid}"
                )
            }
            writeNextDescriptor(callbackGatt)
        }

        override fun onCharacteristicWrite(
            callbackGatt: BluetoothGatt?,
            characteristic: BluetoothGattCharacteristic?,
            status: Int
        ) {
            if (characteristic == null) return
            android.util.Log.d(
                "HeyCyanGattClient",
                "onCharacteristicWrite attempt=$activeAttemptId " +
                    "uuid=${characteristic.uuid} status=$status"
            )
            tracer.onCharacteristicWrite(characteristic.uuid, status)
        }

        @Suppress("DEPRECATION")
        override fun onCharacteristicChanged(
            callbackGatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            characteristic.value?.let { emitNotification(characteristic, it) }
        }

        override fun onCharacteristicChanged(
            callbackGatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            emitNotification(characteristic, value)
        }
    }

    fun markScanning() {
        if (_state.value == DriverState.Disconnected || _state.value is DriverState.Error) {
            _state.value = DriverState.Scanning
        }
    }

    fun fail(cause: Throwable) {
        _state.value = DriverState.Error(cause)
    }

    /** Starts one fresh physical GATT attempt. */
    fun connect(macAddress: String) {
        require(macAddress.isNotBlank()) { "BLE MAC address must not be blank" }

        val currentState = _state.value
        if (currentState != DriverState.Disconnected &&
            currentState != DriverState.Scanning &&
            currentState !is DriverState.Error
        ) {
            android.util.Log.d(
                "HeyCyanGattClient",
                "Skipping connect; state=$currentState attempt=$activeAttemptId"
            )
            return
        }

        closeCurrentGatt("new connection requested")

        val attemptId = attemptCounter.incrementAndGet()
        activeAttemptId = attemptId
        resetConnectionState()
        _state.value = DriverState.Connecting

        val device = try {
            adapter?.getRemoteDevice(macAddress)
                ?: throw IllegalStateException("BluetoothAdapter unavailable")
        } catch (error: Throwable) {
            _state.value = DriverState.Error(error)
            throw error
        }

        android.util.Log.i(
            "HeyCyanGattClient",
            "Starting GATT attempt=$attemptId address=$macAddress transport=LE"
        )

        val createdGatt = runCatching {
            device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
        }.getOrElse {
            val wrapped = RuntimeException(
                "connectGatt threw attempt=$attemptId address=$macAddress",
                it
            )
            _state.value = DriverState.Error(wrapped)
            throw wrapped
        }

        if (createdGatt == null) {
            val error = RuntimeException(
                "connectGatt returned null attempt=$attemptId address=$macAddress"
            )
            _state.value = DriverState.Error(error)
            return
        }

        gatt = createdGatt
    }

    suspend fun awaitReady(timeoutMillis: Long = 30_000L) {
        val awaitedAttempt = activeAttemptId
        val terminalState = withTimeout(timeoutMillis) {
            state.filter {
                it == DriverState.Ready ||
                    it is DriverState.Error ||
                    it == DriverState.Disconnected
            }.first()
        }

        android.util.Log.i(
            "HeyCyanGattClient",
            "awaitReady finished attempt=$awaitedAttempt state=$terminalState"
        )

        when (terminalState) {
            DriverState.Ready -> Unit
            is DriverState.Error -> throw terminalState.cause
            DriverState.Disconnected -> throw IllegalStateException(
                "GATT disconnected before discovery completed for attempt=$awaitedAttempt"
            )
            else -> error("Unexpected terminal GATT state: $terminalState")
        }
    }

    suspend fun awaitDisconnected(timeoutMillis: Long = 5_000L) {
        if (_state.value == DriverState.Disconnected) return

        withTimeoutOrNull(timeoutMillis) {
            state.filter { it == DriverState.Disconnected }.first()
        } ?: run {
            android.util.Log.w(
                "HeyCyanGattClient",
                "Disconnect timeout; forcing GATT close attempt=$activeAttemptId"
            )
            closeCurrentGatt("disconnect timeout")
            _state.value = DriverState.Disconnected
        }
    }

    open fun write(characteristicUuid: UUID, payload: ByteArray): Boolean {
        val currentGatt = gatt ?: return false
        val characteristic = characteristics[characteristicUuid] ?: return false

        if (_state.value != DriverState.Ready) {
            android.util.Log.w(
                "HeyCyanGattClient",
                "Rejecting write in state=${_state.value} " +
                    "attempt=$activeAttemptId uuid=$characteristicUuid"
            )
            return false
        }

        val writeType = if (characteristic.properties and
            BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE != 0
        ) {
            "WRITE_NO_RESPONSE"
        } else {
            "WRITE"
        }

        HeyCyanProtocolCapture.instance.recordFrame(
            characteristicUuid = characteristicUuid,
            payload = payload,
            direction = PacketDirection.TX,
            writeType = writeType
        )
        tracer.onTx(characteristicUuid, payload)
        characteristic.value = payload

        return runCatching {
            currentGatt.writeCharacteristic(characteristic)
        }.getOrElse {
            android.util.Log.e(
                "HeyCyanGattClient",
                "writeCharacteristic threw attempt=$activeAttemptId " +
                    "uuid=$characteristicUuid",
                it
            )
            false
        }
    }

    fun findCharacteristicByProperty(property: Int): BluetoothGattCharacteristic? =
        characteristics.values.firstOrNull { it.properties and property != 0 }

    fun disconnect() {
        val currentGatt = gatt
        if (currentGatt == null) {
            resetConnectionState()
            _state.value = DriverState.Disconnected
            return
        }

        if (_state.value != DriverState.Disconnected) {
            _state.value = DriverState.Disconnecting
        }

        runCatching { currentGatt.disconnect() }
            .onFailure {
                android.util.Log.w(
                    "HeyCyanGattClient",
                    "disconnect() threw; forcing GATT close attempt=$activeAttemptId",
                    it
                )
                closeCurrentGatt("disconnect exception")
                _state.value = DriverState.Disconnected
            }
    }

    private fun discoverServices(gatt: BluetoothGatt) {
        if (gatt !== this.gatt) return
        if (serviceDiscoveryStarted) return

        serviceDiscoveryStarted = true
        if (!gatt.discoverServices()) {
            failGatt(
                gatt,
                RuntimeException(
                    "Unable to start service discovery attempt=$activeAttemptId"
                )
            )
        }
    }

    private fun writeNextDescriptor(gatt: BluetoothGatt?) {
        if (gatt == null || gatt !== this.gatt || descriptorWriteInFlight != null) return

        val next = if (pendingDescriptorWrites.isEmpty()) null else pendingDescriptorWrites.removeFirst()

        if (next == null) {
            _state.value = DriverState.Ready
            tracer.onReady()
            android.util.Log.i(
                "HeyCyanGattClient",
                "GATT ready attempt=$activeAttemptId characteristics=${characteristics.size}"
            )
            return
        }

        descriptorWriteInFlight = next
        val supportsNotifications = next.characteristic.properties and
            BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0
        next.descriptor.value = if (supportsNotifications) {
            BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
        } else {
            BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
        }

        val accepted = runCatching {
            gatt.writeDescriptor(next.descriptor)
        }.getOrElse {
            android.util.Log.w(
                "HeyCyanGattClient",
                "writeDescriptor threw attempt=$activeAttemptId " +
                    "uuid=${next.descriptor.uuid}",
                it
            )
            false
        }

        if (!accepted) {
            tracer.onDescriptorWrite(next.descriptor.uuid, BluetoothGatt.GATT_FAILURE)
            descriptorWriteInFlight = null
            writeNextDescriptor(gatt)
        }
    }

    private fun emitNotification(characteristic: BluetoothGattCharacteristic, value: ByteArray) {
        if (characteristics[characteristic.uuid] == null) return

        val payload = value.copyOf()
        HeyCyanProtocolCapture.instance.recordFrame(
            characteristicUuid = characteristic.uuid,
            payload = payload,
            direction = PacketDirection.RX
        )
        tracer.onRx(characteristic.uuid, payload)
        _incomingBytes.tryEmit(characteristic.uuid to payload)
    }

    private fun failGatt(gatt: BluetoothGatt, cause: Throwable) {
        android.util.Log.e(
            "HeyCyanGattClient",
            "GATT failure attempt=$activeAttemptId: ${cause.message}",
            cause
        )
        _state.value = DriverState.Error(cause)
        clearGatt(gatt)
    }

    private fun closeCurrentGatt(reason: String) {
        val current = gatt ?: return
        android.util.Log.i(
            "HeyCyanGattClient",
            "Closing GATT attempt=$activeAttemptId reason=$reason"
        )
        gatt = null
        resetConnectionState()
        runCatching { current.disconnect() }
        runCatching { current.close() }
    }

    private fun resetConnectionState() {
        pendingDescriptorWrites.clear()
        descriptorWriteInFlight = null
        characteristics.clear()
        serviceDiscoveryStarted = false
    }

    private fun clearGatt(gattToClose: BluetoothGatt?) {
        resetConnectionState()
        if (this.gatt === gattToClose) {
            this.gatt = null
        }
        runCatching { gattToClose?.close() }
    }
}
