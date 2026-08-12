/*
 * Decompiled with CFR 0.152.
 */
package com.oudmon.ble.base.communication.req;

import com.oudmon.ble.base.communication.req.MixtureReq;

public class PressureReq
extends MixtureReq {
    private byte index;

    public PressureReq(byte index) {
        super((byte)55);
        this.subData = new byte[]{index};
    }
}

