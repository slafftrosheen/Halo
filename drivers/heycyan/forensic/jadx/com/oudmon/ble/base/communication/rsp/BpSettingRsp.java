/*
 * Decompiled with CFR 0.152.
 */
package com.oudmon.ble.base.communication.rsp;

import com.oudmon.ble.base.communication.entity.StartEndTimeEntity;
import com.oudmon.ble.base.communication.rsp.MixtureRsp;

public class BpSettingRsp
extends MixtureRsp {
    private boolean isEnable;
    private StartEndTimeEntity startEndTimeEntity;
    private int multiple;

    @Override
    protected void readSubData(byte[] subData) {
        this.isEnable = subData[1] == 1;
        this.startEndTimeEntity = new StartEndTimeEntity(subData[2], subData[3], subData[4], subData[5]);
        this.multiple = subData[6];
    }

    public boolean isEnable() {
        return this.isEnable;
    }

    public StartEndTimeEntity getStartEndTimeEntity() {
        return this.startEndTimeEntity;
    }

    public int getMultiple() {
        return this.multiple;
    }

    public void setEnable(boolean enable) {
        this.isEnable = enable;
    }
}

