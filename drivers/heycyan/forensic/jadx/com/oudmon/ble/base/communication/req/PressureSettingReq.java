/*
 * Decompiled with CFR 0.152.
 */
package com.oudmon.ble.base.communication.req;

import com.oudmon.ble.base.communication.req.MixtureReq;

public class PressureSettingReq
extends MixtureReq {
    private PressureSettingReq(boolean open) {
        super((byte)54);
        this.subData = new byte[]{2, (byte)(open ? 1 : 0)};
    }

    private PressureSettingReq() {
        super((byte)54);
        this.subData = new byte[]{1};
    }

    public static PressureSettingReq getWriteInstance(boolean open) {
        return new PressureSettingReq(open);
    }

    public static PressureSettingReq getReadInstance() {
        return new PressureSettingReq();
    }
}

