/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  android.text.TextUtils
 */
package com.oudmon.ble.base.bluetooth;

import android.text.TextUtils;

public class DeviceManager {
    private String mDeviceName = "";
    private String mDeviceAddress = "";
    private static DeviceManager mInstance;

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     * Enabled force condition propagation
     * Lifted jumps to return sites
     */
    public static DeviceManager getInstance() {
        if (mInstance != null) return mInstance;
        Class<DeviceManager> clazz = DeviceManager.class;
        synchronized (DeviceManager.class) {
            if (mInstance != null) return mInstance;
            mInstance = new DeviceManager();
            // ** MonitorExit[var0] (shouldn't be in output)
            return mInstance;
        }
    }

    public void reSet() {
        mInstance = null;
        this.mDeviceName = "";
        this.mDeviceAddress = null;
    }

    public String getDeviceName() {
        return this.mDeviceName;
    }

    public void setDeviceName(String name) {
        if (TextUtils.isEmpty((CharSequence)name)) {
            name = "";
        }
        this.mDeviceName = name;
    }

    public String getDeviceAddress() {
        return this.mDeviceAddress;
    }

    public void setDeviceAddress(String address) {
        this.mDeviceAddress = address;
    }
}

