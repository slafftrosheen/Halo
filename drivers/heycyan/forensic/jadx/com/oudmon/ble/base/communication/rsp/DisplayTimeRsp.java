/*
 * Decompiled with CFR 0.152.
 */
package com.oudmon.ble.base.communication.rsp;

import com.oudmon.ble.base.communication.rsp.MixtureRsp;

public class DisplayTimeRsp
extends MixtureRsp {
    private int mDisplayTime;
    private int mDisplayType;
    private int mAlpha;
    private boolean mIsCustom;
    private int total;
    private int type;
    private int min;
    private int max;
    private int step;
    private int alwaysOn;
    private int supportAlwaysOn;

    @Override
    protected void readSubData(byte[] subData) {
        this.mDisplayTime = subData[1];
        this.mDisplayType = subData[2];
        this.mAlpha = subData[3];
        this.mIsCustom = subData[4] != 0;
        this.total = subData[5];
        this.type = subData[6];
        this.min = subData[7];
        this.max = subData[8];
        this.step = subData[9];
        this.alwaysOn = subData[10];
        this.supportAlwaysOn = subData[11];
    }

    public int getDisplayTime() {
        return this.mDisplayTime;
    }

    public int getDisplayType() {
        return this.mDisplayType;
    }

    public int getAlpha() {
        return this.mAlpha;
    }

    public boolean isCustom() {
        return this.mIsCustom;
    }

    public int getTotal() {
        return this.total;
    }

    public int getType() {
        return this.type;
    }

    public int getMin() {
        return this.min;
    }

    public int getMax() {
        return this.max;
    }

    public int getStep() {
        return this.step;
    }

    public int getAlwaysOn() {
        return this.alwaysOn;
    }

    public int getSupportAlwaysOn() {
        return this.supportAlwaysOn;
    }
}

