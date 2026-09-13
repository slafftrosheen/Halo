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
import com.halo.drivers.heycyan.codec.CaptureGattStage
import com.halo.drivers.heycyan.codec.CaptureStagePhase
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

    /**
     * Owns one Android connectGatt generation. The callback closes over this object,
     * so callbacks from a retired connection can never be mistaken for a newer one.
     */
    private class ConnectionAttempt(val id: Long) {
        @Volatile
        var gatt: BluetoothGatt? = null

        @Volatile
        var terminal: Boolean = false
    }

    private val bluetoothManager by lazy { context?.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager }
    private val adapter by lazy { bluetoothManager?.adapter }
    private var gatt: BluetoothGatt? = null
    private var connectionAttemptId = 0L
    private var activeAttempt: ConnectionAttempt? = null
    private var activeAttemptId = 0L
    private var serviceDiscoveryStarted = false
    private var descriptorWriteInFlight: PendingDescriptorWrite? = null
    private val pendingDescriptorWrites = ArrayDeque<PendingDescriptorWrite>()
    private val characteristics = mutableMapOf<UUID, BluetoothGattCharacteristic>()

    private val _state = MutableStateFlow<DriverState>(DriverState.Disconnected)
    val state: StateFlow<DriverState> = _state.asStateFlow()
    private val _incomingBytes = MutableSharedFlow<Pair<UUID, ByteArray>>(extraBufferCapacity = 64)
    val incomingBytes: SharedFlow<Pair<UUID, ByteArray>> = _incomingBytes.asSharedFlow()

    private fun callbackFor(attempt: ConnectionAttempt) = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(callbackGatt: BluetoothGatt, status: Int, newState: Int) {
            tracer.onConnectionStateChanged(status, newState)
            android.util.Log.i(
                "HeyCyanGattClient",
                "onConnectionStateChange: attempt=${attempt.id} status=$status newState=$newState active=${activeAttempt?.id}"
            )

            if (!bindOrValidateAttemptGatt(attempt, callbackGatt)) {
                ignoreStaleCallback(attempt, callbackGatt, "connection-state")
                return
            }

            if (status != BluetoothGatt.GATT_SUCCESS) {
                tracer.onGattStage(
                    attempt.id,
                    CaptureGattStage.CONNECT,
                    CaptureStagePhase.FAILED,
                    status,
                    "newState=$newState ${HeyCyanGattErrorClassifier.describe(status)}"
                )
                failGatt(
                    callbackGatt,
                    RuntimeException("GATT connection error: status=$status attempt=${attempt.id}"),
                    closeImmediately = true,
                    attempt = attempt
                )
                return
            }

            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> handleConnected(callbackGatt, attempt)
                BluetoothProfile.STATE_DISCONNECTED -> {
                    tracer.onGattStage(
                        attempt.id,
                        CaptureGattStage.TEARDOWN,
                        CaptureStagePhase.SUCCEEDED,
                        status,
                        "remote disconnected"
                    )
                    clearGatt(callbackGatt, attempt)
                    _state.value = DriverState.Disconnected
                }
            }
        }

        override fun onMtuChanged(callbackGatt: BluetoothGatt, mtu: Int, status: Int) {
            if (!isCurrentAttempt(attempt, callbackGatt)) return
            android.util.Log.i("HeyCyanGattClient", "onMtuChanged: attempt=${attempt.id} mtu=$mtu status=$status")
            if (status == BluetoothGatt.GATT_SUCCESS) {
                tracer.onMtuChanged(mtu)
                tracer.onGattStage(attempt.id, CaptureGattStage.MTU, CaptureStagePhase.SUCCEEDED, status, "mtu=$mtu")
            } else {
                tracer.onGattStage(attempt.id, CaptureGattStage.MTU, CaptureStagePhase.FAILED, status, "mtu=$mtu")
            }
            discoverServices(callbackGatt, attempt)
        }

        override fun onServicesDiscovered(callbackGatt: BluetoothGatt, status: Int) {
            if (!isCurrentAttempt(attempt, callbackGatt)) return
            android.util.Log.i("HeyCyanGattClient", "onServicesDiscovered: attempt=${attempt.id} status=$status")
            if (status != BluetoothGatt.GATT_SUCCESS) {
                tracer.onGattStage(attempt.id, CaptureGattStage.SERVICE_DISCOVERY, CaptureStagePhase.FAILED, status)
                failGatt(
                    callbackGatt,
                    RuntimeException("Service discovery failed: status=$status attempt=${attempt.id}"),
                    attempt = attempt
                )
                return
            }
            tracer.onGattStage(
                attempt.id,
                CaptureGattStage.SERVICE_DISCOVERY,
                CaptureStagePhase.SUCCEEDED,
                status,
                "serviceCount=${callbackGatt.services.size}"
            )
            serviceDiscoveryStarted = true
            pendingDescriptorWrites.clear()
            descriptorWriteInFlight = null
            for (service in callbackGatt.services) {
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
                    val supportsNotifications =
                        characteristic.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0
                    val supportsIndications =
                        characteristic.properties and BluetoothGattCharacteristic.PROPERTY_INDICATE != 0
                    val cccd = characteristic.getDescriptor(CCCD_UUID)
                    val charUuid = characteristic.uuid.toString().lowercase()
                    val isRelevantNotifyChar =
                        charUuid.contains("de5bf729") ||
                            charUuid.startsWith("0000fee3") ||
                            charUuid.startsWith("6e400003") ||
                            charUuid.startsWith("0000ae02") ||
                            charUuid.startsWith("0000ae04")
                    if ((supportsNotifications || supportsIndications) && cccd != null && isRelevantNotifyChar) {
                        callbackGatt.setCharacteristicNotification(characteristic, true)
                        pendingDescriptorWrites.add(PendingDescriptorWrite(characteristic, cccd))
                    }
                }
            }
            writeNextDescriptor(callbackGatt, attempt)
        }

        override fun onDescriptorWrite(
            callbackGatt: BluetoothGatt?,
            descriptor: BluetoothGattDescriptor?,
            status: Int
        ) {
            val currentGatt = callbackGatt ?: attempt.gatt
            if (descriptor == null || currentGatt == null || !isCurrentAttempt(attempt, currentGatt)) return
            tracer.onDescriptorWrite(descriptor.uuid, status)
            if (descriptorWriteInFlight?.descriptor !== descriptor) return
            val inFlight = descriptorWriteInFlight
            descriptorWriteInFlight = null
            if (status != BluetoothGatt.GATT_SUCCESS) {
                tracer.onGattStage(
                    attempt.id,
                    CaptureGattStage.CCCD,
                    CaptureStagePhase.FAILED,
                    status,
                    "characteristic=${inFlight?.characteristic?.uuid} descriptor=${descriptor.uuid}"
                )
                failGatt(
                    currentGatt,
                    RuntimeException("CCCD write failed: status=$status attempt=${attempt.id} uuid=${descriptor.uuid}"),
                    attempt = attempt
                )
                return
            }
            tracer.onGattStage(
                attempt.id,
                CaptureGattStage.CCCD,
                CaptureStagePhase.SUCCEEDED,
                status,
                "characteristic=${inFlight?.characteristic?.uuid} descriptor=${descriptor.uuid}"
            )
            writeNextDescriptor(currentGatt, attempt)
        }

        override fun onCharacteristicWrite(
            callbackGatt: BluetoothGatt?,
            characteristic: BluetoothGattCharacteristic?,
            status: Int
        ) {
            if (characteristic == null || callbackGatt == null || !isCurrentAttempt(attempt, callbackGatt)) return
            android.util.Log.d(
                "HeyCyanGattClient",
                "onCharacteristicWrite: attempt=${attempt.id} uuid=${characteristic.uuid} status=$status"
            )
            tracer.onCharacteristicWrite(characteristic.uuid, status)
        }

        @Suppress("DEPRECATION")
        override fun onCharacteristicChanged(callbackGatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            if (!isCurrentAttempt(attempt, callbackGatt)) return
            characteristic.value?.let { emitNotification(characteristic, it) }
        }

        override fun onCharacteristicChanged(
            callbackGatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            if (!isCurrentAttempt(attempt, callbackGatt)) return
            emitNotification(characteristic, value)
        }
    }

    fun markScanning() {
        if (_state.value == DriverState.Disconnected || _state.value is DriverState.Error) {
            _state.value = DriverState.Scanning
        }
    }

    fun fail(cause: Throwable) {
        val current = gatt
        val attempt = activeAttempt
        if (current != null) {
            tracer.onGattStage(
                attempt?.id ?: activeAttemptId,
                CaptureGattStage.TEARDOWN,
                CaptureStagePhase.STARTED,
                detail = "forced failure cleanup"
            )
            clearGatt(current, attempt)
            tracer.onGattStage(
                attempt?.id ?: activeAttemptId,
                CaptureGattStage.TEARDOWN,
                CaptureStagePhase.SUCCEEDED,
                detail = "forced failure cleanup complete"
            )
        }
        _state.value = DriverState.Error(cause)
    }

    fun connect(macAddress: String) {
        if (_state.value != DriverState.Disconnected &&
            _state.value != DriverState.Scanning &&
            _state.value !is DriverState.Error
        ) {
            android.util.Log.d("HeyCyanGattClient", "Skipping connect, already in state: ${_state.value}")
            return
        }
        val device = try {
            adapter?.getRemoteDevice(macAddress) ?: throw IllegalStateException("BluetoothAdapter unavailable")
        } catch (error: Throwable) {
            _state.value = DriverState.Error(error)
            return
        }

        retireCurrentGatt()
        connectionAttemptId += 1L
        activeAttemptId = connectionAttemptId
        val attempt = ConnectionAttempt(activeAttemptId)
        activeAttempt = attempt
        gatt = null
        resetConnectionState()
        _state.value = DriverState.Connecting
        tracer.onGattStage(
            attempt.id,
            CaptureGattStage.CONNECT,
            CaptureStagePhase.STARTED,
            detail = "address=$macAddress transport=LE"
        )
        android.util.Log.i(
            "HeyCyanGattClient",
            "Initiating GATT connection: attempt=${attempt.id} address=$macAddress transport=LE"
        )

        val returnedGatt = device.connectGatt(
            context,
            false,
            callbackFor(attempt),
            BluetoothDevice.TRANSPORT_LE
        )

        if (activeAttempt !== attempt || attempt.terminal) {
            // A synchronous/very-early terminal callback already retired this generation.
            if (returnedGatt != null && returnedGatt !== attempt.gatt) runCatching { returnedGatt.close() }
            return
        }

        if (returnedGatt == null) {
            attempt.terminal = true
            activeAttempt = null
            tracer.onGattStage(
                attempt.id,
                CaptureGattStage.CONNECT,
                CaptureStagePhase.FAILED,
                detail = "connectGatt returned null"
            )
            _state.value = DriverState.Error(
                RuntimeException("Unable to create GATT connection attempt=${attempt.id}")
            )
            return
        }

        val callbackGatt = attempt.gatt
        if (callbackGatt != null && callbackGatt !== returnedGatt) {
            android.util.Log.e(
                "HeyCyanGattClient",
                "connectGatt returned a different GATT than callback for attempt=${attempt.id}; retiring attempt"
            )
            runCatching { returnedGatt.close() }
            failGatt(
                callbackGatt,
                RuntimeException("Mismatched GATT instance for attempt=${attempt.id}"),
                attempt = attempt
            )
            return
        }

        attempt.gatt = returnedGatt
        gatt = returnedGatt
    }

    suspend fun awaitReady(timeoutMillis: Long = 30_000L) {
        val terminalState = withTimeout(timeoutMillis) {
            state.filter { it == DriverState.Ready || it is DriverState.Error || it == DriverState.Disconnected }.first()
        }
        when (terminalState) {
            DriverState.Ready -> Unit
            is DriverState.Error -> throw terminalState.cause
            DriverState.Disconnected -> throw IllegalStateException(
                "GATT disconnected before discovery completed (attempt=$activeAttemptId)"
            )
            else -> error("Unexpected terminal GATT state: $terminalState")
        }
    }

    suspend fun awaitDisconnected(timeoutMillis: Long = 5_000L) {
        if (_state.value == DriverState.Disconnected) return
        withTimeoutOrNull(timeoutMillis) {
            state.filter { it == DriverState.Disconnected }.first()
        } ?: run {
            val current = gatt
            val attempt = activeAttempt
            if (current != null) {
                tracer.onGattStage(
                    attempt?.id ?: activeAttemptId,
                    CaptureGattStage.TEARDOWN,
                    CaptureStagePhase.STARTED,
                    detail = "disconnect timeout forced close"
                )
            }
            clearGatt(current, attempt)
            if (current != null) {
                tracer.onGattStage(
                    attempt?.id ?: activeAttemptId,
                    CaptureGattStage.TEARDOWN,
                    CaptureStagePhase.SUCCEEDED,
                    detail = "disconnect timeout forced close complete"
                )
            }
            _state.value = DriverState.Disconnected
        }
    }

    open fun write(characteristicUuid: UUID, payload: ByteArray): Boolean {
        val currentGatt = gatt ?: return false
        val characteristic = characteristics[characteristicUuid] ?: return false
        val writeType = if (
            characteristic.properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE != 0
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
        return currentGatt.writeCharacteristic(characteristic)
    }

    fun findCharacteristicByProperty(property: Int): BluetoothGattCharacteristic? =
        characteristics.values.firstOrNull { it.properties and property != 0 }

    fun disconnect() {
        val currentGatt = gatt
        val attempt = activeAttempt
        if (currentGatt == null) {
            clearGatt(null, attempt)
            _state.value = DriverState.Disconnected
            return
        }
        tracer.onGattStage(
            attempt?.id ?: activeAttemptId,
            CaptureGattStage.TEARDOWN,
            CaptureStagePhase.STARTED,
            detail = "disconnect requested"
        )
        if (_state.value != DriverState.Disconnected) _state.value = DriverState.Disconnecting
        runCatching { currentGatt.disconnect() }
    }

    private fun bindOrValidateAttemptGatt(attempt: ConnectionAttempt, callbackGatt: BluetoothGatt): Boolean {
        if (activeAttempt !== attempt || attempt.terminal) return false
        val bound = attempt.gatt
        if (bound == null) {
            // Android may invoke the callback before connectGatt() returns. Because this callback
            // is scoped to the attempt, binding here is unambiguous and safe.
            attempt.gatt = callbackGatt
            gatt = callbackGatt
            return true
        }
        return bound === callbackGatt && gatt === callbackGatt
    }

    private fun isCurrentAttempt(attempt: ConnectionAttempt, candidate: BluetoothGatt): Boolean =
        activeAttempt === attempt && !attempt.terminal && attempt.gatt === candidate && gatt === candidate

    private fun ignoreStaleCallback(attempt: ConnectionAttempt, callbackGatt: BluetoothGatt, stage: String) {
        android.util.Log.w(
            "HeyCyanGattClient",
            "Ignoring stale $stage callback: attempt=${attempt.id} active=${activeAttempt?.id}"
        )
        if (attempt.gatt === callbackGatt || activeAttempt !== attempt) {
            runCatching { callbackGatt.close() }
        }
    }

    private fun handleConnected(currentGatt: BluetoothGatt, attempt: ConnectionAttempt) {
        if (!isCurrentAttempt(attempt, currentGatt)) {
            android.util.Log.w(
                "HeyCyanGattClient",
                "Ignoring CONNECTED handling for retired attempt=${attempt.id}"
            )
            return
        }
        tracer.onGattStage(
            attempt.id,
            CaptureGattStage.CONNECT,
            CaptureStagePhase.SUCCEEDED,
            BluetoothGatt.GATT_SUCCESS,
            "newState=${BluetoothProfile.STATE_CONNECTED}"
        )
        _state.value = DriverState.Connected
        serviceDiscoveryStarted = false
        pendingDescriptorWrites.clear()
        descriptorWriteInFlight = null
        tracer.onMtuRequested(REQUESTED_MTU)
        tracer.onGattStage(
            attempt.id,
            CaptureGattStage.MTU,
            CaptureStagePhase.STARTED,
            detail = "requestedMtu=$REQUESTED_MTU"
        )
        if (!currentGatt.requestMtu(REQUESTED_MTU)) {
            tracer.onGattStage(
                attempt.id,
                CaptureGattStage.MTU,
                CaptureStagePhase.FAILED,
                detail = "requestMtu returned false"
            )
            discoverServices(currentGatt, attempt)
        }
    }

    private fun discoverServices(gatt: BluetoothGatt, attempt: ConnectionAttempt) {
        if (!isCurrentAttempt(attempt, gatt) || serviceDiscoveryStarted) return
        serviceDiscoveryStarted = true
        tracer.onGattStage(attempt.id, CaptureGattStage.SERVICE_DISCOVERY, CaptureStagePhase.STARTED)
        if (!gatt.discoverServices()) {
            tracer.onGattStage(
                attempt.id,
                CaptureGattStage.SERVICE_DISCOVERY,
                CaptureStagePhase.FAILED,
                detail = "discoverServices returned false"
            )
            failGatt(
                gatt,
                RuntimeException("Unable to start service discovery attempt=${attempt.id}"),
                attempt = attempt
            )
        }
    }

    private fun writeNextDescriptor(gatt: BluetoothGatt?, attempt: ConnectionAttempt) {
        if (gatt == null || !isCurrentAttempt(attempt, gatt) || descriptorWriteInFlight != null) return
        val next = if (pendingDescriptorWrites.isEmpty()) null else pendingDescriptorWrites.removeFirst()
        if (next == null) {
            _state.value = DriverState.Ready
            tracer.onGattStage(attempt.id, CaptureGattStage.READY, CaptureStagePhase.SUCCEEDED)
            tracer.onReady()
            android.util.Log.i("HeyCyanGattClient", "GATT ready: attempt=${attempt.id}")
            return
        }
        descriptorWriteInFlight = next
        val supportsNotifications =
            next.characteristic.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0
        next.descriptor.value = if (supportsNotifications) {
            BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
        } else {
            BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
        }
        tracer.onGattStage(
            attempt.id,
            CaptureGattStage.CCCD,
            CaptureStagePhase.STARTED,
            detail = "characteristic=${next.characteristic.uuid} descriptor=${next.descriptor.uuid}"
        )
        android.util.Log.d(
            "HeyCyanGattClient",
            "Writing CCCD: attempt=${attempt.id} characteristic=${next.characteristic.uuid} descriptor=${next.descriptor.uuid}"
        )
        if (!gatt.writeDescriptor(next.descriptor)) {
            tracer.onDescriptorWrite(next.descriptor.uuid, BluetoothGatt.GATT_FAILURE)
            tracer.onGattStage(
                attempt.id,
                CaptureGattStage.CCCD,
                CaptureStagePhase.FAILED,
                BluetoothGatt.GATT_FAILURE,
                "characteristic=${next.characteristic.uuid} descriptor=${next.descriptor.uuid} writeDescriptor returned false"
            )
            descriptorWriteInFlight = null
            failGatt(
                gatt,
                RuntimeException("Unable to start CCCD write attempt=${attempt.id} uuid=${next.descriptor.uuid}"),
                attempt = attempt
            )
        }
    }

    private fun emitNotification(characteristic: BluetoothGattCharacteristic, value: ByteArray) {
        val payload = value.copyOf()
        HeyCyanProtocolCapture.instance.recordFrame(
            characteristicUuid = characteristic.uuid,
            payload = payload,
            direction = PacketDirection.RX
        )
        tracer.onRx(characteristic.uuid, payload)
        _incomingBytes.tryEmit(characteristic.uuid to payload)
    }

    private fun failGatt(
        gatt: BluetoothGatt,
        cause: Throwable,
        closeImmediately: Boolean = true,
        attempt: ConnectionAttempt? = activeAttempt
    ) {
        if (attempt == null || !isCurrentAttempt(attempt, gatt)) {
            runCatching { gatt.close() }
            return
        }
        _state.value = DriverState.Error(cause)
        tracer.onGattStage(
            attempt.id,
            CaptureGattStage.TEARDOWN,
            CaptureStagePhase.STARTED,
            detail = "error cleanup: ${cause.message}"
        )
        if (closeImmediately) clearGatt(gatt, attempt)
        tracer.onGattStage(
            attempt.id,
            CaptureGattStage.TEARDOWN,
            CaptureStagePhase.SUCCEEDED,
            detail = if (closeImmediately) "error cleanup complete" else "error retained"
        )
    }

    private fun retireCurrentGatt() {
        val retired = gatt
        val attempt = activeAttempt
        if (attempt != null) attempt.terminal = true
        activeAttempt = null
        if (retired == null) return
        tracer.onGattStage(
            attempt?.id ?: activeAttemptId,
            CaptureGattStage.TEARDOWN,
            CaptureStagePhase.STARTED,
            detail = "retiring prior GATT before reconnect"
        )
        android.util.Log.i(
            "HeyCyanGattClient",
            "Closing retired GATT before new attempt: attempt=${attempt?.id ?: activeAttemptId}"
        )
        runCatching { retired.disconnect() }
        runCatching { retired.close() }
        tracer.onGattStage(
            attempt?.id ?: activeAttemptId,
            CaptureGattStage.TEARDOWN,
            CaptureStagePhase.SUCCEEDED,
            detail = "retired prior GATT"
        )
        gatt = null
    }

    private fun resetConnectionState() {
        serviceDiscoveryStarted = false
        pendingDescriptorWrites.clear()
        descriptorWriteInFlight = null
        characteristics.clear()
    }

    private fun clearGatt(gattToClose: BluetoothGatt?, attempt: ConnectionAttempt? = activeAttempt) {
        resetConnectionState()
        if (attempt != null) attempt.terminal = true
        if (activeAttempt === attempt) activeAttempt = null
        if (this.gatt === gattToClose || gattToClose == null) this.gatt = null
        if (attempt?.gatt === gattToClose || gattToClose == null) attempt?.gatt = null
        if (gattToClose != null) runCatching { gattToClose.close() }
    }
}
