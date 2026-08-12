/*
 * Decompiled with CFR 0.152.
 */
package com.oudmon.ble.base.communication.req;

import com.oudmon.ble.base.communication.req.MixtureReq;
import com.oudmon.ble.base.communication.utils.ByteUtil;

public class MuslimTargetReq
extends MixtureReq {
    private byte index;

    public MuslimTargetReq() {
        super((byte)123);
        this.subData = new byte[]{1, 1};
    }

    public MuslimTargetReq(int goal) {
        super((byte)123);
        byte[] a = new byte[]{2, 1};
        this.subData = ByteUtil.concat(a, ByteUtil.intToByte(goal, 4));
    }
}

