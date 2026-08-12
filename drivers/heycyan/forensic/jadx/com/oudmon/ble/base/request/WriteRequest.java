/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  android.bluetooth.BluetoothGatt
 *  android.bluetooth.BluetoothGattCharacteristic
 */
package com.oudmon.ble.base.request;

import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import com.oudmon.ble.base.request.BaseRequest;
import java.util.UUID;

public class WriteRequest
extends BaseRequest {
    private byte[] value;
    private boolean noRsp = false;

    public WriteRequest(UUID serviceUuid, UUID charUuid) {
        super(serviceUuid, charUuid);
        this.writeRequest = true;
    }

    private WriteRequest(UUID serviceUuid, UUID charUuid, boolean noResponse) {
        super(serviceUuid, charUuid);
        this.noRsp = noResponse;
        this.writeRequest = true;
    }

    public static WriteRequest getNoRspInstance(UUID serviceUuid, UUID charUuid) {
        return new WriteRequest(serviceUuid, charUuid, true);
    }

    public void setValue(byte[] value) {
        this.value = value;
    }

    public byte[] getValue() {
        return this.value;
    }

    @Override
    public boolean execute(BluetoothGatt bluetoothGatt, BluetoothGattCharacteristic bluetoothGattCharacteristic) {
        if (this.value == null) {
            return false;
        }
        try {
            bluetoothGattCharacteristic.setWriteType(this.noRsp ? 1 : 2);
            boolean setValue = bluetoothGattCharacteristic.setValue(this.value);
            boolean result = setValue && bluetoothGatt.writeCharacteristic(bluetoothGattCharacteristic);
            return result;
        }
        catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }
}

