/*
 * Decompiled with CFR 0.152.
 */
package com.oudmon.ble.base.communication.req;

import com.oudmon.ble.base.communication.req.MixtureReq;

public class DisplayStyleReq
extends MixtureReq {
    private DisplayStyleReq() {
        super((byte)42);
        this.subData = new byte[]{1};
    }

    private DisplayStyleReq(int index) {
        super((byte)42);
        this.subData = new byte[]{2, (byte)index};
    }

    public static DisplayStyleReq getReadInstance() {
        return new DisplayStyleReq();
    }

    public static DisplayStyleReq getWriteInstance(int index) {
        return new DisplayStyleReq(index);
    }
}

