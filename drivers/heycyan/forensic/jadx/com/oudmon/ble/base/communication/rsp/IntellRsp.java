/*
 * Decompiled with CFR 0.152.
 */
package com.oudmon.ble.base.communication.rsp;

import com.oudmon.ble.base.communication.rsp.MixtureRsp;

public class IntellRsp
extends MixtureRsp {
    private boolean isEnable;
    private byte delaySecond;

    @Override
    protected void readSubData(byte[] subData) {
        this.isEnable = subData[1] == 1;
        this.delaySecond = subData[2];
    }

    public boolean isEnable() {
        return this.isEnable;
    }

    public byte getDelaySecond() {
        return this.delaySecond;
    }
}

