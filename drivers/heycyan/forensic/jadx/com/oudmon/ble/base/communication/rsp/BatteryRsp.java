/*
 * Decompiled with CFR 0.152.
 */
package com.oudmon.ble.base.communication.rsp;

import com.oudmon.ble.base.communication.rsp.BaseRspCmd;

public class BatteryRsp
extends BaseRspCmd {
    private int batteryValue;
    private boolean charging;

    @Override
    public boolean acceptData(byte[] data) {
        this.batteryValue = data[0];
        this.charging = data[1] == 1;
        return false;
    }

    public int getBatteryValue() {
        return this.batteryValue;
    }

    public boolean isCharging() {
        return this.charging;
    }
}

