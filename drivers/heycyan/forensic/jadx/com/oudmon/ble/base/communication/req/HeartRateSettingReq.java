/*
 * Decompiled with CFR 0.152.
 */
package com.oudmon.ble.base.communication.req;

import com.oudmon.ble.base.communication.req.MixtureReq;

public class HeartRateSettingReq
extends MixtureReq {
    private HeartRateSettingReq() {
        super((byte)22);
        this.subData = new byte[]{1};
    }

    private HeartRateSettingReq(boolean isEnable, int interval) {
        super((byte)22);
        this.subData = new byte[]{2, (byte)(isEnable ? 1 : 2), (byte)interval};
    }

    public static HeartRateSettingReq getReadInstance() {
        return new HeartRateSettingReq();
    }

    public static HeartRateSettingReq getWriteInstance(boolean isEnable, int interval) {
        return new HeartRateSettingReq(isEnable, interval);
    }
}

