/*
 * Decompiled with CFR 0.152.
 */
package com.oudmon.ble.base.communication.bigData.resp;

import com.oudmon.ble.base.communication.bigData.resp.BaseResponse;

public class SyncTimeResponse
extends BaseResponse {
    private byte[] subData;

    @Override
    public boolean acceptData(byte[] data) {
        this.subData = data;
        return false;
    }

    public byte[] getSubData() {
        return this.subData;
    }
}

