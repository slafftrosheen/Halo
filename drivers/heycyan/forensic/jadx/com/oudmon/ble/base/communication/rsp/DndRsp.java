/*
 * Decompiled with CFR 0.152.
 */
package com.oudmon.ble.base.communication.rsp;

import com.oudmon.ble.base.communication.entity.StartEndTimeEntity;
import com.oudmon.ble.base.communication.rsp.MixtureRsp;

public class DndRsp
extends MixtureRsp {
    private boolean isEnable;
    private StartEndTimeEntity dndEntity;
    private boolean manualDND;

    @Override
    protected void readSubData(byte[] subData) {
        this.isEnable = subData[1] == 1;
        this.manualDND = subData[6] == 1;
        this.dndEntity = new StartEndTimeEntity(subData[2], subData[3], subData[4], subData[5]);
    }

    public boolean isEnable() {
        return this.isEnable;
    }

    public boolean isManualDND() {
        return this.manualDND;
    }

    public StartEndTimeEntity getDndEntity() {
        return this.dndEntity;
    }

    public void setEnable(boolean enable) {
        this.isEnable = enable;
    }
}

