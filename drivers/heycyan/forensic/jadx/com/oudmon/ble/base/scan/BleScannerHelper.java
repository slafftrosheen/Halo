/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  android.bluetooth.BluetoothAdapter
 *  android.bluetooth.BluetoothDevice
 *  android.bluetooth.le.ScanResult
 *  android.content.Context
 *  android.os.Handler
 *  android.os.Looper
 *  android.text.TextUtils
 *  android.util.Log
 *  androidx.core.os.HandlerCompat
 */
package com.oudmon.ble.base.scan;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.le.ScanResult;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;
import androidx.core.os.HandlerCompat;
import com.oudmon.ble.base.bluetooth.DeviceManager;
import com.oudmon.ble.base.scan.BleScannerCompat;
import com.oudmon.ble.base.scan.OnTheScanResult;
import com.oudmon.ble.base.scan.ScanRecord;
import com.oudmon.ble.base.scan.ScanWrapperCallback;
import com.oudmon.qc_utils.bluetooth.BluetoothUtils;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class BleScannerHelper {
    private static final String TAG = "glasses_sdk";
    private static final String HANDLER_TOKEN = "stop_token";
    private static BleScannerHelper bleScannerHelper;
    private Handler handler = new Handler(Looper.getMainLooper());
    private int timeOut = 12000;

    private BleScannerHelper() {
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     * Enabled force condition propagation
     * Lifted jumps to return sites
     */
    public static BleScannerHelper getInstance() {
        if (bleScannerHelper != null) return bleScannerHelper;
        Class<BleScannerHelper> clazz = BleScannerHelper.class;
        synchronized (BleScannerHelper.class) {
            if (bleScannerHelper != null) return bleScannerHelper;
            bleScannerHelper = new BleScannerHelper();
            // ** MonitorExit[var0] (shouldn't be in output)
            return bleScannerHelper;
        }
    }

    public void reSetCallback() {
        bleScannerHelper = null;
    }

    public void scanDevice(final Context context, UUID mUuid, final ScanWrapperCallback scanCallBack) {
        this.handler.removeCallbacksAndMessages((Object)HANDLER_TOKEN);
        if (!BluetoothUtils.isEnabledBluetooth(context)) {
            BleScannerCompat.getScanner((Context)context).scanning = false;
            return;
        }
        if (BleScannerCompat.getScanner(context).isScanning()) {
            Log.i((String)TAG, (String)"isScanning:true");
            this.stopScan(context);
        }
        HandlerCompat.postDelayed((Handler)this.handler, (Runnable)new Runnable(){

            @Override
            public void run() {
                BleScannerHelper.this.stopScan(context);
                if (scanCallBack != null) {
                    scanCallBack.onScanFailed(0);
                }
            }
        }, (Object)HANDLER_TOKEN, (long)12000L);
        BleScannerCompat.getScanner(context).startScan(scanCallBack);
    }

    public void stopScan(Context context) {
        this.handler.removeCallbacksAndMessages((Object)HANDLER_TOKEN);
        if (!BluetoothUtils.isEnabledBluetooth(context)) {
            BleScannerCompat.getScanner((Context)context).scanning = false;
            return;
        }
        BleScannerCompat.getScanner(context).stopScan();
    }

    public boolean scanTheDevice(final Context context, final String macAddress, final OnTheScanResult scanResult) {
        this.handler.removeCallbacksAndMessages((Object)HANDLER_TOKEN);
        if (!BluetoothUtils.isEnabledBluetooth(context)) {
            return false;
        }
        HandlerCompat.postDelayed((Handler)this.handler, (Runnable)new Runnable(){

            @Override
            public void run() {
                BleScannerHelper.this.stopScan(context);
                if (scanResult != null) {
                    scanResult.onScanFailed(0);
                }
            }
        }, (Object)HANDLER_TOKEN, (long)this.timeOut);
        BleScannerCompat.getScanner(context).startScan(new ScanWrapperCallback(){

            @Override
            public void onStart() {
                Log.i((String)BleScannerHelper.TAG, (String)"start");
            }

            @Override
            public void onStop() {
                Log.i((String)BleScannerHelper.TAG, (String)"stop");
                try {
                    BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
                    Set devices1 = adapter.getBondedDevices();
                    if (devices1.size() > 0) {
                        for (BluetoothDevice bluetoothDevice : devices1) {
                            if (bluetoothDevice == null || bluetoothDevice.getName() == null || bluetoothDevice.getAddress() == null || !bluetoothDevice.getAddress().equalsIgnoreCase(macAddress)) continue;
                            scanResult.onResult(bluetoothDevice);
                            Log.i((String)BleScannerHelper.TAG, (String)"\u7cfb\u7edf\u7ed1\u5b9a\u4e86\u624b\u73af");
                        }
                    }
                }
                catch (Exception e) {
                    e.printStackTrace();
                }
            }

            @Override
            public void onLeScan(BluetoothDevice device, int rssi, byte[] scanRecord) {
                if (device.getAddress().equalsIgnoreCase(macAddress)) {
                    scanResult.onResult(device);
                    Log.i((String)BleScannerHelper.TAG, (String)device.getAddress());
                }
            }

            @Override
            public void onScanFailed(int errorCode) {
                scanResult.onScanFailed(errorCode);
                Log.i((String)BleScannerHelper.TAG, (String)("------------" + errorCode));
            }

            @Override
            public void onParsedData(BluetoothDevice device, ScanRecord scanRecord) {
            }

            @Override
            public void onBatchScanResults(List<ScanResult> results) {
                for (ScanResult sr : results) {
                    BluetoothDevice device = sr.getDevice();
                    if (!device.getAddress().equalsIgnoreCase(macAddress)) continue;
                    scanResult.onResult(device);
                }
            }
        });
        return true;
    }

    public void removeSystemBle(String mac) {
        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        Set devices1 = adapter.getBondedDevices();
        if (devices1.size() > 0) {
            for (BluetoothDevice bluetoothDevice : devices1) {
                Log.i((String)TAG, (String)bluetoothDevice.getName());
                Log.i((String)TAG, (String)bluetoothDevice.getAddress());
                if (bluetoothDevice == null || bluetoothDevice.getName() == null || bluetoothDevice.getAddress() == null) continue;
                Log.i((String)TAG, (String)("\u79fb\u9664" + mac));
                if (TextUtils.isEmpty((CharSequence)mac) || !mac.equalsIgnoreCase(bluetoothDevice.getAddress())) continue;
                this.removeBondDevice(adapter, bluetoothDevice.getAddress());
            }
        }
    }

    public void removeSystemBle() {
        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        Set devices1 = adapter.getBondedDevices();
        if (devices1.size() > 0) {
            for (BluetoothDevice bluetoothDevice : devices1) {
                String mac;
                if (bluetoothDevice == null || bluetoothDevice.getName() == null || bluetoothDevice.getAddress() == null || TextUtils.isEmpty((CharSequence)(mac = DeviceManager.getInstance().getDeviceAddress())) || !mac.equalsIgnoreCase(bluetoothDevice.getAddress())) continue;
                this.removeBondDevice(adapter, bluetoothDevice.getAddress());
            }
        }
    }

    public void removeMacSystemBond(String address) {
        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        Set devices1 = adapter.getBondedDevices();
        if (devices1.size() > 0) {
            for (BluetoothDevice bluetoothDevice : devices1) {
                if (bluetoothDevice == null || bluetoothDevice.getName() == null || bluetoothDevice.getAddress() == null || TextUtils.isEmpty((CharSequence)address) || !address.equalsIgnoreCase(bluetoothDevice.getAddress())) continue;
                this.removeBondDevice(adapter, bluetoothDevice.getAddress());
            }
        }
    }

    public boolean isMacSystemBond(String address) {
        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        Set devices1 = adapter.getBondedDevices();
        if (devices1.size() > 0) {
            for (BluetoothDevice bluetoothDevice : devices1) {
                if (bluetoothDevice == null || bluetoothDevice.getAddress() == null || TextUtils.isEmpty((CharSequence)address) || !address.equalsIgnoreCase(bluetoothDevice.getAddress())) continue;
                return true;
            }
        }
        return false;
    }

    private void removeBondDevice(BluetoothAdapter adapter, String address) {
        BluetoothDevice device = adapter.getRemoteDevice(address);
        Class<BluetoothDevice> btDeviceCls = BluetoothDevice.class;
        Method removeBond = null;
        try {
            removeBond = btDeviceCls.getMethod("removeBond", new Class[0]);
            removeBond.setAccessible(true);
            removeBond.invoke(device, new Object[0]);
        }
        catch (NoSuchMethodException e) {
            e.printStackTrace();
        }
        catch (IllegalAccessException e) {
            e.printStackTrace();
        }
        catch (InvocationTargetException e) {
            e.printStackTrace();
        }
    }

    public int getTimeOut() {
        return this.timeOut;
    }

    public void setTimeOut(int timeOut) {
        this.timeOut = timeOut;
    }
}

