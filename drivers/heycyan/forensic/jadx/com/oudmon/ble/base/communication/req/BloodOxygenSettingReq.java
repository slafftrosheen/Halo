/*
 * Decompiled with CFR 0.152.
 */
package com.oudmon.ble.base.communication.req;

import com.oudmon.ble.base.communication.req.MixtureReq;

public class BloodOxygenSettingReq
extends MixtureReq {
    private BloodOxygenSettingReq() {
        super((byte)44);
        this.subData = new byte[]{1};
    }

    private BloodOxygenSettingReq(boolean isEnable) {
        super((byte)44);
        this.subData = new byte[]{2, (byte)(isEnable ? 1 : 0)};
    }

    public static BloodOxygenSettingReq getReadInstance() {
        return new BloodOxygenSettingReq();
    }

    public static BloodOxygenSettingReq getWriteInstance(boolean isEnable) {
        return new BloodOxygenSettingReq(isEnable);
    }
}

