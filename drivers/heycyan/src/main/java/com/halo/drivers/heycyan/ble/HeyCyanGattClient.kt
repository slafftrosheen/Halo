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

/**
 * Handles low-level GATT connection, complete service discovery, and raw BLE IO.
 *
 * No UUID is assumed here. Every service, characteristic, and descriptor returned
 * by Android is reported to the tracer before any notification subscription is made.
 */
@SuppressLint("MissingPermission") // Caller (HostGraph) is responsible for ensuring permissions
class HeyCyanGattClient(
    private val context: Context,
    private val tracer: PacketTracer
) {

    companion object {
        private val CCCD_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
        private const val REQUESTED_MTU = 517
    }

    private data class PendingDescriptorWrite(
        val characteristic: BluetoothGattCharacteristic,
        val descriptor: BluetoothGattDescriptor
    )

    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val adapter = bluetoothManager.adapter

    private var gatt: BluetoothGatt? = null
    private var serviceDiscoveryStarted = false
    private var descriptorWriteInFlight: PendingDescriptorWrite? = null
    private val pendingDescriptorWrites = ArrayDeque<PendingDescriptorWrite>()

    // Store characteristics by UUID for future writes. Protocol command routing is
    // intentionally not implemented until captures identify the correct endpoint.
    private val characteristics = mutableMapOf<UUID, BluetoothGattCharacteristic>()

    private val _state = MutableStateFlow<DriverState>(DriverState.Disconnected)
    val state: StateFlow<DriverState> = _state.asStateFlow()

    private val _incomingBytes = MutableSharedFlow<Pair<UUID, ByteArray>>(extraBufferCapacity = 64)
    val incomingBytes: SharedFlow<Pair<UUID, ByteArray>> = _incomingBytes.asSharedFlow()

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            android.util.Log.i("HeyCyanGattClient", "onConnectionStateChange: status=$status, newState=$newState")
            tracer.onConnectionStateChanged(status, newState)
            if (status != BluetoothGatt.GATT_SUCCESS) {
                failGatt(gatt, RuntimeException("GATT connection error: $status"))
                return
            }

            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    this@HeyCyanGattClient.gatt = gatt
                    _state.value = DriverState.Connected
                    serviceDiscoveryStarted = false
                    pendingDescriptorWrites.clear()
                    descriptorWriteInFlight = null

                    // Request the largest MTU supported by Android. The effective
                    // value is reported by onMtuChanged before service discovery.
                    tracer.onMtuRequested(REQUESTED_MTU)
                    if (!gatt.requestMtu(REQUESTED_MTU)) {
                        discoverServices(gatt)
                    }
                }

                BluetoothProfile.STATE_DISCONNECTED -> {
                    clearGatt(gatt)
                    _state.value = DriverState.Disconnected
                }
            }
        }

        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                tracer.onMtuChanged(mtu)
            }
            discoverServices(gatt)
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            android.util.Log.i("HeyCyanGattClient", "onServicesDiscovered: status=$status")
            if (status != BluetoothGatt.GATT_SUCCESS) {
                failGatt(gatt, RuntimeException("Service discovery failed: $status"))
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

                    if ((supportsNotifications || supportsIndications) && cccd != null) {
                        gatt.setCharacteristicNotification(characteristic, true)
                        pendingDescriptorWrites.add(
                            PendingDescriptorWrite(characteristic, cccd)
                        )
                    }
                }
            }

            writeNextDescriptor(gatt)
        }

        override fun onDescriptorWrite(
            gatt: BluetoothGatt?,
            descriptor: BluetoothGattDescriptor?,
            status: Int
        ) {
            if (descriptor == null) return

            tracer.onDescriptorWrite(descriptor.uuid, status)
            if (descriptorWriteInFlight?.descriptor?.uuid != descriptor.uuid) return

            descriptorWriteInFlight = null
            writeNextDescriptor(gatt ?: this@HeyCyanGattClient.gatt)
        }

        @Suppress("DEPRECATION")
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            characteristic.value?.let { emitNotification(characteristic, it) }
        }

        // Android 13+ delivers the notification payload directly through this
        // overload. Keeping both callbacks supports the module's minSdk of 26.
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
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

    fun connect(macAddress: String) {
        if (_state.value != DriverState.Disconnected && 
            _state.value != DriverState.Scanning && 
            _state.value !is DriverState.Error) {
            android.util.Log.d("HeyCyanGattClient", "Skipping connect, already in state: ${_state.value}")
            return
        }

        android.util.Log.i("HeyCyanGattClient", "Connecting to $macAddress")
        _state.value = DriverState.Connecting
        val device: BluetoothDevice = try {
            adapter.getRemoteDevice(macAddress)
        } catch (error: Throwable) {
            _state.value = DriverState.Error(error)
            return
        }

        characteristics.clear()
        pendingDescriptorWrites.clear()
        descriptorWriteInFlight = null
        serviceDiscoveryStarted = false
        gatt = device.connectGatt(context, false, gattCallback)

        if (gatt == null) {
            _state.value = DriverState.Error(RuntimeException("Unable to create GATT connection"))
        }
    }

    suspend fun awaitReady(timeoutMillis: Long = 30_000L) {
        val terminalState = withTimeout(timeoutMillis) {
            state.filter { it == DriverState.Ready || it is DriverState.Error || it == DriverState.Disconnected }
                .first()
        }

        when (terminalState) {
            DriverState.Ready -> Unit
            is DriverState.Error -> throw terminalState.cause
            DriverState.Disconnected -> throw IllegalStateException("GATT disconnected before discovery completed")
            else -> error("Unexpected terminal GATT state: $terminalState")
        }
    }

    suspend fun awaitDisconnected(timeoutMillis: Long = 5_000L) {
        if (_state.value == DriverState.Disconnected) return

        withTimeoutOrNull(timeoutMillis) {
            state.filter { it == DriverState.Disconnected }.first()
        } ?: run {
            clearGatt(gatt)
            _state.value = DriverState.Disconnected
        }
    }

    fun write(characteristicUuid: UUID, payload: ByteArray): Boolean {
        val currentGatt = gatt ?: return false
        val characteristic = characteristics[characteristicUuid] ?: return false

        tracer.onTx(characteristicUuid, payload)
        characteristic.value = payload
        return currentGatt.writeCharacteristic(characteristic)
    }

    fun findCharacteristicByProperty(property: Int): BluetoothGattCharacteristic? {
        return characteristics.values.firstOrNull { it.properties and property != 0 }
    }

    fun disconnect() {
        val currentGatt = gatt
        if (currentGatt == null) {
            clearGatt(null)
            _state.value = DriverState.Disconnected
            return
        }

        if (_state.value != DriverState.Disconnected) {
            _state.value = DriverState.Disconnecting
        }
        currentGatt.disconnect()
    }

    private fun discoverServices(gatt: BluetoothGatt) {
        if (serviceDiscoveryStarted) return
        serviceDiscoveryStarted = true
        if (!gatt.discoverServices()) {
            failGatt(gatt, RuntimeException("Unable to start service discovery"))
        }
    }

    private fun writeNextDescriptor(gatt: BluetoothGatt?) {
        if (gatt == null || descriptorWriteInFlight != null) return

        val next = if (pendingDescriptorWrites.isEmpty()) {
            null
        } else {
            pendingDescriptorWrites.removeFirst()
        }

        if (next == null) {
            _state.value = DriverState.Ready
            tracer.onReady()
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

        if (!gatt.writeDescriptor(next.descriptor)) {
            tracer.onDescriptorWrite(next.descriptor.uuid, BluetoothGatt.GATT_FAILURE)
            descriptorWriteInFlight = null
            writeNextDescriptor(gatt)
        }
    }

    private fun emitNotification(characteristic: BluetoothGattCharacteristic, value: ByteArray) {
        val payload = value.copyOf()
        tracer.onRx(characteristic.uuid, payload)
        _incomingBytes.tryEmit(characteristic.uuid to payload)
    }

    private fun failGatt(gatt: BluetoothGatt, cause: Throwable) {
        _state.value = DriverState.Error(cause)
        clearGatt(gatt)
    }

    private fun clearGatt(gattToClose: BluetoothGatt?) {
        pendingDescriptorWrites.clear()
        descriptorWriteInFlight = null
        characteristics.clear()
        serviceDiscoveryStarted = false
        if (this.gatt === gattToClose || gattToClose == null) {
            this.gatt = null
        }
        gattToClose?.close()
    }
}