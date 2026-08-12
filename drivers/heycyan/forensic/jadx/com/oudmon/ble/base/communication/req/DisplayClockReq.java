/*
 * Decompiled with CFR 0.152.
 */
package com.oudmon.ble.base.communication.req;

import com.oudmon.ble.base.communication.req.MixtureReq;

public class DisplayClockReq
extends MixtureReq {
    private DisplayClockReq() {
        super((byte)18);
        this.subData = new byte[]{1};
    }

    private DisplayClockReq(boolean enable) {
        super((byte)18);
        this.subData = new byte[]{2, (byte)(enable ? 1 : 2)};
    }

    public static DisplayClockReq getReadInstance() {
        return new DisplayClockReq();
    }

    public static DisplayClockReq getWriteInstance(boolean enable) {
        return new DisplayClockReq(enable);
    }
}

