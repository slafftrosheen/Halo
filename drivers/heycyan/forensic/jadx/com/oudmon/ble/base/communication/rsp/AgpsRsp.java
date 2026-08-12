/*
 * Decompiled with CFR 0.152.
 */
package com.oudmon.ble.base.communication.rsp;

import com.oudmon.ble.base.communication.rsp.MixtureRsp;

public class AgpsRsp
extends MixtureRsp {
    private boolean mEnable;

    @Override
    protected void readSubData(byte[] subData) {
        this.mEnable = subData[1] == 1;
    }

    public boolean isEnable() {
        return this.mEnable;
    }
}

