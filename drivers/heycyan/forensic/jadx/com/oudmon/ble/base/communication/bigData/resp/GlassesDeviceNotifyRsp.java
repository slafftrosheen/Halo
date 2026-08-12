/*
 * Decompiled with CFR 0.152.
 */
package com.oudmon.ble.base.communication.bigData.resp;

import com.oudmon.ble.base.communication.bigData.resp.BaseResponse;

public class GlassesDeviceNotifyRsp
extends BaseResponse {
    private byte[] loadData;

    @Override
    public boolean acceptData(byte[] data) {
        this.loadData = data;
        return false;
    }

    public byte[] getLoadData() {
        return this.loadData;
    }
}

