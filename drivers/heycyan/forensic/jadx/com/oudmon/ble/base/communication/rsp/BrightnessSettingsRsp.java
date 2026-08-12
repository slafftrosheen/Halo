/*
 * Decompiled with CFR 0.152.
 */
package com.oudmon.ble.base.communication.rsp;

import com.oudmon.ble.base.communication.rsp.MixtureRsp;

public class BrightnessSettingsRsp
extends MixtureRsp {
    private int level = 0;

    @Override
    protected void readSubData(byte[] subData) {
        this.level = subData[1];
    }

    public int getLevel() {
        return this.level;
    }
}

