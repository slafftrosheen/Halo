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
import kotlinx.coroutines.CancellationException
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

    private val bluetoothManager by lazy { context?.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager }
    private val adapter by lazy { bluetoothManager?.adapter }
    private var gatt: BluetoothGatt? = null
    private var connectionAttemptId = 0L
    private var activeAttemptId = 0L
    private var serviceDiscoveryStarted = false
    private var descriptorWriteInFlight: PendingDescriptorWrite? = null
    private val pendingDescriptorWrites = ArrayDeque<PendingDescriptorWrite>()
    private val characteristics = mutableMapOf<UUID, BluetoothGattCharacteristic>()

    private val _state = MutableStateFlow<DriverState>(DriverState.Disconnected)
    val state: StateFlow<DriverState> = _state.asStateFlow()
    private val _incomingBytes = MutableSharedFlow<Pair<UUID, ByteArray>>(extraBufferCapacity = 64)
    val incomingBytes: SharedFlow<Pair<UUID, ByteArray>> = _incomingBytes.asSharedFlow()

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            val attempt = if (gatt === this@HeyCyanGattClient.gatt) activeAttemptId else null
            tracer.onConnectionStateChanged(status, newState)
            android.util.Log.i("HeyCyanGattClient", "onConnectionStateChange: attempt=$attempt status=$status newState=$newState")
            if (gatt !== this@HeyCyanGattClient.gatt) {
                android.util.Log.w("HeyCyanGattClient", "Ignoring stale GATT callback for retired connection")
                runCatching { gatt.close() }
                return
            }

            if (status != BluetoothGatt.GATT_SUCCESS) {
                tracer.onGattStage(activeAttemptId, CaptureGattStage.CONNECT, CaptureStagePhase.FAILED, status, "newState=$newState ${HeyCyanGattErrorClassifier.describe(status)}")
                // Keep the public state failed, but let the owner decide whether
                // to retry. This avoids pretending every Android GATT error is a
                // protocol-level failure.
                failGatt(gatt, RuntimeException("GATT connection error: status=$status attempt=$activeAttemptId"), closeImmediately = true)
                return
            }

            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    tracer.onGattStage(activeAttemptId, CaptureGattStage.CONNECT, CaptureStagePhase.SUCCEEDED, status, "newState=$newState")
                    _state.value = DriverState.Connected
                    serviceDiscoveryStarted = false
                    pendingDescriptorWrites.clear()
                    descriptorWriteInFlight = null
                    tracer.onMtuRequested(REQUESTED_MTU)
                    tracer.onGattStage(activeAttemptId, CaptureGattStage.MTU, CaptureStagePhase.STARTED, detail = "requestedMtu=$REQUESTED_MTU")
                    if (!gatt.requestMtu(REQUESTED_MTU)) {
                        tracer.onGattStage(activeAttemptId, CaptureGattStage.MTU, CaptureStagePhase.FAILED, detail = "requestMtu returned false")
                        discoverServices(gatt)
                    }
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    tracer.onGattStage(activeAttemptId, CaptureGattStage.TEARDOWN, CaptureStagePhase.SUCCEEDED, status, "remote disconnected")
                    clearGatt(gatt)
                    _state.value = DriverState.Disconnected
                }
            }
        }

        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
            if (!isCurrentGatt(gatt)) return
            android.util.Log.i("HeyCyanGattClient", "onMtuChanged: attempt=$activeAttemptId mtu=$mtu status=$status")
            if (status == BluetoothGatt.GATT_SUCCESS) {
                tracer.onMtuChanged(mtu)
                tracer.onGattStage(activeAttemptId, CaptureGattStage.MTU, CaptureStagePhase.SUCCEEDED, status, "mtu=$mtu")
            } else {
                tracer.onGattStage(activeAttemptId, CaptureGattStage.MTU, CaptureStagePhase.FAILED, status, "mtu=$mtu")
            }
            discoverServices(gatt)
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (!isCurrentGatt(gatt)) return
            android.util.Log.i("HeyCyanGattClient", "onServicesDiscovered: attempt=$activeAttemptId status=$status")
            if (status != BluetoothGatt.GATT_SUCCESS) {
                tracer.onGattStage(activeAttemptId, CaptureGattStage.SERVICE_DISCOVERY, CaptureStagePhase.FAILED, status)
                failGatt(gatt, RuntimeException("Service discovery failed: status=$status attempt=$activeAttemptId"))
                return
            }
            tracer.onGattStage(activeAttemptId, CaptureGattStage.SERVICE_DISCOVERY, CaptureStagePhase.SUCCEEDED, status, "serviceCount=${gatt.services.size}")
            serviceDiscoveryStarted = true
            pendingDescriptorWrites.clear()
            descriptorWriteInFlight = null
            for (service in gatt.services) {
                tracer.onDiscoveredService(service.uuid, service.type)
                for (characteristic in service.characteristics) {
                    tracer.onDiscoveredCharacteristic(service.uuid, characteristic.uuid, characteristic.properties, characteristic.permissions)
                    characteristics[characteristic.uuid] = characteristic
                    for (descriptor in characteristic.descriptors) {
                        tracer.onDiscoveredDescriptor(service.uuid, characteristic.uuid, descriptor.uuid, descriptor.permissions)
                    }
                    val supportsNotifications = characteristic.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0
                    val supportsIndications = characteristic.properties and BluetoothGattCharacteristic.PROPERTY_INDICATE != 0
                    val cccd = characteristic.getDescriptor(CCCD_UUID)
                    val charUuid = characteristic.uuid.toString().lowercase()
                    val isRelevantNotifyChar = charUuid.contains("de5bf729") || charUuid.startsWith("0000fee3") || charUuid.startsWith("6e400003") || charUuid.startsWith("0000ae02") || charUuid.startsWith("0000ae04")
                    if ((supportsNotifications || supportsIndications) && cccd != null && isRelevantNotifyChar) {
                        gatt.setCharacteristicNotification(characteristic, true)
                        pendingDescriptorWrites.add(PendingDescriptorWrite(characteristic, cccd))
                    }
                }
            }
            writeNextDescriptor(gatt)
        }

        override fun onDescriptorWrite(gatt: BluetoothGatt?, descriptor: BluetoothGattDescriptor?, status: Int) {
            val currentGatt = gatt ?: this@HeyCyanGattClient.gatt
            if (descriptor == null || currentGatt == null || !isCurrentGatt(currentGatt)) return
            tracer.onDescriptorWrite(descriptor.uuid, status)
            if (descriptorWriteInFlight?.descriptor !== descriptor) return
            val inFlight = descriptorWriteInFlight
            descriptorWriteInFlight = null
            if (status != BluetoothGatt.GATT_SUCCESS) {
                tracer.onGattStage(activeAttemptId, CaptureGattStage.CCCD, CaptureStagePhase.FAILED, status, "characteristic=${inFlight?.characteristic?.uuid} descriptor=${descriptor.uuid}")
                failGatt(currentGatt, RuntimeException("CCCD write failed: status=$status attempt=$activeAttemptId uuid=${descriptor.uuid}"))
                return
            }
            tracer.onGattStage(activeAttemptId, CaptureGattStage.CCCD, CaptureStagePhase.SUCCEEDED, status, "characteristic=${inFlight?.characteristic?.uuid} descriptor=${descriptor.uuid}")
            writeNextDescriptor(currentGatt)
        }

        override fun onCharacteristicWrite(gatt: BluetoothGatt?, characteristic: BluetoothGattCharacteristic?, status: Int) {
            if (characteristic == null || gatt == null || !isCurrentGatt(gatt)) return
            android.util.Log.d("HeyCyanGattClient", "onCharacteristicWrite: attempt=$activeAttemptId uuid=${characteristic.uuid} status=$status")
            tracer.onCharacteristicWrite(characteristic.uuid, status)
        }

        @Suppress("DEPRECATION")
        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            if (!isCurrentGatt(gatt)) return
            characteristic.value?.let { emitNotification(characteristic, it) }
        }

        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray) {
            if (!isCurrentGatt(gatt)) return
            emitNotification(characteristic, value)
        }
    }

    fun markScanning() {
        if (_state.value == DriverState.Disconnected || _state.value is DriverState.Error) _state.value = DriverState.Scanning
    }

    fun fail(cause: Throwable) {
        val current = gatt
        if (current != null) {
            tracer.onGattStage(activeAttemptId, CaptureGattStage.TEARDOWN, CaptureStagePhase.STARTED, detail = "forced failure cleanup")
            clearGatt(current)
            tracer.onGattStage(activeAttemptId, CaptureGattStage.TEARDOWN, CaptureStagePhase.SUCCEEDED, detail = "forced failure cleanup complete")
        }
        _state.value = DriverState.Error(cause)
    }

    fun connect(macAddress: String) {
        if (_state.value != DriverState.Disconnected && _state.value != DriverState.Scanning && _state.value !is DriverState.Error) {
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
        gatt = null
        resetConnectionState()
        _state.value = DriverState.Connecting
        tracer.onGattStage(activeAttemptId, CaptureGattStage.CONNECT, CaptureStagePhase.STARTED, detail = "address=$macAddress transport=LE")
        android.util.Log.i("HeyCyanGattClient", "Initiating GATT connection: attempt=$activeAttemptId address=$macAddress transport=LE")
        val newGatt = device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
        gatt = newGatt
        if (newGatt == null) {
            tracer.onGattStage(activeAttemptId, CaptureGattStage.CONNECT, CaptureStagePhase.FAILED, detail = "connectGatt returned null")
            _state.value = DriverState.Error(RuntimeException("Unable to create GATT connection attempt=$activeAttemptId"))
        }
    }

    suspend fun awaitReady(timeoutMillis: Long = 30_000L) {
        val terminalState = withTimeout(timeoutMillis) {
            state.filter { it == DriverState.Ready || it is DriverState.Error || it == DriverState.Disconnected }.first()
        }
        when (terminalState) {
            DriverState.Ready -> Unit
            is DriverState.Error -> throw terminalState.cause
            DriverState.Disconnected -> throw IllegalStateException("GATT disconnected before discovery completed (attempt=$activeAttemptId)")
            else -> error("Unexpected terminal GATT state: $terminalState")
        }
    }

    suspend fun awaitDisconnected(timeoutMillis: Long = 5_000L) {
        if (_state.value == DriverState.Disconnected) return
        withTimeoutOrNull(timeoutMillis) { state.filter { it == DriverState.Disconnected }.first() } ?: run {
            val current = gatt
            if (current != null) tracer.onGattStage(activeAttemptId, CaptureGattStage.TEARDOWN, CaptureStagePhase.STARTED, detail = "disconnect timeout forced close")
            clearGatt(current)
            if (current != null) tracer.onGattStage(activeAttemptId, CaptureGattStage.TEARDOWN, CaptureStagePhase.SUCCEEDED, detail = "disconnect timeout forced close complete")
            _state.value = DriverState.Disconnected
        }
    }

    open fun write(characteristicUuid: UUID, payload: ByteArray): Boolean {
        val currentGatt = gatt ?: return false
        val characteristic = characteristics[characteristicUuid] ?: return false
        val writeType = if (characteristic.properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE != 0) "WRITE_NO_RESPONSE" else "WRITE"
        HeyCyanProtocolCapture.instance.recordFrame(characteristicUuid = characteristicUuid, payload = payload, direction = PacketDirection.TX, writeType = writeType)
        tracer.onTx(characteristicUuid, payload)
        characteristic.value = payload
        return currentGatt.writeCharacteristic(characteristic)
    }

    fun findCharacteristicByProperty(property: Int): BluetoothGattCharacteristic? = characteristics.values.firstOrNull { it.properties and property != 0 }

    fun disconnect() {
        val currentGatt = gatt
        if (currentGatt == null) {
            clearGatt(null)
            _state.value = DriverState.Disconnected
            return
        }
        tracer.onGattStage(activeAttemptId, CaptureGattStage.TEARDOWN, CaptureStagePhase.STARTED, detail = "disconnect requested")
        if (_state.value != DriverState.Disconnected) _state.value = DriverState.Disconnecting
        runCatching { currentGatt.disconnect() }
    }

    private fun isCurrentGatt(candidate: BluetoothGatt): Boolean = candidate === gatt

    private fun discoverServices(gatt: BluetoothGatt) {
        if (!isCurrentGatt(gatt) || serviceDiscoveryStarted) return
        serviceDiscoveryStarted = true
        tracer.onGattStage(activeAttemptId, CaptureGattStage.SERVICE_DISCOVERY, CaptureStagePhase.STARTED)
        if (!gatt.discoverServices()) {
            tracer.onGattStage(activeAttemptId, CaptureGattStage.SERVICE_DISCOVERY, CaptureStagePhase.FAILED, detail = "discoverServices returned false")
            failGatt(gatt, RuntimeException("Unable to start service discovery attempt=$activeAttemptId"))
        }
    }

    private fun writeNextDescriptor(gatt: BluetoothGatt?) {
        if (gatt == null || !isCurrentGatt(gatt) || descriptorWriteInFlight != null) return
        val next = if (pendingDescriptorWrites.isEmpty()) null else pendingDescriptorWrites.removeFirst()
        if (next == null) {
            _state.value = DriverState.Ready
            tracer.onGattStage(activeAttemptId, CaptureGattStage.READY, CaptureStagePhase.SUCCEEDED)
            tracer.onReady()
            android.util.Log.i("HeyCyanGattClient", "GATT ready: attempt=$activeAttemptId")
            return
        }
        descriptorWriteInFlight = next
        val supportsNotifications = next.characteristic.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0
        next.descriptor.value = if (supportsNotifications) BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE else BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
        tracer.onGattStage(activeAttemptId, CaptureGattStage.CCCD, CaptureStagePhase.STARTED, detail = "characteristic=${next.characteristic.uuid} descriptor=${next.descriptor.uuid}")
        android.util.Log.d("HeyCyanGattClient", "Writing CCCD: attempt=$activeAttemptId characteristic=${next.characteristic.uuid} descriptor=${next.descriptor.uuid}")
        if (!gatt.writeDescriptor(next.descriptor)) {
            tracer.onDescriptorWrite(next.descriptor.uuid, BluetoothGatt.GATT_FAILURE)
            tracer.onGattStage(activeAttemptId, CaptureGattStage.CCCD, CaptureStagePhase.FAILED, BluetoothGatt.GATT_FAILURE, "characteristic=${next.characteristic.uuid} descriptor=${next.descriptor.uuid} writeDescriptor returned false")
            descriptorWriteInFlight = null
            failGatt(gatt, RuntimeException("Unable to start CCCD write attempt=$activeAttemptId uuid=${next.descriptor.uuid}"))
        }
    }

    private fun emitNotification(characteristic: BluetoothGattCharacteristic, value: ByteArray) {
        val payload = value.copyOf()
        HeyCyanProtocolCapture.instance.recordFrame(characteristicUuid = characteristic.uuid, payload = payload, direction = PacketDirection.RX)
        tracer.onRx(characteristic.uuid, payload)
        _incomingBytes.tryEmit(characteristic.uuid to payload)
    }

    private fun failGatt(gatt: BluetoothGatt, cause: Throwable, closeImmediately: Boolean = true) {
        if (!isCurrentGatt(gatt)) {
            runCatching { gatt.close() }
            return
        }
        _state.value = DriverState.Error(cause)
        tracer.onGattStage(activeAttemptId, CaptureGattStage.TEARDOWN, CaptureStagePhase.STARTED, detail = "error cleanup: ${cause.message}")
        if (closeImmediately) clearGatt(gatt)
        tracer.onGattStage(activeAttemptId, CaptureGattStage.TEARDOWN, CaptureStagePhase.SUCCEEDED, detail = if (closeImmediately) "error cleanup complete" else "error retained")
    }

    private fun retireCurrentGatt() {
        val retired = gatt ?: return
        tracer.onGattStage(activeAttemptId, CaptureGattStage.TEARDOWN, CaptureStagePhase.STARTED, detail = "retiring prior GATT before reconnect")
        android.util.Log.i("HeyCyanGattClient", "Closing retired GATT before new attempt")
        runCatching { retired.disconnect() }
        runCatching { retired.close() }
        tracer.onGattStage(activeAttemptId, CaptureGattStage.TEARDOWN, CaptureStagePhase.SUCCEEDED, detail = "retired prior GATT")
        gatt = null
    }

    private fun resetConnectionState() {
        serviceDiscoveryStarted = false
        pendingDescriptorWrites.clear()
        descriptorWriteInFlight = null
        characteristics.clear()
    }

    private fun clearGatt(gattToClose: BluetoothGatt?) {
        resetConnectionState()
        if (this.gatt === gattToClose || gattToClose == null) this.gatt = null
        if (gattToClose != null) runCatching { gattToClose.close() }
    }
}