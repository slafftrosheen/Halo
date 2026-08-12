/*
 * Decompiled with CFR 0.152.
 */
package com.oudmon.ble.base.communication.rsp;

import com.oudmon.ble.base.communication.rsp.MixtureRsp;

public class DisplayOrientationRsp
extends MixtureRsp {
    private boolean isPortrait;
    private boolean isLeft;

    @Override
    protected void readSubData(byte[] subData) {
        this.isPortrait = subData[1] == 1;
        this.isLeft = subData[2] == 1;
    }

    public boolean isPortrait() {
        return this.isPortrait;
    }

    public boolean isLeft() {
        return this.isLeft;
    }

    public void setPortrait(boolean portrait) {
        this.isPortrait = portrait;
    }

    public void setLeft(boolean left) {
        this.isLeft = left;
    }
}

