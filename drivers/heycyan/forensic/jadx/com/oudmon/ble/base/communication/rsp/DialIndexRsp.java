/*
 * Decompiled with CFR 0.152.
 */
package com.oudmon.ble.base.communication.rsp;

import com.oudmon.ble.base.communication.rsp.BaseRspCmd;

public class DialIndexRsp
extends BaseRspCmd {
    private int index = 0;

    @Override
    public boolean acceptData(byte[] data) {
        this.index = data[1];
        return false;
    }

    public int getIndex() {
        return this.index;
    }
}

