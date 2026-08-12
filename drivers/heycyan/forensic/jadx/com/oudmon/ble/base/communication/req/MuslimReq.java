/*
 * Decompiled with CFR 0.152.
 */
package com.oudmon.ble.base.communication.req;

import com.oudmon.ble.base.communication.req.MixtureReq;

public class MuslimReq
extends MixtureReq {
    private byte index;

    public MuslimReq(byte index) {
        super((byte)122);
        this.subData = new byte[]{1, index};
    }

    public MuslimReq(boolean clear) {
        super((byte)122);
        this.subData = new byte[]{2, 1};
    }

    public static MuslimReq getWriteInstance(boolean clear) {
        return new MuslimReq(clear);
    }
}

