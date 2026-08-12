/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  android.bluetooth.BluetoothAdapter
 *  android.content.Context
 *  android.os.Build$VERSION
 */
package com.oudmon.ble.base.scan;

import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.os.Build;
import com.oudmon.ble.base.scan.BluetoothScannerImplJB;
import com.oudmon.ble.base.scan.BluetoothScannerImplLollipop;
import com.oudmon.ble.base.scan.ScanWrapperCallback;

public abstract class BleScannerCompat {
    private static BleScannerCompat mInstance;
    BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
    ScanWrapperCallback scanWrapperCallback;
    public boolean scanning;

    public static BleScannerCompat getScanner(Context context) {
        if (mInstance != null) {
            return mInstance;
        }
        if (Build.VERSION.SDK_INT >= 21) {
            mInstance = new BluetoothScannerImplLollipop(context);
            return mInstance;
        }
        mInstance = new BluetoothScannerImplJB();
        return mInstance;
    }

    public void startScan(ScanWrapperCallback scanWrapperCallback) {
        this.scanning = true;
        this.scanWrapperCallback = scanWrapperCallback;
        scanWrapperCallback.onStart();
    }

    public void stopScan() {
        this.scanning = false;
        if (this.scanWrapperCallback != null) {
            this.scanWrapperCallback.onStop();
        }
    }

    public boolean isScanning() {
        return this.scanning;
    }
}

