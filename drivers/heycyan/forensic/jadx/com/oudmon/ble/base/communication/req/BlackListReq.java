/*
 * Decompiled with CFR 0.152.
 */
package com.oudmon.ble.base.communication.req;

import com.oudmon.ble.base.communication.req.BaseReqCmd;

public class BlackListReq
extends BaseReqCmd {
    public BlackListReq() {
        super((byte)45);
    }

    @Override
    protected byte[] getSubData() {
        return new byte[]{1};
    }
}

