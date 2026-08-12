/*
 * Decompiled with CFR 0.152.
 */
package com.oudmon.ble.base.communication.req;

import com.oudmon.ble.base.communication.req.MixtureReq;

public class PalmScreenReq
extends MixtureReq {
    private PalmScreenReq() {
        super((byte)5);
        this.subData = new byte[]{1};
    }

    private PalmScreenReq(boolean isEnable, boolean isLeft, boolean needPalm) {
        super((byte)5);
        this.subData = new byte[]{2, (byte)(isEnable ? 1 : 2), (byte)((isLeft ? 1 : 2) | (needPalm ? 4 : 0))};
    }

    public static PalmScreenReq getReadInstance() {
        return new PalmScreenReq();
    }

    public static PalmScreenReq getWriteInstance(boolean isEnable, boolean isLeft) {
        return new PalmScreenReq(isEnable, isLeft, true);
    }
}

