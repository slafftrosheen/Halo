/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  android.annotation.SuppressLint
 *  android.bluetooth.BluetoothAdapter
 *  android.bluetooth.BluetoothManager
 *  android.content.Context
 *  android.os.Build$VERSION
 */
package com.oudmon.qc_utils.bluetooth;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.os.Build;

public class BluetoothUtils {
    @SuppressLint(value={"MissingPermission"})
    public static boolean isEnabledBluetooth(Context context) {
        try {
            BluetoothAdapter adapter = ((BluetoothManager)context.getSystemService("bluetooth")).getAdapter();
            if (!context.getPackageManager().hasSystemFeature("android.hardware.bluetooth_le")) {
                return false;
            }
            if (adapter == null) {
                return false;
            }
            return adapter.isEnabled();
        }
        catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public static boolean hasLollipop() {
        return Build.VERSION.SDK_INT >= 21;
    }
}

