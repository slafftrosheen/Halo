/*
 * Decompiled with CFR 0.152.
 */
package com.oudmon.ble.base.communication.req;

import com.oudmon.ble.base.communication.req.BaseReqCmd;

public class SimpleKeyReq
extends BaseReqCmd {
    public SimpleKeyReq(byte key) {
        super(key);
    }

    @Override
    protected byte[] getSubData() {
        return null;
    }
}

