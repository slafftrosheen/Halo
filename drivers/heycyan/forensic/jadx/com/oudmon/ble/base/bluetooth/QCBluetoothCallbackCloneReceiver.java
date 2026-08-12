/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  android.bluetooth.BluetoothDevice
 *  android.content.BroadcastReceiver
 *  android.content.Context
 *  android.content.Intent
 */
package com.oudmon.ble.base.bluetooth;

import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class QCBluetoothCallbackCloneReceiver
extends BroadcastReceiver {
    public void onReceive(Context context, Intent intent) {
        String action;
        switch (action = intent.getAction()) {
            case "com.swatchdevice.pro.sdk.ble.gatt_connected": {
                BluetoothDevice device = (BluetoothDevice)intent.getParcelableExtra("DEVICE");
                this.connectStatue(device, true);
                break;
            }
            case "com.swatchdevice.pro.sdk.ble.gatt_disconnected": 
            case "com.swatchdevice.pro.sdk.ble.BLE_NO_CALLBACK": {
                BluetoothDevice device = (BluetoothDevice)intent.getParcelableExtra("DEVICE");
                this.connectStatue(device, false);
                break;
            }
            case "com.swatchdevice.pro.characteristic_write_qc": {
                byte[] data = intent.getByteArrayExtra("DATA");
                this.onCommandSend(data);
                break;
            }
            case "com.swatchdevice.pro.sdk.ble.characteristic_read": {
                String uuid = intent.getStringExtra("CHARACTER_UUID");
                byte[] data = intent.getByteArrayExtra("VALUE");
                this.onCharacteristicRead(uuid, data);
                break;
            }
            case "com.swatchdevice.pro.characteristic_changed_qc": {
                String address = intent.getStringExtra("ADDRESS");
                String uuid = intent.getStringExtra("CHARACTER_UUID");
                byte[] data = intent.getByteArrayExtra("VALUE");
                this.onCharacteristicChange(address, uuid, data);
                break;
            }
            case "com.swatchdevice.pro.sdk.ble.service_discovered": {
                this.onServiceDiscovered();
                break;
            }
            case "com.swatchdevice.pro.sdk.ble.BLE_STATUS": {
                int status = intent.getIntExtra("EXTRA_STATUS", 99);
                int newState = intent.getIntExtra("EXTRA_BLE_NEW_STATE", 99);
                this.bleStatus(status, newState);
                break;
            }
        }
    }

    public void connectStatue(BluetoothDevice device, boolean connected) {
    }

    public void onCharacteristicRead(String uuid, byte[] data) {
    }

    public void bleStatus(int status, int newState) {
    }

    public void onServiceDiscovered() {
    }

    public void onCommandSend(byte[] data) {
    }

    public void onCharacteristicChangeFilter(String address, String uuid, byte[] data) {
    }

    public void onCharacteristicChange(String address, String uuid, byte[] data) {
    }
}

