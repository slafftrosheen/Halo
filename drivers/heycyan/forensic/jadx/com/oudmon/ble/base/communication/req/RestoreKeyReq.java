/*
 * Decompiled with CFR 0.152.
 */
package com.oudmon.ble.base.communication.req;

import com.oudmon.ble.base.communication.req.SimpleKeyReq;

public class RestoreKeyReq
extends SimpleKeyReq {
    public RestoreKeyReq(byte key) {
        super(key);
    }

    @Override
    protected byte[] getSubData() {
        byte[] sub = new byte[]{102, 102};
        return sub;
    }
}

