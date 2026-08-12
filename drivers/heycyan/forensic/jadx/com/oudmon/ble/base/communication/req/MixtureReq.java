/*
 * Decompiled with CFR 0.152.
 */
package com.oudmon.ble.base.communication.req;

import com.oudmon.ble.base.communication.req.BaseReqCmd;

public abstract class MixtureReq
extends BaseReqCmd {
    protected byte[] subData;

    public MixtureReq(byte key) {
        super(key);
    }

    @Override
    protected byte[] getSubData() {
        return this.subData;
    }
}

