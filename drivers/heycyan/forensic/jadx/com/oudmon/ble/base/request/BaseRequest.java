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
import java.util.UUID;

public abstract class BaseRequest {
    public boolean writeRequest;
    private UUID serviceUuid;
    private UUID charUuid;

    public BaseRequest(UUID serviceUuid, UUID charUuid) {
        this.serviceUuid = serviceUuid;
        this.charUuid = charUuid;
    }

    public UUID getServiceUuid() {
        return this.serviceUuid;
    }

    public UUID getCharUuid() {
        return this.charUuid;
    }

    public abstract boolean execute(BluetoothGatt var1, BluetoothGattCharacteristic var2);

    public boolean needInitCharacteristic() {
        return true;
    }
}

