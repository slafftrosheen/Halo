/*
 * Decompiled with CFR 0.152.
 */
package com.oudmon.ble.base.communication.req;

import com.oudmon.ble.base.communication.req.BaseReqCmd;

public class BpReadConformReq
extends BaseReqCmd {
    private boolean isSuccess;

    public BpReadConformReq(boolean isSuccess) {
        super((byte)14);
        this.isSuccess = isSuccess;
    }

    @Override
    protected byte[] getSubData() {
        return new byte[]{(byte)(this.isSuccess ? 0 : 255)};
    }
}

