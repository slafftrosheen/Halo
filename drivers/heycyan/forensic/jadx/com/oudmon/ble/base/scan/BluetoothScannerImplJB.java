/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  android.bluetooth.BluetoothAdapter$LeScanCallback
 *  android.bluetooth.BluetoothDevice
 */
package com.oudmon.ble.base.scan;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import com.oudmon.ble.base.scan.BleScannerCompat;
import com.oudmon.ble.base.scan.ScanWrapperCallback;

class BluetoothScannerImplJB
extends BleScannerCompat {
    private BluetoothAdapter.LeScanCallback leScanCallback = new BluetoothAdapter.LeScanCallback(){

        public void onLeScan(BluetoothDevice device, int rssi, byte[] scanRecord) {
            BluetoothScannerImplJB.this.scanWrapperCallback.onLeScan(device, rssi, scanRecord);
        }
    };

    BluetoothScannerImplJB() {
    }

    @Override
    public void startScan(ScanWrapperCallback scanWrapperCallback) {
        super.startScan(scanWrapperCallback);
        this.scanning = true;
        this.bluetoothAdapter.startLeScan(this.leScanCallback);
    }

    @Override
    public void stopScan() {
        super.stopScan();
        this.scanning = false;
        this.bluetoothAdapter.stopLeScan(this.leScanCallback);
    }
}

