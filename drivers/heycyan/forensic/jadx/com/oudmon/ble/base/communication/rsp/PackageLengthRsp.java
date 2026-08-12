/*
 * Decompiled with CFR 0.152.
 */
package com.oudmon.ble.base.communication.rsp;

import com.oudmon.ble.base.communication.rsp.BaseRspCmd;

public class PackageLengthRsp
extends BaseRspCmd {
    public int mData = 0;

    @Override
    public boolean acceptData(byte[] data) {
        this.mData = data[0] & 0xFF;
        return false;
    }
}

