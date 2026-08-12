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

public class ReadRssiRequest
extends BaseRequest {
    private static ReadRssiRequest readRequest = new ReadRssiRequest();

    public static ReadRssiRequest getInstance() {
        return readRequest;
    }

    private ReadRssiRequest() {
        super(null, null);
    }

    @Override
    public boolean execute(BluetoothGatt bluetoothGatt, BluetoothGattCharacteristic bluetoothGattCharacteristic) {
        return bluetoothGatt.readRemoteRssi();
    }

    @Override
    public boolean needInitCharacteristic() {
        return false;
    }
}

