/*
 * Decompiled with CFR 0.152.
 */
package com.oudmon.ble.base.communication.rsp;

import com.oudmon.ble.base.communication.rsp.BaseRspCmd;

public class DeviceNotifyRsp
extends BaseRspCmd {
    private int dataType;
    private byte[] loadData;

    @Override
    public boolean acceptData(byte[] data) {
        this.dataType = data[0];
        this.loadData = data;
        return false;
    }

    public int getDataType() {
        return this.dataType;
    }

    public byte[] getLoadData() {
        return this.loadData;
    }
}

