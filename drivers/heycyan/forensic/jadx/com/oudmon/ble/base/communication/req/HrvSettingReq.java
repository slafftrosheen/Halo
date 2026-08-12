/*
 * Decompiled with CFR 0.152.
 */
package com.oudmon.ble.base.communication.req;

import com.oudmon.ble.base.communication.req.MixtureReq;

public class HrvSettingReq
extends MixtureReq {
    private HrvSettingReq() {
        super((byte)56);
        this.subData = new byte[]{1};
    }

    public HrvSettingReq(boolean isEnable) {
        super((byte)56);
        this.subData = new byte[]{2, (byte)(isEnable ? 1 : 0)};
    }

    public static HrvSettingReq getReadInstance() {
        return new HrvSettingReq();
    }
}

