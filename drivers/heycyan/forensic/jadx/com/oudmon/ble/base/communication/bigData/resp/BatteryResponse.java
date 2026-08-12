/*
 * Decompiled with CFR 0.152.
 */
package com.oudmon.ble.base.communication.bigData.resp;

import com.oudmon.ble.base.communication.bigData.resp.BaseResponse;

public class BatteryResponse
extends BaseResponse {
    private byte[] subData;
    private int battery;
    private boolean charging;

    @Override
    public boolean acceptData(byte[] data) {
        try {
            this.subData = data;
            this.battery = data[6];
            this.charging = data[7] == 1;
        }
        catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    public byte[] getSubData() {
        return this.subData;
    }

    public int getBattery() {
        return this.battery;
    }

    public boolean isCharging() {
        return this.charging;
    }
}

