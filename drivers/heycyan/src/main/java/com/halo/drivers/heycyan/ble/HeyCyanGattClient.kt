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
import com.halo.sdk.driver.DriverState
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

/**
 * Handles the low-level GATT connection and IO to the HeyCyan glasses.
 */
@SuppressLint("MissingPermission") // Caller (HostGraph) is responsible for ensuring permissions
class HeyCyanGattClient(private val context: Context) {

    companion object {
        val HEYCYAN_SERVICE_UUID: UUID = UUID.fromString("7905FFF0-B5CE-4E99-A40F-4B1E122D00D0")
        val HEYCYAN_CHARACTERISTIC_UUID: UUID = UUID.fromString("19B10001-E8F2-537E-4F6C-D104768A1214")
        private val CCCD_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    }

    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val adapter = bluetoothManager.adapter
    
    private var gatt: BluetoothGatt? = null
    private var characteristic: BluetoothGattCharacteristic? = null

    private val _state = MutableStateFlow<DriverState>(DriverState.Disconnected)
    val state: StateFlow<DriverState> = _state.asStateFlow()

    private val _incomingBytes = MutableSharedFlow<ByteArray>(extraBufferCapacity = 64)
    val incomingBytes: SharedFlow<ByteArray> = _incomingBytes.asSharedFlow()

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                _state.value = DriverState.Error(RuntimeException("GATT Error: \$status"))
                gatt.close()
                this@HeyCyanGattClient.gatt = null
                return
            }

            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    _state.value = DriverState.Connected
                    gatt.discoverServices() // Start service discovery
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    _state.value = DriverState.Disconnected
                    gatt.close()
                    this@HeyCyanGattClient.gatt = null
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                val service = gatt.getService(HEYCYAN_SERVICE_UUID)
                if (service != null) {
                    characteristic = service.getCharacteristic(HEYCYAN_CHARACTERISTIC_UUID)
                    if (characteristic != null) {
                        // Enable notifications
                        gatt.setCharacteristicNotification(characteristic, true)
                        val descriptor = characteristic?.getDescriptor(CCCD_UUID)
                        if (descriptor != null) {
                            descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                            gatt.writeDescriptor(descriptor)
                        } else {
                            _state.value = DriverState.Ready // No descriptor, just ready
                        }
                    } else {
                        _state.value = DriverState.Error(RuntimeException("Characteristic not found"))
                    }
                } else {
                    _state.value = DriverState.Error(RuntimeException("Service not found"))
                }
            } else {
                _state.value = DriverState.Error(RuntimeException("Service discovery failed: \$status"))
            }
        }

        override fun onDescriptorWrite(gatt: BluetoothGatt?, descriptor: BluetoothGattDescriptor?, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                // Now we are fully ready for IO
                _state.value = DriverState.Ready
            }
        }

        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            if (characteristic.uuid == HEYCYAN_CHARACTERISTIC_UUID) {
                val payload = characteristic.value
                if (payload != null) {
                    _incomingBytes.tryEmit(payload)
                }
            }
        }
    }

    fun connect(macAddress: String) {
        if (_state.value != DriverState.Disconnected && _state.value !is DriverState.Error) {
            return // Already connecting or connected
        }
        
        _state.value = DriverState.Connecting
        val device: BluetoothDevice = adapter.getRemoteDevice(macAddress)
        gatt = device.connectGatt(context, false, gattCallback)
    }

    fun write(payload: ByteArray): Boolean {
        val gatt = this.gatt ?: return false
        val char = this.characteristic ?: return false
        
        char.value = payload
        return gatt.writeCharacteristic(char)
    }

    fun disconnect() {
        gatt?.disconnect()
    }
}
