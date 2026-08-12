/*
 * Decompiled with CFR 0.152.
 */
package com.oudmon.ble.base.communication.rsp;

import com.oudmon.ble.base.communication.rsp.BaseRspCmd;

public class ReadPersonalizationSettingRsp
extends BaseRspCmd {
    private int mClockSetting;
    private int mPowerOnSetting;
    private int mPowerOffSetting;

    @Override
    public boolean acceptData(byte[] data) {
        this.mClockSetting = data[0];
        this.mPowerOnSetting = data[1];
        this.mPowerOffSetting = data[2];
        return false;
    }

    public int getClockSetting() {
        return this.mClockSetting;
    }

    public int getPowerOnSetting() {
        return this.mPowerOnSetting;
    }

    public int getPowerOffSetting() {
        return this.mPowerOffSetting;
    }

    public String toString() {
        return "ReadPersonalizationSettingRsp{status=" + this.status + ", mClockSetting=" + this.mClockSetting + ", mPowerOnSetting=" + this.mPowerOnSetting + ", mPowerOffSetting=" + this.mPowerOffSetting + '}';
    }
}

