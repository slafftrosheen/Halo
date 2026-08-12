/*
 * Decompiled with CFR 0.152.
 */
package com.oudmon.ble.base.communication.rsp;

import com.oudmon.ble.base.communication.rsp.MixtureRsp;

public class DegreeSwitchRsp
extends MixtureRsp {
    private boolean isEnable = false;
    private boolean isCelsius = false;

    @Override
    protected void readSubData(byte[] subData) {
        this.isEnable = subData[1] == 1;
        this.isCelsius = subData[2] == 1;
    }

    public boolean isEnable() {
        return this.isEnable;
    }

    public void setEnable(boolean enable) {
        this.isEnable = enable;
    }

    public boolean isCelsius() {
        return this.isCelsius;
    }

    public void setCelsius(boolean celsius) {
        this.isCelsius = celsius;
    }

    public String toString() {
        return "DegreeSwitchRsp{isEnable=" + this.isEnable + ", isCelsius=" + this.isCelsius + '}';
    }
}

