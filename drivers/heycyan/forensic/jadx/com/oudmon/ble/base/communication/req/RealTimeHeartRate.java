/*
 * Decompiled with CFR 0.152.
 */
package com.oudmon.ble.base.communication.req;

import com.oudmon.ble.base.communication.req.BaseReqCmd;

public class RealTimeHeartRate
extends BaseReqCmd {
    private int type;
    private byte[] mData = new byte[1];

    public RealTimeHeartRate(int type) {
        super((byte)30);
        this.type = type;
        this.mData[0] = (byte)type;
    }

    @Override
    protected byte[] getSubData() {
        return this.mData;
    }

    public int getType() {
        return this.type;
    }

    public void setType(int type) {
        this.type = type;
    }
}

