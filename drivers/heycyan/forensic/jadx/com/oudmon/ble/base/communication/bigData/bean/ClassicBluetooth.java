/*
 * Decompiled with CFR 0.152.
 */
package com.oudmon.ble.base.communication.bigData.bean;

import com.oudmon.ble.base.communication.bigData.bean.BaseBean;

public class ClassicBluetooth
extends BaseBean {
    private String deviceMac;
    private String deviceName;

    public String getDeviceMac() {
        return this.deviceMac;
    }

    public void setDeviceMac(String deviceMac) {
        this.deviceMac = deviceMac;
    }

    public String getDeviceName() {
        return this.deviceName;
    }

    public void setDeviceName(String deviceName) {
        this.deviceName = deviceName;
    }
}

