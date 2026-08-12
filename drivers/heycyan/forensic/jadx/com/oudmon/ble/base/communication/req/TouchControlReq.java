/*
 * Decompiled with CFR 0.152.
 */
package com.oudmon.ble.base.communication.req;

import com.oudmon.ble.base.communication.req.MixtureReq;

public class TouchControlReq
extends MixtureReq {
    boolean touch = false;

    public static TouchControlReq getReadInstance(boolean touch) {
        return new TouchControlReq(touch);
    }

    public TouchControlReq() {
        super((byte)59);
        this.subData = new byte[]{1};
    }

    public TouchControlReq(boolean touch) {
        super((byte)59);
        this.subData = touch ? new byte[]{1, 0} : new byte[]{1, 1};
    }

    private TouchControlReq(int type, boolean touch, int strength) {
        super((byte)59);
        this.subData = touch ? new byte[]{2, 0, (byte)type} : new byte[]{2, 1, (byte)type, (byte)strength};
    }

    public static TouchControlReq getWriteInstance(int type, boolean touch, int strength) {
        return new TouchControlReq(type, touch, strength);
    }
}

