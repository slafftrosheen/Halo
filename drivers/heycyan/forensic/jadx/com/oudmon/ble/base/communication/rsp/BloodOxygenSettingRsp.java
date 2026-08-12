/*
 * Decompiled with CFR 0.152.
 */
package com.oudmon.ble.base.communication.rsp;

import com.oudmon.ble.base.communication.rsp.MixtureRsp;

public class BloodOxygenSettingRsp
extends MixtureRsp {
    private boolean isEnable;

    @Override
    protected void readSubData(byte[] subData) {
        this.isEnable = subData[1] == 1;
    }

    public boolean isEnable() {
        return this.isEnable;
    }
}

