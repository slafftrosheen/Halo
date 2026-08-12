/*
 * Decompiled with CFR 0.152.
 */
package com.oudmon.ble.base.communication.rsp;

import com.oudmon.ble.base.communication.rsp.BaseRspCmd;

public class TouchControlResp
extends BaseRspCmd {
    private int appType;
    private int strength;
    private boolean touch;

    @Override
    public boolean acceptData(byte[] data) {
        this.touch = data[1] == 0;
        this.appType = data[2];
        this.strength = data[3];
        return false;
    }

    public int getAppType() {
        return this.appType;
    }

    public int getStrength() {
        return this.strength;
    }

    public boolean isTouch() {
        return this.touch;
    }
}

