/*
 * Decompiled with CFR 0.152.
 */
package com.oudmon.ble.base.communication.req;

import com.oudmon.ble.base.communication.req.MixtureReq;

public class HRVReq
extends MixtureReq {
    private byte index;

    public HRVReq(byte index) {
        super((byte)57);
        this.subData = new byte[]{index};
    }
}

