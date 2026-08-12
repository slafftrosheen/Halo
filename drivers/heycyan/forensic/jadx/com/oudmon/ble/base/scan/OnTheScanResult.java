/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  android.bluetooth.BluetoothDevice
 */
package com.oudmon.ble.base.scan;

import android.bluetooth.BluetoothDevice;

public interface OnTheScanResult {
    public void onResult(BluetoothDevice var1);

    public void onScanFailed(int var1);
}

