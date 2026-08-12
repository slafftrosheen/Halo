/*
 * Decompiled with CFR 0.152.
 */
package com.oudmon.ble.base.communication.req;

import com.oudmon.ble.base.communication.req.MixtureReq;

public class BatterySavingReq
extends MixtureReq {
    public BatterySavingReq() {
        super((byte)118);
        this.subData = new byte[]{0};
    }

    private BatterySavingReq(boolean open) {
        super((byte)118);
        this.subData = new byte[]{1, (byte)(open ? 1 : 0)};
    }

    public static BatterySavingReq getReadInstance() {
        return new BatterySavingReq();
    }

    public static BatterySavingReq getWriteInstance(boolean open) {
        return new BatterySavingReq(open);
    }
}

