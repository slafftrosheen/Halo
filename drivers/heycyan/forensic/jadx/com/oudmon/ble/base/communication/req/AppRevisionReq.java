/*
 * Decompiled with CFR 0.152.
 */
package com.oudmon.ble.base.communication.req;

import com.oudmon.ble.base.communication.req.MixtureReq;

public class AppRevisionReq
extends MixtureReq {
    private AppRevisionReq(int type) {
        super((byte)-95);
        this.subData = new byte[]{(byte)type};
    }

    public static AppRevisionReq getWriteInstance(int type) {
        return new AppRevisionReq(type);
    }
}

