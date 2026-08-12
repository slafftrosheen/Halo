/*
 * Decompiled with CFR 0.152.
 */
package com.oudmon.ble.base.communication.req;

import com.oudmon.ble.base.communication.req.BaseReqCmd;

public class SetANCSReq
extends BaseReqCmd {
    public SetANCSReq() {
        super((byte)96);
    }

    @Override
    protected byte[] getSubData() {
        byte[] src = new byte[]{-1, -97};
        return src;
    }
}

