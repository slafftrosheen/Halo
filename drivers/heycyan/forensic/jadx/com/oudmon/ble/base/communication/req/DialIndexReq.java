/*
 * Decompiled with CFR 0.152.
 */
package com.oudmon.ble.base.communication.req;

import com.oudmon.ble.base.communication.req.MixtureReq;

public class DialIndexReq
extends MixtureReq {
    public DialIndexReq() {
        super((byte)117);
        this.subData = new byte[]{0};
    }

    private DialIndexReq(int index) {
        super((byte)117);
        this.subData = new byte[]{1, (byte)index};
    }

    public static DialIndexReq getReadInstance() {
        return new DialIndexReq();
    }

    public static DialIndexReq getWriteInstance(int index) {
        return new DialIndexReq(index);
    }
}

