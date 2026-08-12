/*
 * Decompiled with CFR 0.152.
 */
package com.oudmon.ble.base.communication.rsp;

import com.oudmon.ble.base.communication.rsp.BaseRspCmd;

public class RealTimeHeartRateRsp
extends BaseRspCmd {
    private int heart;

    @Override
    public boolean acceptData(byte[] data) {
        this.heart = data[0];
        return false;
    }

    public int getHeart() {
        return this.heart;
    }
}

