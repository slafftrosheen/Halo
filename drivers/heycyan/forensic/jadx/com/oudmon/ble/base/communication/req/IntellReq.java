/*
 * Decompiled with CFR 0.152.
 */
package com.oudmon.ble.base.communication.req;

import com.oudmon.ble.base.communication.req.MixtureReq;

public class IntellReq
extends MixtureReq {
    private boolean isEnable;
    private byte delaySecond;

    private IntellReq() {
        super((byte)9);
        this.subData = new byte[]{1};
    }

    private IntellReq(boolean isEnable, byte delaySecond) {
        super((byte)9);
        this.subData = new byte[]{2, (byte)(isEnable ? 1 : 2), delaySecond};
    }

    public static IntellReq getReadInstance() {
        return new IntellReq();
    }

    public static IntellReq getWriteInstance(boolean isEnable, byte delaySecond) {
        return new IntellReq(isEnable, delaySecond);
    }
}

