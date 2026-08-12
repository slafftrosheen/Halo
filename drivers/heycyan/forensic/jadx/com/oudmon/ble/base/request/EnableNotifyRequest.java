/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  android.bluetooth.BluetoothGatt
 *  android.bluetooth.BluetoothGattCharacteristic
 *  android.bluetooth.BluetoothGattDescriptor
 *  android.util.Log
 */
package com.oudmon.ble.base.request;

import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.util.Log;
import com.oudmon.ble.base.request.BaseRequest;
import java.util.UUID;

public class EnableNotifyRequest
extends BaseRequest {
    private UUID GATT_NOTIFY_CONFIG = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");
    private boolean isEnable = true;
    private ListenerCallback callback;

    public EnableNotifyRequest(UUID serviceUuid, UUID charUuid, ListenerCallback callback) {
        super(serviceUuid, charUuid);
        this.callback = callback;
    }

    public EnableNotifyRequest(UUID serviceUuid, UUID charUuid, boolean isEnable) {
        super(serviceUuid, charUuid);
        this.isEnable = isEnable;
    }

    @Override
    public boolean execute(BluetoothGatt bluetoothGatt, BluetoothGattCharacteristic bluetoothGattCharacteristic) {
        if (bluetoothGatt == null) {
            if (this.callback != null) {
                this.callback.enable(false);
            }
            return false;
        }
        boolean b = bluetoothGatt.setCharacteristicNotification(bluetoothGattCharacteristic, this.isEnable);
        if (!b) {
            Log.i((String)"GLASSES_LOG", (String)"open local notify failed");
            if (this.callback != null) {
                this.callback.enable(false);
            }
            return false;
        }
        BluetoothGattDescriptor descriptor = bluetoothGattCharacteristic.getDescriptor(this.GATT_NOTIFY_CONFIG);
        if (descriptor == null) {
            Log.i((String)"GLASSES_LOG", (String)"descriptor is null, execute failed");
            if (this.callback != null) {
                this.callback.enable(false);
            }
            return false;
        }
        descriptor.setValue(this.isEnable ? BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE : BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE);
        boolean flag = bluetoothGatt.writeDescriptor(descriptor);
        if (this.callback != null) {
            this.callback.enable(flag);
        }
        return flag;
    }

    public boolean isEnable() {
        return this.isEnable;
    }

    public void setEnable(boolean enable) {
        this.isEnable = enable;
    }

    public static interface ListenerCallback {
        public void enable(boolean var1);
    }
}

