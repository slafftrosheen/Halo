/*
 * Decompiled with CFR 0.152.
 */
package com.oudmon.ble.base.communication.rsp;

import com.oudmon.ble.base.communication.rsp.MixtureRsp;

public class DisplayClockRsp
extends MixtureRsp {
    private boolean isClock;

    @Override
    protected void readSubData(byte[] subData) {
        this.isClock = subData[1] == 1;
    }

    public boolean isClock() {
        return this.isClock;
    }

    public void setClock(boolean clock) {
        this.isClock = clock;
    }
}

