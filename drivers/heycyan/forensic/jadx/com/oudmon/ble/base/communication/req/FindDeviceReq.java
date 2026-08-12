/*
 * Decompiled with CFR 0.152.
 */
package com.oudmon.ble.base.communication.req;

import com.oudmon.ble.base.communication.req.BaseReqCmd;

public class FindDeviceReq
extends BaseReqCmd {
    public FindDeviceReq() {
        super((byte)80);
    }

    @Override
    protected byte[] getSubData() {
        return new byte[]{85, -86};
    }
}

