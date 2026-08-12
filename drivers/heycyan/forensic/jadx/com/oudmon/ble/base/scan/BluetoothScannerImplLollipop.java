/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  android.annotation.TargetApi
 *  android.bluetooth.BluetoothDevice
 *  android.bluetooth.le.BluetoothLeScanner
 *  android.bluetooth.le.ScanCallback
 *  android.bluetooth.le.ScanFilter
 *  android.bluetooth.le.ScanResult
 *  android.bluetooth.le.ScanSettings
 *  android.bluetooth.le.ScanSettings$Builder
 *  android.content.Context
 *  android.os.Build$VERSION
 *  android.util.Log
 *  androidx.core.app.ActivityCompat
 */
package com.oudmon.ble.base.scan;

import android.annotation.TargetApi;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.os.Build;
import android.util.Log;
import androidx.core.app.ActivityCompat;
import com.oudmon.ble.base.scan.BleScannerCompat;
import com.oudmon.ble.base.scan.ScanRecord;
import com.oudmon.ble.base.scan.ScanWrapperCallback;
import com.oudmon.ble.base.util.AppUtil;
import com.oudmon.qc_utils.bluetooth.BluetoothUtils;
import java.util.ArrayList;
import java.util.List;

@TargetApi(value=21)
class BluetoothScannerImplLollipop
extends BleScannerCompat {
    private static final String TAG = "BluetoothScannerImplLol";
    private BluetoothLeScanner scanner;
    private ScanSettings scanSettings;
    private List<ScanFilter> filters = new ArrayList<ScanFilter>();
    private Context context;
    private ScanCallback scannerCallback = new ScanCallback(){

        public void onScanResult(int callbackType, ScanResult result) {
            ScanRecord parseRecord;
            BluetoothDevice device = result.getDevice();
            byte[] scanRecord = result.getScanRecord().getBytes();
            if (BluetoothScannerImplLollipop.this.scanWrapperCallback != null) {
                BluetoothScannerImplLollipop.this.scanWrapperCallback.onLeScan(device, result.getRssi(), scanRecord);
            }
            if ((parseRecord = ScanRecord.parseFromBytes(scanRecord)) != null && BluetoothScannerImplLollipop.this.scanWrapperCallback != null) {
                BluetoothScannerImplLollipop.this.scanWrapperCallback.onParsedData(device, parseRecord);
            }
        }

        public void onBatchScanResults(List<ScanResult> results) {
            if (results != null && !results.isEmpty() && BluetoothScannerImplLollipop.this.scanWrapperCallback != null) {
                BluetoothScannerImplLollipop.this.scanWrapperCallback.onBatchScanResults(results);
            }
        }

        public void onScanFailed(int errorCode) {
            Log.i((String)BluetoothScannerImplLollipop.TAG, (String)("Scan Failed Error Code: " + errorCode));
            if (BluetoothScannerImplLollipop.this.scanWrapperCallback != null) {
                BluetoothScannerImplLollipop.this.scanWrapperCallback.onScanFailed(errorCode);
            }
        }
    };

    public BluetoothScannerImplLollipop(Context context) {
        this.context = context;
    }

    @Override
    public void startScan(ScanWrapperCallback scanWrapperCallback) {
        super.startScan(scanWrapperCallback);
        this.scanning = true;
        if (this.scanner == null) {
            this.scanner = this.bluetoothAdapter.getBluetoothLeScanner();
        }
        if (!BluetoothUtils.isEnabledBluetooth(this.context)) {
            return;
        }
        this.setScanSettings();
        if (this.scanner != null) {
            if (Build.VERSION.SDK_INT >= 31 && ActivityCompat.checkSelfPermission((Context)this.context, (String)"android.permission.BLUETOOTH_SCAN") != 0) {
                return;
            }
            this.scanner.startScan(this.filters, this.scanSettings, this.scannerCallback);
        }
    }

    @Override
    public void stopScan() {
        super.stopScan();
        this.scanning = false;
        if (!BluetoothUtils.isEnabledBluetooth(this.context)) {
            return;
        }
        if (this.scanner == null) {
            this.scanner = this.bluetoothAdapter.getBluetoothLeScanner();
        }
        if (Build.VERSION.SDK_INT >= 31 && ActivityCompat.checkSelfPermission((Context)this.context, (String)"android.permission.BLUETOOTH_SCAN") != 0) {
            return;
        }
        this.scanner.stopScan(this.scannerCallback);
    }

    private void setScanSettings() {
        boolean background = AppUtil.isBackground(this.context);
        boolean background1 = AppUtil.isApplicationBroughtToBackground(this.context);
        if (background || background1) {
            ScanSettings.Builder builder = new ScanSettings.Builder();
            builder.setScanMode(0);
            builder.setMatchMode(1);
            builder.setCallbackType(1);
            this.scanSettings = builder.build();
        } else {
            this.filters.clear();
            ScanSettings.Builder builder = new ScanSettings.Builder().setScanMode(2);
            builder.setReportDelay(0L);
            this.scanSettings = builder.build();
        }
    }
}

