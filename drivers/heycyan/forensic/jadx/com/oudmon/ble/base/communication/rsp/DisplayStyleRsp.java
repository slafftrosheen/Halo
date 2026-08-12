/*
 * Decompiled with CFR 0.152.
 */
package com.oudmon.ble.base.communication.rsp;

import com.oudmon.ble.base.communication.rsp.MixtureRsp;

public class DisplayStyleRsp
extends MixtureRsp {
    private int styleIndex;

    @Override
    protected void readSubData(byte[] subData) {
        this.styleIndex = subData[1];
    }

    public int getStyleIndex() {
        return this.styleIndex;
    }

    public void setStyleIndex(int index) {
        this.styleIndex = index;
    }
}

