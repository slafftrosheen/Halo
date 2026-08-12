/*
 * Decompiled with CFR 0.152.
 */
package com.oudmon.ble.base.communication.rsp;

import com.oudmon.ble.base.communication.rsp.MixtureRsp;
import com.oudmon.ble.base.communication.utils.ByteUtil;

public class HeartRateSettingRsp
extends MixtureRsp {
    private boolean isEnable;
    private int heartInterval;
    private int startInterval;

    @Override
    protected void readSubData(byte[] subData) {
        this.isEnable = subData[1] == 1;
        this.heartInterval = ByteUtil.byteToInt(subData[2]);
        int start = ByteUtil.byteToInt(subData[3]);
        this.startInterval = start == 0 ? 5 : start;
    }

    public boolean isEnable() {
        return this.isEnable;
    }

    public int getHeartInterval() {
        return this.heartInterval;
    }

    public int getStartInterval() {
        return this.startInterval;
    }

    public void setStartInterval(int startInterval) {
        this.startInterval = startInterval;
    }
}

