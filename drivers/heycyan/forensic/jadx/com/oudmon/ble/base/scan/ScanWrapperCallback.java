/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  android.bluetooth.BluetoothDevice
 *  android.bluetooth.le.ScanResult
 *  androidx.annotation.RequiresApi
 */
package com.oudmon.ble.base.scan;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.le.ScanResult;
import androidx.annotation.RequiresApi;
import com.oudmon.ble.base.scan.ScanRecord;
import java.util.List;

public interface ScanWrapperCallback {
    public void onStart();

    public void onStop();

    public void onLeScan(BluetoothDevice var1, int var2, byte[] var3);

    public void onScanFailed(int var1);

    @RequiresApi(api=21)
    public void onParsedData(BluetoothDevice var1, ScanRecord var2);

    public void onBatchScanResults(List<ScanResult> var1);
}

