package com.halo.drivers.heycyan.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothStatusCodes
import android.os.Build

interface GattPlatformWriter {
    fun writeCharacteristic(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        value: ByteArray,
        writeType: Int
    ): Int

    fun writeDescriptor(
        gatt: BluetoothGatt,
        descriptor: BluetoothGattDescriptor,
        value: ByteArray
    ): Int
}

@SuppressLint("MissingPermission")
object AndroidGattPlatformWriter : GattPlatformWriter {
    @Suppress("DEPRECATION")
    override fun writeCharacteristic(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        value: ByteArray,
        writeType: Int
    ): Int = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        gatt.writeCharacteristic(characteristic, value.copyOf(), writeType)
    } else {
        characteristic.writeType = writeType
        characteristic.value = value.copyOf()
        if (gatt.writeCharacteristic(characteristic)) BluetoothStatusCodes.SUCCESS
        else BluetoothGatt.GATT_FAILURE
    }

    @Suppress("DEPRECATION")
    override fun writeDescriptor(
        gatt: BluetoothGatt,
        descriptor: BluetoothGattDescriptor,
        value: ByteArray
    ): Int = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        gatt.writeDescriptor(descriptor, value.copyOf())
    } else {
        descriptor.value = value.copyOf()
        if (gatt.writeDescriptor(descriptor)) BluetoothStatusCodes.SUCCESS
        else BluetoothGatt.GATT_FAILURE
    }
}
